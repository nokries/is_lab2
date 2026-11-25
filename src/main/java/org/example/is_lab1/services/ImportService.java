package org.example.is_lab1.services;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.example.is_lab1.config.YamlConfig;
import org.example.is_lab1.exceptions.ConcurrencyConflictException;
import org.example.is_lab1.exceptions.ImportValidationException;
import org.example.is_lab1.exceptions.UniquenessViolationException;
import org.example.is_lab1.models.dto.BookCreatureDTO;
import org.example.is_lab1.models.dto.ImportOperationDto;
import org.example.is_lab1.models.dto.ImportRequestDTO;
import org.example.is_lab1.models.dto.MagicCityDTO;
import org.example.is_lab1.models.dto.RingDTO;
import org.example.is_lab1.models.entity.BookCreature;
import org.example.is_lab1.models.entity.ImportOperation;
import org.example.is_lab1.models.entity.ImportStatus;
import org.example.is_lab1.models.entity.MagicCity;
import org.example.is_lab1.models.entity.User;
import org.example.is_lab1.repository.ImportOperationRepository;
import org.example.is_lab1.repository.InteractRepository;
import org.example.is_lab1.utils.BookCreatureMapper;
import org.example.is_lab1.utils.MagicCityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Массовое добавление объектов из YAML-файла.
//
// Транзакционность ("всё или ничего"): весь файл обрабатывается ОДНОЙ транзакцией (importRecords).
// Любая ошибка проверки данных, нарушение уникальности или сбой БД откатывает её целиком -
// ни один объект из файла не создаётся. Запись в истории (ImportOperation) создаётся отдельной
// транзакцией до импорта и фиксируется сразу, поэтому неудачная операция остаётся в истории со статусом FAILED.
//
// Уровень изоляции - SERIALIZABLE (см. SerializableTransaction): проверка уникальности имён - это
// "прочитать, потом вставить", и два параллельных импорта с одинаковыми именами при более слабой
// изоляции оба прошли бы проверку.
@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    // Защита от слишком больших файлов: одна транзакция не должна держать БД слишком долго
    private static final int MAX_RECORDS = 5000;
    // Сколько ошибок показываем пользователю (остальные считаем)
    private static final int MAX_ERRORS_SHOWN = 20;
    // Размер пачки имён для проверки уникальности (SQL: name IN (...))
    private static final int NAMES_CHUNK = 500;

    private final InteractRepository creatureRepository;
    private final ImportOperationRepository operationRepository;
    private final ImportOperationService operations;
    private final BookCreatureMapper mapper;
    private final MagicCityMapper cityMapper;
    private final Validator validator;
    private final SerializableTransaction serializable;
    private final WebSocketNotificationService notificationService;

    public ImportService(InteractRepository creatureRepository,
                         ImportOperationRepository operationRepository,
                         ImportOperationService operations,
                         BookCreatureMapper mapper,
                         MagicCityMapper cityMapper,
                         Validator validator,
                         SerializableTransaction serializable,
                         WebSocketNotificationService notificationService) {
        this.creatureRepository = creatureRepository;
        this.operationRepository = operationRepository;
        this.operations = operations;
        this.mapper = mapper;
        this.cityMapper = cityMapper;
        this.validator = validator;
        this.serializable = serializable;
        this.notificationService = notificationService;
    }

    // Импорт файла от имени пользователя. При успехе возвращает запись истории (SUCCESS),
    // при ошибке - записывает FAILED и пробрасывает исключение (его превращает в ответ ExceptionHandler)
    public ImportOperationDto importFromYaml(MultipartFile file, User user) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new ImportValidationException("Не удалось прочитать загруженный файл");
        }

        Long operationId = operations.start(user, file.getOriginalFilename());
        try {
            List<BookCreatureDTO> records = parse(content);
            serializable.run(() -> importRecords(records, operationId));
        } catch (RuntimeException e) {
            log.warn("Импорт (операция {}) не выполнен: {}", operationId, e.getMessage());
            try {
                operations.markFailed(operationId, failureMessage(e));
            } catch (RuntimeException markError) {
                log.error("Не удалось записать в историю неудачу операции {}", operationId, markError);
            }
            throw e;
        }
        return operations.getOperation(operationId);
    }

    // ---------------------------------------------------------------- разбор файла

    private List<BookCreatureDTO> parse(byte[] content) {
        ImportRequestDTO request;
        try {
            request = YamlConfig.getYamlMapper().readValue(content, ImportRequestDTO.class);
        } catch (JsonProcessingException e) {
            throw new ImportValidationException(describeYamlError(e));
        } catch (IOException e) {
            throw new ImportValidationException("Не удалось прочитать файл");
        }

        if (request == null || request.creatures() == null || request.creatures().isEmpty()) {
            throw new ImportValidationException("Файл не содержит ни одного объекта: ожидается непустой список 'creatures'");
        }
        if (request.creatures().size() > MAX_RECORDS) {
            throw new ImportValidationException("В файле слишком много записей: "
                    + request.creatures().size() + " (максимум " + MAX_RECORDS + ")");
        }
        return request.creatures();
    }

    // Человекочитаемое описание ошибки разбора YAML: номер записи, поле, строка файла
    private static String describeYamlError(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        String line = "";
        if (location != null && location.getLineNr() > 0) {
            line = " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
        }

        if (!(e instanceof JsonMappingException jme)) {
            return "Некорректный формат YAML" + line;
        }

        // Путь до проблемного поля: creatures[2].coordinates.x -> "Запись №3, поле 'coordinates.x'"
        Integer record = null;
        StringBuilder field = new StringBuilder();
        for (JsonMappingException.Reference ref : jme.getPath()) {
            if (ref.getFieldName() != null) {
                if ("creatures".equals(ref.getFieldName()) && record == null) {
                    continue;
                }
                if (field.length() > 0) {
                    field.append('.');
                }
                field.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0 && record == null) {
                record = ref.getIndex() + 1;
            }
        }

        String detail;
        if (jme instanceof UnrecognizedPropertyException upe) {
            detail = "неизвестное поле '" + upe.getPropertyName() + "'";
        } else if (jme.getCause() instanceof InputCoercionException) {
            detail = "число вне допустимого диапазона";
        } else if (jme instanceof InvalidFormatException ife) {
            detail = "недопустимое значение '" + ife.getValue() + "'";
        } else {
            detail = "недопустимое значение или неверный тип данных";
        }

        String place = (record != null) ? "Запись №" + record : "Файл";
        if (field.length() > 0) {
            place = place + ", поле '" + field + "'";
        }
        return place + line + ": " + detail;
    }

    // ---------------------------------------------------------------- импорт (одна транзакция)

    // Выполняется внутри транзакции SERIALIZABLE (см. importFromYaml). Любое исключение откатывает всё.
    private void importRecords(List<BookCreatureDTO> records, Long operationId) {
        // 1. Проверяем каждую запись по ограничениям предметной области и собираем сущности.
        //    Ошибки копим, а не бросаем сразу, чтобы показать пользователю все проблемы файла за один раз.
        List<String> errors = new ArrayList<>();
        List<BookCreature> entities = new ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            BookCreature entity = convertRecord(records.get(i), i + 1, errors);
            if (entity != null) {
                entities.add(entity);
            }
        }
        if (!errors.isEmpty()) {
            throw new ImportValidationException(limit(errors));
        }

        // 2. Ограничение уникальности (имя существа): и внутри файла, и среди уже существующих объектов
        List<String> duplicates = findDuplicateNames(records);
        if (!duplicates.isEmpty()) {
            throw new UniquenessViolationException(String.join("; ", limit(duplicates)));
        }

        // 3. Сохраняем. Вложенные объекты (координаты, кольцо, город) создаются каскадом вместе с основным
        creatureRepository.saveAll(entities);
        creatureRepository.flush(); // ошибки БД (CHECK и т.п.) проявляются здесь, а не при коммите

        for (BookCreature creature : entities) {
            MagicCity city = creature.getCreatureLocation();
            if (city != null) {
                notificationService.notifyCreated(WebSocketNotificationService.CITY, city.getId(), cityMapper.toDto(city));
            }
            notificationService.notifyCreated(WebSocketNotificationService.CREATURE, creature.getId(), mapper.toDto(creature));
        }

        // 4. Отмечаем операцию успешной в той же транзакции: данные и статус фиксируются атомарно
        ImportOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Import operation not found with id " + operationId));
        operation.setStatus(ImportStatus.SUCCESS);
        operation.setEndTime(LocalDateTime.now());
        operation.setAddedCount(entities.size());
        operation.setErrorMessage(null);
        operationRepository.save(operation);
    }

    // Проверяет одну запись и превращает её в сущность. При ошибках добавляет их в errors и возвращает null.
    private BookCreature convertRecord(BookCreatureDTO dto, int number, List<String> errors) {
        if (dto == null) {
            errors.add("Запись №" + number + ": запись пуста");
            return null;
        }

        List<String> problems = new ArrayList<>();

        // Ограничения самого существа и его координат (те же аннотации, что и при создании через форму)
        for (ConstraintViolation<BookCreatureDTO> v : validator.validate(dto)) {
            problems.add(v.getPropertyPath() + ": " + v.getMessage());
        }

        // Вложенные объекты должны быть описаны полностью, в той же записи (ссылки по id не поддерживаются)
        MagicCityDTO city = dto.creatureLocation();
        RingDTO ring = dto.ring();
        boolean hasIdReference = false;
        if (city != null && city.id() != 0) {
            problems.add("creatureLocation.id: вложенный объект нужно описывать полностью, ссылка по id не поддерживается");
            hasIdReference = true;
        }
        if (ring != null && ring.id() != 0) {
            problems.add("ring.id: вложенный объект нужно описывать полностью, ссылка по id не поддерживается");
            hasIdReference = true;
        }

        BookCreature entity = null;
        if (!hasIdReference) {
            // Маппер проверяет вложенные город и кольцо по ограничениям их сущностей
            // и разбирает тип существа / губернатора (неизвестное значение - IllegalArgumentException)
            try {
                entity = mapper.toEntity(dto);
            } catch (ConstraintViolationException e) {
                for (ConstraintViolation<?> v : e.getConstraintViolations()) {
                    problems.add(nestedPrefix(v) + v.getPropertyPath() + ": " + v.getMessage());
                }
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }

        if (!problems.isEmpty()) {
            String label = "Запись №" + number;
            if (dto.name() != null && !dto.name().isBlank()) {
                label = label + " ('" + dto.name() + "')";
            }
            errors.add(label + ": " + String.join(", ", problems));
            return null;
        }
        return entity;
    }

    // К какому вложенному объекту относится нарушение (маппер проверяет город и кольцо отдельно от существа)
    private static String nestedPrefix(ConstraintViolation<?> v) {
        Class<?> root = v.getRootBeanClass();
        if (root == MagicCity.class) {
            return "creatureLocation.";
        }
        if (root == org.example.is_lab1.models.entity.Ring.class) {
            return "ring.";
        }
        return "";
    }

    // Находит нарушения уникальности имени: повторы внутри файла и совпадения с объектами в БД
    private List<String> findDuplicateNames(List<BookCreatureDTO> records) {
        List<String> problems = new ArrayList<>();

        // имя -> номера записей, в которых оно встречается
        Map<String, List<Integer>> positions = new LinkedHashMap<>();
        for (int i = 0; i < records.size(); i++) {
            positions.computeIfAbsent(records.get(i).name(), k -> new ArrayList<>()).add(i + 1);
        }

        for (Map.Entry<String, List<Integer>> entry : positions.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add("Имя '" + entry.getKey() + "' повторяется в файле (записи №"
                        + joinNumbers(entry.getValue()) + ")");
            }
        }

        List<String> names = new ArrayList<>(positions.keySet());
        for (int from = 0; from < names.size(); from += NAMES_CHUNK) {
            List<String> chunk = names.subList(from, Math.min(from + NAMES_CHUNK, names.size()));
            Set<String> existing = Set.copyOf(creatureRepository.findExistingNames(chunk));
            for (String name : chunk) {
                if (existing.contains(name)) {
                    problems.add("Имя '" + name + "' уже существует в системе (запись №"
                            + joinNumbers(positions.get(name)) + ")");
                }
            }
        }
        return problems;
    }

    private static String joinNumbers(List<Integer> numbers) {
        List<String> parts = new ArrayList<>();
        for (Integer n : numbers) {
            parts.add(String.valueOf(n));
        }
        return String.join(", №", parts);
    }

    private static List<String> limit(List<String> errors) {
        if (errors.size() <= MAX_ERRORS_SHOWN) {
            return errors;
        }
        List<String> shown = new ArrayList<>(errors.subList(0, MAX_ERRORS_SHOWN));
        shown.add("... и ещё ошибок: " + (errors.size() - MAX_ERRORS_SHOWN));
        return shown;
    }

    // Текст ошибки для истории
    private static String failureMessage(RuntimeException e) {
        if (e instanceof ImportValidationException || e instanceof UniquenessViolationException
                || e instanceof ConcurrencyConflictException) {
            return e.getMessage();
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null) {
            message = "Ошибка при выполнении импорта";
        }
        return message;
    }
}

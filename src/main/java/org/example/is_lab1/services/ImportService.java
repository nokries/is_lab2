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

@Service
public class ImportService {
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private static final int MAX_RECORDS = 5000;
    private static final int MAX_ERRORS_SHOWN = 20;
    private static final int NAMES_CHUNK = 500;

    private final InteractRepository creatureRepository;
    private final ImportOperationRepository operationRepository;
    private final ImportOperationService operations;
    private final BookCreatureMapper mapper;
    private final MagicCityMapper cityMapper;
    private final Validator validator;
    private final WebSocketNotificationService notificationService;

    public ImportService(InteractRepository creatureRepository,
                         ImportOperationRepository operationRepository,
                         ImportOperationService operations,
                         BookCreatureMapper mapper,
                         MagicCityMapper cityMapper,
                         Validator validator,
                         WebSocketNotificationService notificationService) {
        this.creatureRepository = creatureRepository;
        this.operationRepository = operationRepository;
        this.operations = operations;
        this.mapper = mapper;
        this.cityMapper = cityMapper;
        this.validator = validator;
        this.notificationService = notificationService;
    }


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

    private static String describeYamlError(JsonProcessingException e) {
        JsonLocation location = e.getLocation();
        String line = "";
        if (location != null && location.getLineNr() > 0) {
            line = " (строка " + location.getLineNr() + ", столбец " + location.getColumnNr() + ")";
        }

        if (!(e instanceof JsonMappingException jme)) {
            return "Некорректный формат YAML" + line;
        }

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


    private void importRecords(List<BookCreatureDTO> records, Long operationId) {
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

        List<String> duplicates = findDuplicateNames(records);
        if (!duplicates.isEmpty()) {
            throw new UniquenessViolationException(String.join("; ", limit(duplicates)));
        }


        creatureRepository.saveAll(entities);
        creatureRepository.flush();

        for (BookCreature creature : entities) {
            MagicCity city = creature.getCreatureLocation();
            if (city != null) {
                notificationService.notifyCreated(WebSocketNotificationService.CITY, city.getId(), cityMapper.toDto(city));
            }
            notificationService.notifyCreated(WebSocketNotificationService.CREATURE, creature.getId(), mapper.toDto(creature));
        }

        ImportOperation operation = operationRepository.findById(operationId)
                .orElseThrow(() -> new EntityNotFoundException("Import operation not found with id " + operationId));
        operation.setStatus(ImportStatus.SUCCESS);
        operation.setEndTime(LocalDateTime.now());
        operation.setAddedCount(entities.size());
        operation.setErrorMessage(null);
        operationRepository.save(operation);
    }

    private BookCreature convertRecord(BookCreatureDTO dto, int number, List<String> errors) {
        if (dto == null) {
            errors.add("Запись №" + number + ": запись пуста");
            return null;
        }

        List<String> problems = new ArrayList<>();

        for (ConstraintViolation<BookCreatureDTO> v : validator.validate(dto)) {
            problems.add(v.getPropertyPath() + ": " + v.getMessage());
        }

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


    private List<String> findDuplicateNames(List<BookCreatureDTO> records) {
        List<String> problems = new ArrayList<>();


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

    private static String failureMessage(RuntimeException e) {
        if (e instanceof ImportValidationException || e instanceof UniquenessViolationException) {
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

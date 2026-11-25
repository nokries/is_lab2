package org.example.is_lab1.services;

import jakarta.persistence.EntityNotFoundException;
import org.example.is_lab1.exceptions.UniquenessViolationException;
import org.example.is_lab1.models.dto.BookCreatureDTO;
import org.example.is_lab1.models.entity.BookCreature;
import org.example.is_lab1.models.entity.BookCreatureType;
import org.example.is_lab1.models.entity.MagicCity;
import org.example.is_lab1.repository.CityRepository;
import org.example.is_lab1.repository.InteractRepository;
import org.example.is_lab1.repository.RingRepository;
import org.example.is_lab1.utils.BookCreatureMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class InteractService {
    private final InteractRepository repository;
    private final BookCreatureMapper mapper;
    private final RingRepository ringRepository;
    private final CityRepository cityRepository;
    private final WebSocketNotificationService notificationService;
    private final SerializableTransaction serializable;


    public InteractService(InteractRepository repository, BookCreatureMapper mapper, RingRepository ringRepository, CityRepository cityRepository, WebSocketNotificationService notificationService, SerializableTransaction serializable) {
        this.repository = repository;
        this.mapper = mapper;
        this.ringRepository = ringRepository;
        this.cityRepository = cityRepository;
        this.notificationService = notificationService;
        this.serializable = serializable;
    }

    // ЛР2: имя существа должно быть уникальным. Ограничение проверяется здесь, в бизнес-логике (в БД его нет).
    // ownId - id редактируемого объекта (его собственное имя конфликтом не считается), при создании null
    private void checkNameIsFree(String name, Long ownId) {
        boolean taken;
        if (ownId == null) {
            taken = repository.existsByName(name);
        } else {
            taken = repository.existsByNameAndIdNot(name, ownId);
        }
        if (taken) {
            throw new UniquenessViolationException("Существо с именем '" + name + "' уже существует");
        }
    }

    // Все три операции записи ниже выполняются в транзакции SERIALIZABLE с автоповтором при конфликте
    // (см. SerializableTransaction и docs/LAB2_ISOLATION.md): проверка уникальности имени - это
    // "прочитать, потом вставить", и при более слабой изоляции параллельные запросы её обходят.
    public BookCreatureDTO create(BookCreatureDTO creature){
        return serializable.execute(() -> {
            checkNameIsFree(creature.name(), null);
            BookCreature el = mapper.toEntity(creature);
            repository.save(el);
            BookCreatureDTO created = mapper.toDto(el);
            notificationService.notifyCreated(WebSocketNotificationService.CREATURE, el.getId(), created);
            return created;
        });
    }

    public BookCreatureDTO modify(long id, BookCreatureDTO creature){
        return serializable.execute(() -> {
            Optional<BookCreature> found = repository.findById(id);
            if (found.isEmpty()) {
                throw new EntityNotFoundException("Creature not found with id " + id);
            }
            BookCreature existing = found.get();
            // Проверяем имя только если оно действительно меняется
            if (!Objects.equals(existing.getName(), creature.name())) {
                checkNameIsFree(creature.name(), id);
            }
            mapper.updateEntityFromDto(creature, existing);
            repository.save(existing);
            BookCreatureDTO updated = mapper.toDto(existing);
            notificationService.notifyUpdated(WebSocketNotificationService.CREATURE, id, updated);
            return updated;
        });
    }

    public void delete(long id){
        serializable.run(() -> {
            Optional<BookCreature> found = repository.findById(id);
            if (found.isEmpty()) {
                throw new EntityNotFoundException("Creature not found with id " + id);
            }
            BookCreature creature = found.get();

            MagicCity city = creature.getCreatureLocation();

            repository.delete(creature);

            // Проверяем, остались ли ещё жители этого города
            if (city != null) {
                boolean hasResidents = repository.existsByCreatureLocation(city);
                if (!hasResidents) {
                    cityRepository.delete(city);
                    notificationService.notifyDeleted(WebSocketNotificationService.CITY, city.getId());
                }
            }
            notificationService.notifyDeleted(WebSocketNotificationService.CREATURE, id);
        });
    }

    // filterBy - по какой строковой колонке фильтруем (name / creatureType / city / ring), фильтр - только полное совпадение
    public Page<BookCreatureDTO> get(Pageable pageable, String filterBy, String filter){
        Page<BookCreature> page;
        if (filter != null && !filter.isEmpty()) {
            String column;
            if (filterBy == null) {
                column = "name";
            } else {
                column = filterBy;
            }
            switch (column) {
                case "creatureType" -> page = repository.findByCreatureType(BookCreatureType.getType(filter), pageable);
                case "city" -> page = repository.findByCreatureLocationName(filter, pageable);
                case "ring" -> page = repository.findByRingName(filter, pageable);
                default -> page = repository.findByName(filter, pageable);
            }
        } else {
            page = repository.findAll(pageable);
        }

        List<BookCreatureDTO> dtoList = new ArrayList<>();
        for (BookCreature c : page.getContent()) {
            dtoList.add(mapper.toDto(c));
        }
        return new PageImpl<>(dtoList, pageable, page.getTotalElements());
    }

    public BookCreatureDTO getById(long id){
        Optional<BookCreature> found = repository.findById(id);
        if (found.isEmpty()) {
            throw new EntityNotFoundException("Creature not found with id " + id);
        }
        return mapper.toDto(found.get());
    }
}
package org.example.is_lab1.repository;

import org.example.is_lab1.models.entity.BookCreature;
import org.example.is_lab1.models.entity.BookCreatureType;
import org.example.is_lab1.models.entity.MagicCity;
import org.example.is_lab1.models.entity.Ring;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InteractRepository extends JpaRepository<BookCreature, Long> {
    boolean existsByCreatureLocation(MagicCity city);

    // Фильтрация таблицы: только полное совпадение (без Containing / IgnoreCase)
    Page<BookCreature> findByName(String name, Pageable pageable);

    Page<BookCreature> findByCreatureType(BookCreatureType type, Pageable pageable);

    Page<BookCreature> findByCreatureLocationName(String cityName, Pageable pageable);

    Page<BookCreature> findByRingName(String ringName, Pageable pageable);

    // Для каскадного удаления
    List<BookCreature> findByCreatureLocation(MagicCity city);

    Optional<BookCreature> findByRing(Ring ring);

    // Специальные операции
    Optional<BookCreature> findFirstByOrderByCoordinatesXAscCoordinatesYAsc();

    List<BookCreature> findByNameContaining(String substring);

    List<BookCreature> findByRingPowerLessThan(Integer power);

    List<BookCreature> findByCreatureType(BookCreatureType type);

    // ЛР2: проверка уникальности имени существа (ограничение реализовано в бизнес-логике, а не в БД)
    boolean existsByName(String name);

    // То же, но с исключением самого редактируемого объекта (при изменении имени)
    boolean existsByNameAndIdNot(String name, Long id);

    // Какие из перечисленных имён уже заняты (для импорта: одним запросом на пачку имён)
    @Query("SELECT b.name FROM BookCreature b WHERE b.name IN :names")
    List<String> findExistingNames(@Param("names") Collection<String> names);

}

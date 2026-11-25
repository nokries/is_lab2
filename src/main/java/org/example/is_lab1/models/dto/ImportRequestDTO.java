package org.example.is_lab1.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Содержимое YAML-файла импорта. Формат файла:
//
// creatures:
//   - name: "Frodo"
//     coordinates: { x: 10, y: 20 }
//     age: 50
//     creatureType: HOBBIT          # необязательное поле
//     attackLevel: 7
//     creatureLocation:             # необязательное поле; вложенный объект описывается целиком, в той же записи
//       name: "Shire"
//       area: 5000.5
//       population: 1000
//       governor: HOBBIT
//       capital: true
//       populationDensity: 200
//     ring:                         # обязательное поле; вложенный объект описывается целиком, в той же записи
//       name: "One Ring"
//       power: 1000
//       weight: 5
public record ImportRequestDTO(
        @JsonProperty("creatures") List<BookCreatureDTO> creatures
) {}

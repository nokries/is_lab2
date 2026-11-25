package org.example.is_lab1.models.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ImportRequestDTO(
        @JsonProperty("creatures") List<BookCreatureDTO> creatures
) {}

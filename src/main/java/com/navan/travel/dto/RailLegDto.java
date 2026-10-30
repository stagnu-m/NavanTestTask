package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RailLegDto(
        @NotBlank(message = "Origin is required")
        String origin,

        @NotBlank(message = "Destination is required")
        String destination,

        @NotBlank(message = "Departure is required")
        String departure,

        @NotBlank(message = "Arrival is required")
        String arrival,

        @JsonProperty("train_number")
        String trainNumber,

        @JsonProperty("class")
        String travelClass
) {}

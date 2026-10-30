package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlightCouponDto(
        String carrier,

        @NotBlank(message = "Flight number is required")
        @JsonProperty("flight_number")
        String flightNumber,

        @NotBlank(message = "From location is required")
        String from,

        @NotBlank(message = "To location is required")
        String to,

        @NotBlank(message = "Departure time is required")
        String departure,

        @NotBlank(message = "Arrival time is required")
        String arrival
) {}

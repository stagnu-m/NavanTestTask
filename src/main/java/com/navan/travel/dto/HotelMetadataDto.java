package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HotelMetadataDto(
        @NotBlank(message = "Hotel name is required")
        String name,

        @NotNull(message = "Hotel address is required")
        @Valid
        AddressDto address,

        @NotBlank(message = "Check-in date is required")
        @JsonProperty("check_in")
        String checkIn,

        @NotBlank(message = "Check-out date is required")
        @JsonProperty("check_out")
        String checkOut,

        @NotNull(message = "Number of nights is required")
        Integer nights,

        @NotNull(message = "Room rate per night is required")
        @JsonProperty("room_rate_per_night")
        BigDecimal roomRatePerNight,

        List<@Valid HotelExtraDto> extras
) {}

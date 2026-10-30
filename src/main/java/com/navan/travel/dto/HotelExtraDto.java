package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record HotelExtraDto(
        @NotBlank(message = "Extra name is required")
        String name,

        @NotNull(message = "Extra amount is required")
        BigDecimal amount
) {}

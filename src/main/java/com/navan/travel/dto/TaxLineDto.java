package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaxLineDto(
        @NotBlank(message = "Tax name is required")
        String name,

        BigDecimal rate,

        @NotNull(message = "Tax amount is required")
        BigDecimal amount
) {}

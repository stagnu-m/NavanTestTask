package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeeLineDto(
        @NotBlank(message = "Fee name is required")
        String name,

        @NotNull(message = "Fee amount is required")
        BigDecimal amount
) {}

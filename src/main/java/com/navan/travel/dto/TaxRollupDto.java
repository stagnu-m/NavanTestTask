package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaxRollupDto(
        String name,
        BigDecimal rate,
        BigDecimal amount
) {}

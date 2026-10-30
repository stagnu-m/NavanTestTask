package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeeRollupDto(
        String name,
        BigDecimal amount
) {}

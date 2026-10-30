package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        String id,
        String type,
        @JsonProperty("external_id")
        String externalId,
        String currency,
        @JsonProperty("occurred_at")
        String occurredAt,
        BigDecimal total,
        @JsonProperty("tax_lines")
        List<TaxLineDto> taxLines,
        @JsonProperty("fee_lines")
        List<FeeLineDto> feeLines,
        Object metadata
) {}

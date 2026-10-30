package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InvoiceResponse(
        String id,
        String currency,
        Map<String, java.math.BigDecimal> totals,
        @JsonProperty("tax_rollup")
        List<TaxRollupDto> taxRollup,
        @JsonProperty("fee_rollup")
        List<FeeRollupDto> feeRollup,
        List<TransactionResponse> transactions,
        @JsonProperty("created_at")
        String createdAt,
        @JsonProperty("updated_at")
        String updatedAt
) {}

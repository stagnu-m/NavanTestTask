package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.navan.travel.model.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public record TransactionRequest(
        @NotNull(message = "Transaction type is required")
        TransactionType type,

        @NotBlank(message = "external_id is required")
        @JsonProperty("external_id")
        String externalId,

        @NotBlank(message = "currency is required")
        String currency,

        @NotBlank(message = "occurred_at is required")
        @JsonProperty("occurred_at")
        String occurredAt,

        @NotNull(message = "total is required")
        BigDecimal total,

        @NotNull(message = "tax_lines is required")
        @JsonProperty("tax_lines")
        List<@NotNull(message = "Tax line cannot be null") @Valid TaxLineDto> taxLines,

        @NotNull(message = "fee_lines is required")
        @JsonProperty("fee_lines")
        List<@NotNull(message = "Fee line cannot be null") @Valid FeeLineDto> feeLines,

        @NotNull(message = "metadata is required")
        JsonNode metadata
) {}

package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

public record CreateInvoiceRequest(
        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("transaction_ids")
        List<@NotNull(message = "transaction_id cannot be null") @NotBlank(message = "transaction_id cannot be blank") String> transactionIds
) {
    public List<String> resolvedTransactionIds() {
        if (transactionIds != null) {
            return transactionIds;
        }
        if (transactionId != null && !transactionId.isBlank()) {
            return Collections.singletonList(transactionId);
        }
        return Collections.emptyList();
    }
}

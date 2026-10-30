package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record CreateInvoiceRequest(
        @JsonProperty("transaction_id")
        String transactionId,

        @JsonProperty("transaction_ids")
        List<String> transactionIds
) {
    public List<String> resolvedTransactionIds() {
        if (transactionIds != null && !transactionIds.isEmpty()) {
            return transactionIds;
        }
        if (transactionId != null && !transactionId.isBlank()) {
            return Collections.singletonList(transactionId);
        }
        return Collections.emptyList();
    }
}

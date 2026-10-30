package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NavanFeeMetadataDto(
        @NotBlank(message = "Fee kind is required")
        @JsonProperty("fee_kind")
        String feeKind,

        String description,

        @JsonProperty("related_external_id")
        String relatedExternalId
) {}

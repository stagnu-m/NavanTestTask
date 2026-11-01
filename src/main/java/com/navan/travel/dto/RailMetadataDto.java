package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RailMetadataDto(
        @NotNull(message = "Rail legs are required")
        @NotEmpty(message = "Rail legs cannot be empty")
        List<@Valid RailLegDto> legs
) {}

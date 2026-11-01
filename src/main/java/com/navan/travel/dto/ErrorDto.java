package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDto(
        String error,
        String id
) {
    public ErrorDto(String error) {
        this(error, null);
    }
}

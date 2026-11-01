package com.navan.travel.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FlightMetadataDto(
        @NotBlank(message = "Flight origin is required")
        String origin,

        @NotBlank(message = "Flight destination is required")
        String destination,

        @NotNull(message = "Flight fare is required")
        BigDecimal fare,

        @NotNull(message = "Flight coupons list is required")
        List<@Valid FlightCouponDto> coupons
) {}

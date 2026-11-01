package com.navan.travel.service;

import com.navan.travel.dto.OffsetLimitRequest;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.ConflictException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class HelperService {

    public Currency validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("currency cannot be blank");
        }
        try {
            return Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ISO-4217 currency code: " + currency);
        }
    }

    public BigDecimal validateMoney(BigDecimal amount, String fieldName, Currency currency) {
        if (amount == null) {
            throw new BadRequestException(fieldName + " cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(fieldName + " cannot be negative");
        }
        int maxScale = Math.max(0, currency.getDefaultFractionDigits());
        int effectiveScale = amount.compareTo(BigDecimal.ZERO) == 0 ? 0 : amount.stripTrailingZeros().scale();
        if (effectiveScale > maxScale) {
            throw new BadRequestException(fieldName + " scale exceeds maximum " + maxScale + " decimal places for currency " + currency.getCurrencyCode());
        }
        return amount.setScale(maxScale, RoundingMode.HALF_UP);
    }

    public BigDecimal scaleMoney(BigDecimal amount) {
        return (amount != null ? amount : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public OffsetLimitRequest validatePagination(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new BadRequestException("offset cannot be negative");
        }
        return new OffsetLimitRequest(limit, offset);
    }

    public Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid ISO-8601 date-time format: " + text);
        }
    }

    public List<String> validateTransactionIds(List<String> txIds) {
        if (txIds == null || txIds.isEmpty()) {
            throw new BadRequestException("At least one transaction_id must be provided");
        }
        Set<String> uniqueIds = new HashSet<>();
        for (String id : txIds) {
            if (id == null || id.isBlank()) {
                throw new BadRequestException("transaction_id cannot be null or blank");
            }
            if (!uniqueIds.add(id)) {
                throw new ConflictException("Duplicate transaction_id listed on invoice: " + id);
            }
        }
        return txIds;
    }
}

package com.navan.travel.service;

import com.navan.travel.dto.OffsetLimitRequest;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HelperServiceUnitTest {

    private HelperService helperService;

    @BeforeEach
    void setUp() {
        helperService = new HelperService();
    }

    @Test
    void testValidateCurrency() {
        assertThatThrownBy(() -> helperService.validateCurrency(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("currency cannot be blank");

        assertThatThrownBy(() -> helperService.validateCurrency("   "))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("currency cannot be blank");

        assertThatThrownBy(() -> helperService.validateCurrency("INVALID_CURRENCY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid ISO-4217 currency code");

        Currency eur = helperService.validateCurrency("eur");
        assertThat(eur.getCurrencyCode()).isEqualTo("EUR");

        Currency usd = helperService.validateCurrency("USD");
        assertThat(usd.getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void testValidateMoney() {
        Currency usd = Currency.getInstance("USD");

        assertThatThrownBy(() -> helperService.validateMoney(null, "amount", usd))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount cannot be null");

        assertThatThrownBy(() -> helperService.validateMoney(BigDecimal.valueOf(-5), "amount", usd))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("amount cannot be negative");

        assertThatThrownBy(() -> helperService.validateMoney(new BigDecimal("10.123"), "amount", usd))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("scale exceeds maximum 2 decimal places");

        BigDecimal valid = helperService.validateMoney(new BigDecimal("25.5"), "amount", usd);
        assertThat(valid).isEqualTo(new BigDecimal("25.50"));

        BigDecimal zero = helperService.validateMoney(BigDecimal.ZERO, "amount", usd);
        assertThat(zero).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void testScaleMoney() {
        assertThat(helperService.scaleMoney(null)).isEqualTo(new BigDecimal("0.00"));
        assertThat(helperService.scaleMoney(new BigDecimal("42.1"))).isEqualTo(new BigDecimal("42.10"));
    }

    @Test
    void testValidatePagination() {
        assertThatThrownBy(() -> helperService.validatePagination(0, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit must be between 1 and 100");

        assertThatThrownBy(() -> helperService.validatePagination(101, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("limit must be between 1 and 100");

        assertThatThrownBy(() -> helperService.validatePagination(50, -1))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("offset cannot be negative");

        OffsetLimitRequest req = helperService.validatePagination(25, 50);
        assertThat(req.getPageSize()).isEqualTo(25);
        assertThat(req.getOffset()).isEqualTo(50);
    }

    @Test
    void testParseInstant() {
        assertThatThrownBy(() -> helperService.parseInstant("not-a-timestamp"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid ISO-8601 date-time format");

        Instant instant = helperService.parseInstant("2026-03-12T10:00:00Z");
        assertThat(instant).isEqualTo(Instant.parse("2026-03-12T10:00:00Z"));
    }

    @Test
    void testValidateTransactionIds() {
        assertThatThrownBy(() -> helperService.validateTransactionIds(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At least one transaction_id must be provided");

        assertThatThrownBy(() -> helperService.validateTransactionIds(List.of()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("At least one transaction_id must be provided");

        List<String> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> helperService.validateTransactionIds(withNull))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transaction_id cannot be null or blank");

        assertThatThrownBy(() -> helperService.validateTransactionIds(List.of("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("transaction_id cannot be null or blank");

        assertThatThrownBy(() -> helperService.validateTransactionIds(List.of("txn_1", "txn_1")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Duplicate transaction_id listed on invoice: txn_1");

        List<String> valid = helperService.validateTransactionIds(List.of("txn_1", "txn_2"));
        assertThat(valid).containsExactly("txn_1", "txn_2");
    }
}

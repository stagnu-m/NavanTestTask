package com.navan.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.repository.InvoiceRepository;
import com.navan.travel.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceUnitTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionService transactionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testDetermineCategoryBranches() {
        InvoiceService service = new InvoiceService(invoiceRepository, transactionRepository, transactionService, objectMapper);

        // 1. navan_fee with Map metadata but no fee_kind
        String cat1 = ReflectionTestUtils.invokeMethod(service, "determineCategory", "navan_fee", Map.of("other", "value"));
        assertThat(cat1).isEqualTo("navan_fee");

        // 2. navan_fee with non-map metadata
        String cat2 = ReflectionTestUtils.invokeMethod(service, "determineCategory", "navan_fee", "string_metadata");
        assertThat(cat2).isEqualTo("navan_fee");

        // 3. non-navan_fee type
        String cat3 = ReflectionTestUtils.invokeMethod(service, "determineCategory", "flight", Map.of());
        assertThat(cat3).isEqualTo("flight");

        // 4. validateCurrencies with empty list
        ReflectionTestUtils.invokeMethod(service, "validateCurrencies", Collections.emptyList());
    }
}

package com.navan.travel.model.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityLifecycleTest {

    @Test
    void testTransactionEntityLifecycle() {
        TransactionEntity entity = new TransactionEntity();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();

        // Second onPrePersist when already set (covers false branch)
        Instant prevCreated = entity.getCreatedAt();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isEqualTo(prevCreated);

        entity.onPreUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void testInvoiceEntityLifecycle() {
        InvoiceEntity entity = new InvoiceEntity();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();

        // Second onPrePersist when already set (covers false branch)
        Instant prevCreated = entity.getCreatedAt();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isEqualTo(prevCreated);

        entity.onPreUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }
}

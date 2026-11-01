package com.navan.travel.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TransactionTypeConverterTest {

    private final TransactionTypeConverter converter = new TransactionTypeConverter();

    @Test
    void testConvertToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(TransactionType.FLIGHT)).isEqualTo("flight");
        assertThat(converter.convertToDatabaseColumn(TransactionType.HOTEL)).isEqualTo("hotel");
        assertThat(converter.convertToDatabaseColumn(TransactionType.RAIL)).isEqualTo("rail");
        assertThat(converter.convertToDatabaseColumn(TransactionType.NAVAN_FEE)).isEqualTo("navan_fee");
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void testConvertToEntityAttribute() {
        assertThat(converter.convertToEntityAttribute("flight")).isEqualTo(TransactionType.FLIGHT);
        assertThat(converter.convertToEntityAttribute("hotel")).isEqualTo(TransactionType.HOTEL);
        assertThat(converter.convertToEntityAttribute("rail")).isEqualTo(TransactionType.RAIL);
        assertThat(converter.convertToEntityAttribute("navan_fee")).isEqualTo(TransactionType.NAVAN_FEE);
        assertThat(converter.convertToEntityAttribute("spaceship")).isEqualTo(TransactionType.UNSUPPORTED);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}

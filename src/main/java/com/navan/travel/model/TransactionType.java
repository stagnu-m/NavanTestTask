package com.navan.travel.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {
    FLIGHT("flight"),
    HOTEL("hotel"),
    RAIL("rail"),
    NAVAN_FEE("navan_fee"),
    UNSUPPORTED("unsupported");

    private final String value;

    TransactionType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static TransactionType fromString(String text) {
        if (text == null) {
            return UNSUPPORTED;
        }
        for (TransactionType t : TransactionType.values()) {
            if (t.value.equalsIgnoreCase(text.trim())) {
                return t;
            }
        }
        return UNSUPPORTED;
    }
}

package com.navan.travel.exception;

public class ConflictException extends RuntimeException {
    private final String existingId;

    public ConflictException(String message) {
        super(message);
        this.existingId = null;
    }

    public ConflictException(String message, String existingId) {
        super(message);
        this.existingId = existingId;
    }

    public String getExistingId() {
        return existingId;
    }
}

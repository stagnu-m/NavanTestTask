package com.navan.travel.exception;

public class TransactionNotFoundException extends NotFoundException {

    public TransactionNotFoundException(String id) {
        super("Transaction not found: " + id);
    }
}

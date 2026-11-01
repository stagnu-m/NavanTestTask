package com.navan.travel.exception;

public class InvoiceNotFoundException extends NotFoundException {

    public InvoiceNotFoundException(String id) {
        super("Invoice not found: " + id);
    }
}

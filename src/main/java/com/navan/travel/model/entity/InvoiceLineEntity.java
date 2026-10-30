package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines", indexes = {
        @Index(name = "idx_inv_line_invoice_id", columnList = "invoice_id"),
        @Index(name = "idx_inv_line_transaction_id", columnList = "transaction_id")
})
public class InvoiceLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private InvoiceEntity invoice;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "occurred_at", nullable = false, length = 64)
    private String occurredAt;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Lob
    @Column(name = "tax_lines_json", nullable = false)
    private String taxLinesJson;

    @Lob
    @Column(name = "fee_lines_json", nullable = false)
    private String feeLinesJson;

    @Lob
    @Column(name = "metadata_json", nullable = false)
    private String metadataJson;

    public InvoiceLineEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public InvoiceEntity getInvoice() {
        return invoice;
    }

    public void setInvoice(InvoiceEntity invoice) {
        this.invoice = invoice;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getTaxLinesJson() {
        return taxLinesJson;
    }

    public void setTaxLinesJson(String taxLinesJson) {
        this.taxLinesJson = taxLinesJson;
    }

    public String getFeeLinesJson() {
        return feeLinesJson;
    }

    public void setFeeLinesJson(String feeLinesJson) {
        this.feeLinesJson = feeLinesJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }
}

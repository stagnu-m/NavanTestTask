package com.navan.travel.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "navan_fee_metadata", indexes = {
        @Index(name = "idx_fee_kind", columnList = "fee_kind"),
        @Index(name = "idx_fee_related_ext_id", columnList = "related_external_id")
})
public class NavanFeeMetadataEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @Column(name = "fee_kind", nullable = false, length = 64)
    private String feeKind;

    @Column(name = "description")
    private String description;

    @Column(name = "related_external_id", length = 128)
    private String relatedExternalId;

    public NavanFeeMetadataEntity() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TransactionEntity getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionEntity transaction) {
        this.transaction = transaction;
    }

    public String getFeeKind() {
        return feeKind;
    }

    public void setFeeKind(String feeKind) {
        this.feeKind = feeKind;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRelatedExternalId() {
        return relatedExternalId;
    }

    public void setRelatedExternalId(String relatedExternalId) {
        this.relatedExternalId = relatedExternalId;
    }
}

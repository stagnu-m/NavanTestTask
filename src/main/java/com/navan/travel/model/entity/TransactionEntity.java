package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions", indexes = {
        @Index(name = "idx_tx_external_id", columnList = "external_id", unique = true),
        @Index(name = "idx_tx_type", columnList = "type"),
        @Index(name = "idx_tx_occurred_at", columnList = "occurred_at")
})
public class TransactionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "external_id", nullable = false, unique = true, length = 128)
    private String externalId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionTaxLineEntity> taxLines = new ArrayList<>();

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TransactionFeeLineEntity> feeLines = new ArrayList<>();

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private FlightMetadataEntity flightMetadata;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private HotelMetadataEntity hotelMetadata;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private RailMetadataEntity railMetadata;

    @OneToOne(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private NavanFeeMetadataEntity navanFeeMetadata;

    public TransactionEntity() {}

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    public void onPreUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TransactionTaxLineEntity> getTaxLines() {
        return taxLines;
    }

    public void setTaxLines(List<TransactionTaxLineEntity> taxLines) {
        this.taxLines = taxLines;
    }

    public List<TransactionFeeLineEntity> getFeeLines() {
        return feeLines;
    }

    public void setFeeLines(List<TransactionFeeLineEntity> feeLines) {
        this.feeLines = feeLines;
    }

    public FlightMetadataEntity getFlightMetadata() {
        return flightMetadata;
    }

    public void setFlightMetadata(FlightMetadataEntity flightMetadata) {
        this.flightMetadata = flightMetadata;
    }

    public HotelMetadataEntity getHotelMetadata() {
        return hotelMetadata;
    }

    public void setHotelMetadata(HotelMetadataEntity hotelMetadata) {
        this.hotelMetadata = hotelMetadata;
    }

    public RailMetadataEntity getRailMetadata() {
        return railMetadata;
    }

    public void setRailMetadata(RailMetadataEntity railMetadata) {
        this.railMetadata = railMetadata;
    }

    public NavanFeeMetadataEntity getNavanFeeMetadata() {
        return navanFeeMetadata;
    }

    public void setNavanFeeMetadata(NavanFeeMetadataEntity navanFeeMetadata) {
        this.navanFeeMetadata = navanFeeMetadata;
    }
}

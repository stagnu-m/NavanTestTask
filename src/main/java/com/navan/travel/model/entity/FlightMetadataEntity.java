package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "flight_metadata")
public class FlightMetadataEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @Column(name = "origin", nullable = false, length = 64)
    private String origin;

    @Column(name = "destination", nullable = false, length = 64)
    private String destination;

    @Column(name = "fare", nullable = false, precision = 19, scale = 2)
    private BigDecimal fare;

    @OneToMany(mappedBy = "flightMetadata", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("couponOrder ASC")
    private List<FlightCouponEntity> coupons = new ArrayList<>();

    public FlightMetadataEntity() {}

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

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public BigDecimal getFare() {
        return fare;
    }

    public void setFare(BigDecimal fare) {
        this.fare = fare;
    }

    public List<FlightCouponEntity> getCoupons() {
        return coupons;
    }

    public void setCoupons(List<FlightCouponEntity> coupons) {
        this.coupons = coupons;
    }
}

package com.navan.travel.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "flight_coupons", indexes = {
        @Index(name = "idx_coupon_flight_id", columnList = "flight_metadata_id")
})
public class FlightCouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_metadata_id", nullable = false)
    private FlightMetadataEntity flightMetadata;

    @Column(name = "coupon_order", nullable = false)
    private int couponOrder;

    @Column(name = "carrier", length = 32)
    private String carrier;

    @Column(name = "flight_number", nullable = false, length = 32)
    private String flightNumber;

    @Column(name = "from_location", nullable = false, length = 64)
    private String fromLocation;

    @Column(name = "to_location", nullable = false, length = 64)
    private String toLocation;

    @Column(name = "departure", nullable = false, length = 64)
    private String departure;

    @Column(name = "arrival", nullable = false, length = 64)
    private String arrival;

    public FlightCouponEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FlightMetadataEntity getFlightMetadata() {
        return flightMetadata;
    }

    public void setFlightMetadata(FlightMetadataEntity flightMetadata) {
        this.flightMetadata = flightMetadata;
    }

    public int getCouponOrder() {
        return couponOrder;
    }

    public void setCouponOrder(int couponOrder) {
        this.couponOrder = couponOrder;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public String getFromLocation() {
        return fromLocation;
    }

    public void setFromLocation(String fromLocation) {
        this.fromLocation = fromLocation;
    }

    public String getToLocation() {
        return toLocation;
    }

    public void setToLocation(String toLocation) {
        this.toLocation = toLocation;
    }

    public String getDeparture() {
        return departure;
    }

    public void setDeparture(String departure) {
        this.departure = departure;
    }

    public String getArrival() {
        return arrival;
    }

    public void setArrival(String arrival) {
        this.arrival = arrival;
    }
}

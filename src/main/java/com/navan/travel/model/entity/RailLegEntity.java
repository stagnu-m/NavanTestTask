package com.navan.travel.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "rail_legs", indexes = {
        @Index(name = "idx_leg_rail_id", columnList = "rail_metadata_id")
})
public class RailLegEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rail_metadata_id", nullable = false)
    private RailMetadataEntity railMetadata;

    @Column(name = "leg_order", nullable = false)
    private int legOrder;

    @Column(name = "origin", nullable = false, length = 128)
    private String origin;

    @Column(name = "destination", nullable = false, length = 128)
    private String destination;

    @Column(name = "departure", nullable = false, length = 64)
    private String departure;

    @Column(name = "arrival", nullable = false, length = 64)
    private String arrival;

    @Column(name = "train_number", length = 64)
    private String trainNumber;

    @Column(name = "travel_class", length = 64)
    private String travelClass;

    public RailLegEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RailMetadataEntity getRailMetadata() {
        return railMetadata;
    }

    public void setRailMetadata(RailMetadataEntity railMetadata) {
        this.railMetadata = railMetadata;
    }

    public int getLegOrder() {
        return legOrder;
    }

    public void setLegOrder(int legOrder) {
        this.legOrder = legOrder;
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

    public String getTrainNumber() {
        return trainNumber;
    }

    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }

    public String getTravelClass() {
        return travelClass;
    }

    public void setTravelClass(String travelClass) {
        this.travelClass = travelClass;
    }
}

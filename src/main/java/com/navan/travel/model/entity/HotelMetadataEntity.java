package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotel_metadata")
public class HotelMetadataEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "street", nullable = false)
    private String street;

    @Column(name = "city", nullable = false, length = 128)
    private String city;

    @Column(name = "country", nullable = false, length = 64)
    private String country;

    @Column(name = "check_in", nullable = false, length = 32)
    private String checkIn;

    @Column(name = "check_out", nullable = false, length = 32)
    private String checkOut;

    @Column(name = "nights", nullable = false)
    private int nights;

    @Column(name = "room_rate_per_night", nullable = false, precision = 19, scale = 2)
    private BigDecimal roomRatePerNight;

    @OneToMany(mappedBy = "hotelMetadata", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HotelExtraEntity> extras = new ArrayList<>();

    public HotelMetadataEntity() {}

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(String checkIn) {
        this.checkIn = checkIn;
    }

    public String getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(String checkOut) {
        this.checkOut = checkOut;
    }

    public int getNights() {
        return nights;
    }

    public void setNights(int nights) {
        this.nights = nights;
    }

    public BigDecimal getRoomRatePerNight() {
        return roomRatePerNight;
    }

    public void setRoomRatePerNight(BigDecimal roomRatePerNight) {
        this.roomRatePerNight = roomRatePerNight;
    }

    public List<HotelExtraEntity> getExtras() {
        return extras;
    }

    public void setExtras(List<HotelExtraEntity> extras) {
        this.extras = extras;
    }
}

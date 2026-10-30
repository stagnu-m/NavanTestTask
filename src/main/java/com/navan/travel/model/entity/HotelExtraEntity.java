package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "hotel_extras")
public class HotelExtraEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_metadata_id", nullable = false)
    private HotelMetadataEntity hotelMetadata;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    public HotelExtraEntity() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public HotelMetadataEntity getHotelMetadata() {
        return hotelMetadata;
    }

    public void setHotelMetadata(HotelMetadataEntity hotelMetadata) {
        this.hotelMetadata = hotelMetadata;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

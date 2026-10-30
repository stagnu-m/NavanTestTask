package com.navan.travel.model.entity;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rail_metadata")
public class RailMetadataEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "transaction_id")
    private TransactionEntity transaction;

    @OneToMany(mappedBy = "railMetadata", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("legOrder ASC")
    private List<RailLegEntity> legs = new ArrayList<>();

    public RailMetadataEntity() {}

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

    public List<RailLegEntity> getLegs() {
        return legs;
    }

    public void setLegs(List<RailLegEntity> legs) {
        this.legs = legs;
    }
}

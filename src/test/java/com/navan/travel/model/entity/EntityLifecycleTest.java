package com.navan.travel.model.entity;

import com.navan.travel.model.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class EntityLifecycleTest {

    @Test
    void testTransactionEntityLifecycle() {
        TransactionEntity entity = new TransactionEntity();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();

        // Second onPrePersist when already set (covers false branch)
        Instant prevCreated = entity.getCreatedAt();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isEqualTo(prevCreated);

        entity.onPreUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();

        // Accessors
        entity.setId("tx_1");
        assertThat(entity.getId()).isEqualTo("tx_1");
        entity.setType(TransactionType.FLIGHT);
        assertThat(entity.getType()).isEqualTo(TransactionType.FLIGHT);
        entity.setExternalId("ext_1");
        assertThat(entity.getExternalId()).isEqualTo("ext_1");
        entity.setCurrency("EUR");
        assertThat(entity.getCurrency()).isEqualTo("EUR");
        Instant now = Instant.now();
        entity.setOccurredAt(now);
        assertThat(entity.getOccurredAt()).isEqualTo(now);
        entity.setTotal(new BigDecimal("100.00"));
        assertThat(entity.getTotal()).isEqualByComparingTo("100.00");
        entity.setVersion(1L);
        assertThat(entity.getVersion()).isEqualTo(1L);
        entity.setTaxLines(new ArrayList<>());
        assertThat(entity.getTaxLines()).isEmpty();
        entity.setFeeLines(new ArrayList<>());
        assertThat(entity.getFeeLines()).isEmpty();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
    }

    @Test
    void testInvoiceEntityLifecycle() {
        InvoiceEntity entity = new InvoiceEntity();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();

        // Second onPrePersist when already set (covers false branch)
        Instant prevCreated = entity.getCreatedAt();
        entity.onPrePersist();
        assertThat(entity.getCreatedAt()).isEqualTo(prevCreated);

        entity.onPreUpdate();
        assertThat(entity.getUpdatedAt()).isNotNull();

        entity.setId("inv_1");
        assertThat(entity.getId()).isEqualTo("inv_1");
        entity.setCurrency("EUR");
        assertThat(entity.getCurrency()).isEqualTo("EUR");
        entity.setGrandTotal(new BigDecimal("200.00"));
        assertThat(entity.getGrandTotal()).isEqualByComparingTo("200.00");
        entity.setLines(new ArrayList<>());
        assertThat(entity.getLines()).isEmpty();
    }

    @Test
    void testInvoiceLineEntityAccessors() {
        InvoiceLineEntity line = new InvoiceLineEntity();
        line.setId(10L);
        assertThat(line.getId()).isEqualTo(10L);
        InvoiceEntity inv = new InvoiceEntity();
        line.setInvoice(inv);
        assertThat(line.getInvoice()).isEqualTo(inv);
        line.setTransactionId("tx_1");
        assertThat(line.getTransactionId()).isEqualTo("tx_1");
        TransactionEntity tx = new TransactionEntity();
        line.setTransaction(tx);
        assertThat(line.getTransaction()).isEqualTo(tx);
        line.setExternalId("ext_1");
        assertThat(line.getExternalId()).isEqualTo("ext_1");
        line.setTransactionType("flight");
        assertThat(line.getTransactionType()).isEqualTo("flight");
        line.setOccurredAt("2026-03-11T00:00:00Z");
        assertThat(line.getOccurredAt()).isEqualTo("2026-03-11T00:00:00Z");
        line.setTotal(new BigDecimal("50.00"));
        assertThat(line.getTotal()).isEqualByComparingTo("50.00");
        line.setTaxLinesJson("[]");
        assertThat(line.getTaxLinesJson()).isEqualTo("[]");
        line.setFeeLinesJson("[]");
        assertThat(line.getFeeLinesJson()).isEqualTo("[]");
        line.setMetadataJson("{}");
        assertThat(line.getMetadataJson()).isEqualTo("{}");
    }

    @Test
    void testOtherEntitiesAccessors() {
        FlightMetadataEntity flight = new FlightMetadataEntity();
        flight.setId("tx_f");
        assertThat(flight.getId()).isEqualTo("tx_f");
        TransactionEntity tx = new TransactionEntity();
        flight.setTransaction(tx);
        assertThat(flight.getTransaction()).isEqualTo(tx);
        flight.setCoupons(new ArrayList<>());
        assertThat(flight.getCoupons()).isEmpty();

        FlightCouponEntity coupon = new FlightCouponEntity();
        coupon.setId(1L);
        assertThat(coupon.getId()).isEqualTo(1L);

        HotelMetadataEntity hotel = new HotelMetadataEntity();
        hotel.setId("tx_h");
        assertThat(hotel.getId()).isEqualTo("tx_h");
        hotel.setTransaction(tx);
        assertThat(hotel.getTransaction()).isEqualTo(tx);
        hotel.setExtras(new ArrayList<>());
        assertThat(hotel.getExtras()).isEmpty();

        HotelExtraEntity extra = new HotelExtraEntity();
        extra.setId(2L);
        assertThat(extra.getId()).isEqualTo(2L);
        extra.setHotelMetadata(hotel);
        assertThat(extra.getHotelMetadata()).isEqualTo(hotel);

        RailMetadataEntity rail = new RailMetadataEntity();
        rail.setId("tx_r");
        assertThat(rail.getId()).isEqualTo("tx_r");
        rail.setTransaction(tx);
        assertThat(rail.getTransaction()).isEqualTo(tx);
        rail.setLegs(new ArrayList<>());
        assertThat(rail.getLegs()).isEmpty();

        RailLegEntity leg = new RailLegEntity();
        leg.setId(3L);
        assertThat(leg.getId()).isEqualTo(3L);
        leg.setRailMetadata(rail);
        assertThat(leg.getRailMetadata()).isEqualTo(rail);

        NavanFeeMetadataEntity fee = new NavanFeeMetadataEntity();
        fee.setId("tx_fee");
        assertThat(fee.getId()).isEqualTo("tx_fee");
        fee.setTransaction(tx);
        assertThat(fee.getTransaction()).isEqualTo(tx);

        TransactionTaxLineEntity taxLine = new TransactionTaxLineEntity();
        taxLine.setId(4L);
        assertThat(taxLine.getId()).isEqualTo(4L);

        TransactionFeeLineEntity feeLine = new TransactionFeeLineEntity();
        feeLine.setId(5L);
        assertThat(feeLine.getId()).isEqualTo(5L);
    }
}

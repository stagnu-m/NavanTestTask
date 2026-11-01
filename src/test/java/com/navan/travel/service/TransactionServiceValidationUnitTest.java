package com.navan.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.travel.dto.FeeLineDto;
import com.navan.travel.dto.TaxLineDto;
import com.navan.travel.dto.TransactionRequest;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.NotFoundException;
import com.navan.travel.model.TransactionType;
import com.navan.travel.model.entity.TransactionEntity;
import com.navan.travel.repository.InvoiceLineRepository;
import com.navan.travel.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceValidationUnitTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private InvoiceLineRepository invoiceLineRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, invoiceLineRepository, objectMapper, transactionManager);
    }

    @Test
    void testValidateMetadataNode_NullAndUnsupportedTypes() {
        assertThatThrownBy(() -> service.validateMetadataNode(null, objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown transaction type: null");

        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.UNSUPPORTED, objectMapper.createObjectNode()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown transaction type: unsupported");
    }

    @Test
    void testValidateMetadataNode_NullEmptyNonObject() {
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, null))
                .isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, NullNode.getInstance()))
                .isInstanceOf(BadRequestException.class);

        ArrayNode array = objectMapper.createArrayNode();
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, array))
                .isInstanceOf(BadRequestException.class);

        ObjectNode empty = objectMapper.createObjectNode();
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, empty))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testValidateMetadataNode_FlightBranches() {
        // Blank origin
        ObjectNode f1 = objectMapper.createObjectNode();
        f1.put("origin", "  ");
        f1.put("destination", "LHR");
        f1.put("fare", 100);
        f1.putArray("coupons").addObject().put("flight_number", "BA1");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f1))
                .isInstanceOf(BadRequestException.class);

        // Null destination
        ObjectNode f2 = objectMapper.createObjectNode();
        f2.put("origin", "TXL");
        f2.put("fare", 100);
        f2.putArray("coupons");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f2))
                .isInstanceOf(BadRequestException.class);

        // Blank destination
        ObjectNode f3 = objectMapper.createObjectNode();
        f3.put("origin", "TXL");
        f3.put("destination", "");
        f3.put("fare", 100);
        f3.putArray("coupons");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f3))
                .isInstanceOf(BadRequestException.class);

        // Null fare
        ObjectNode f4 = objectMapper.createObjectNode();
        f4.put("origin", "TXL");
        f4.put("destination", "LHR");
        f4.putArray("coupons");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f4))
                .isInstanceOf(BadRequestException.class);

        // Null coupons
        ObjectNode f5 = objectMapper.createObjectNode();
        f5.put("origin", "TXL");
        f5.put("destination", "LHR");
        f5.put("fare", 100);
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f5))
                .isInstanceOf(BadRequestException.class);

        // Coupon flightNumber null
        ObjectNode f6 = f5.deepCopy();
        ObjectNode c1 = f6.putArray("coupons").addObject();
        c1.put("from", "TXL").put("to", "LHR").put("departure", "2026-03-11T07:00:00Z").put("arrival", "2026-03-11T08:00:00Z");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);

        // Coupon flightNumber blank
        c1.put("flight_number", " ");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);

        // Coupon from null
        c1.put("flight_number", "BA1");
        c1.remove("from");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);

        // Coupon to null
        c1.put("from", "TXL");
        c1.remove("to");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);

        // Coupon departure null
        c1.put("to", "LHR");
        c1.remove("departure");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);

        // Coupon arrival null
        c1.put("departure", "2026-03-11T07:00:00Z");
        c1.remove("arrival");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.FLIGHT, f6))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testValidateMetadataNode_HotelBranches() {
        ObjectNode h = objectMapper.createObjectNode();

        // Name blank
        h.put("name", " ");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // Address null
        h.put("name", "Hotel Mitte");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // CheckIn null
        ObjectNode addr = h.putObject("address");
        addr.put("street", "12 Kingsway").put("city", "London").put("country", "GB");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // CheckOut null
        h.put("check_in", "2026-03-11");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // Nights null
        h.put("check_out", "2026-03-13");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // RoomRatePerNight null
        h.put("nights", 2);
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // Valid base
        h.put("room_rate_per_night", 140.0);

        // Address street null
        addr.remove("street");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // Address city null
        addr.put("street", "12 Kingsway");
        addr.remove("city");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);

        // Address country null
        addr.put("city", "London");
        addr.remove("country");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.HOTEL, h))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testValidateMetadataNode_RailBranches() {
        ObjectNode r = objectMapper.createObjectNode();

        // Legs null
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);

        // Legs empty
        ArrayNode legs = r.putArray("legs");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);

        // Leg origin null
        ObjectNode leg = legs.addObject();
        leg.put("destination", "Paris").put("departure", "2026-03-13T10:00:00Z").put("arrival", "2026-03-13T12:00:00Z");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);

        // Leg destination null
        leg.put("origin", "London");
        leg.remove("destination");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);

        // Leg departure null
        leg.put("destination", "Paris");
        leg.remove("departure");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);

        // Leg arrival null
        leg.put("departure", "2026-03-13T10:00:00Z");
        leg.remove("arrival");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.RAIL, r))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void testValidateMetadataNode_NavanFeeBranches() {
        ObjectNode nf = objectMapper.createObjectNode();
        nf.put("description", "Fee description");

        // feeKind null
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.NAVAN_FEE, nf))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown navan_fee fee_kind: null");

        // feeKind invalid
        nf.put("fee_kind", "vip_access");
        assertThatThrownBy(() -> service.validateMetadataNode(TransactionType.NAVAN_FEE, nf))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown navan_fee fee_kind: vip_access");

        // feeKind valid
        nf.put("fee_kind", "trip_fee");
        service.validateMetadataNode(TransactionType.NAVAN_FEE, nf);

        nf.put("fee_kind", "agent_call_fee");
        service.validateMetadataNode(TransactionType.NAVAN_FEE, nf);
    }

    @Test
    void testCreateTransaction_NullTypeAndRaceBranches() {
        TransactionRequest reqNullType = new TransactionRequest(
                null, "ext-1", "EUR", "2026-03-11T00:00:00Z", BigDecimal.TEN, List.of(), List.of(), objectMapper.createObjectNode());

        assertThatThrownBy(() -> service.createTransaction(reqNullType))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unknown transaction type: null");
    }

    @Test
    void testBuildMetadataDto_NullMetadataEntities() {
        TransactionEntity f = new TransactionEntity();
        f.setType(TransactionType.FLIGHT);
        assertThat(service.buildMetadataDto(f)).isEqualTo(Collections.emptyMap());

        TransactionEntity h = new TransactionEntity();
        h.setType(TransactionType.HOTEL);
        assertThat(service.buildMetadataDto(h)).isEqualTo(Collections.emptyMap());

        TransactionEntity r = new TransactionEntity();
        r.setType(TransactionType.RAIL);
        assertThat(service.buildMetadataDto(r)).isEqualTo(Collections.emptyMap());

        TransactionEntity nf = new TransactionEntity();
        nf.setType(TransactionType.NAVAN_FEE);
        assertThat(service.buildMetadataDto(nf)).isEqualTo(Collections.emptyMap());

        TransactionEntity u = new TransactionEntity();
        u.setType(TransactionType.UNSUPPORTED);
        assertThat(service.buildMetadataDto(u)).isEqualTo(Collections.emptyMap());
    }

    @Test
    void testUpdateTransaction_TaxFeeLinesAndMetadataBranches() {
        TransactionEntity existing = new TransactionEntity();
        existing.setId("txn_test");
        existing.setType(TransactionType.FLIGHT);
        existing.setTotal(new BigDecimal("220.00"));
        existing.setCurrency("EUR");
        existing.setOccurredAt(Instant.now());

        when(transactionRepository.findById("txn_test")).thenReturn(Optional.of(existing));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 1. Update with taxLines only (feeLines null)
        TransactionRequest req1 = new TransactionRequest(
                null, null, null, null, null,
                List.of(new TaxLineDto("Tax 1", null, new BigDecimal("10.00"))),
                null, null);
        service.updateTransaction("txn_test", req1);
        assertThat(existing.getTaxLines()).hasSize(1);
        assertThat(existing.getFeeLines()).isEmpty();

        // 2. Update with feeLines only (taxLines null)
        TransactionRequest req2 = new TransactionRequest(
                null, null, null, null, null, null,
                List.of(new FeeLineDto("Fee 1", new BigDecimal("5.00"))),
                null);
        service.updateTransaction("txn_test", req2);
        assertThat(existing.getFeeLines()).hasSize(1);
        assertThat(existing.getTaxLines()).isEmpty();

        // 3. Update with metadata and explicit type
        ObjectNode flightMeta = objectMapper.createObjectNode();
        flightMeta.put("origin", "BER").put("destination", "CDG").put("fare", 150.0);
        ObjectNode c = flightMeta.putArray("coupons").addObject();
        c.put("flight_number", "AF101").put("from", "BER").put("to", "CDG")
                .put("departure", "2026-03-11T10:00:00Z").put("arrival", "2026-03-11T12:00:00Z");

        TransactionRequest req3 = new TransactionRequest(
                TransactionType.FLIGHT, null, null, null, null, null, null, flightMeta);
        service.updateTransaction("txn_test", req3);
        assertThat(existing.getFlightMetadata()).isNotNull();
        assertThat(existing.getFlightMetadata().getOrigin()).isEqualTo("BER");

        // 4. Update with metadata without type (uses entity.getType())
        flightMeta.put("origin", "FRA");
        TransactionRequest req4 = new TransactionRequest(
                null, null, null, null, null, null, null, flightMeta);
        service.updateTransaction("txn_test", req4);
        assertThat(existing.getFlightMetadata().getOrigin()).isEqualTo("FRA");

        // 5. Update hotel metadata without extras (covers dto.extras() == null branch)
        existing.setType(TransactionType.HOTEL);
        ObjectNode hotelMeta = objectMapper.createObjectNode();
        hotelMeta.put("name", "Hotel").put("check_in", "2026-03-11").put("check_out", "2026-03-13")
                .put("nights", 2).put("room_rate_per_night", 100.0);
        hotelMeta.putObject("address").put("street", "Str").put("city", "City").put("country", "DE");
        TransactionRequest req5 = new TransactionRequest(
                TransactionType.HOTEL, null, null, null, null, null, null, hotelMeta);
        service.updateTransaction("txn_test", req5);
        assertThat(existing.getHotelMetadata()).isNotNull();
        assertThat(existing.getHotelMetadata().getExtras()).isEmpty();

        // 6. Update rail metadata
        existing.setType(TransactionType.RAIL);
        ObjectNode railMeta = objectMapper.createObjectNode();
        railMeta.putArray("legs").addObject()
                .put("origin", "Berlin").put("destination", "Munich")
                .put("departure", "2026-03-11T08:00:00Z").put("arrival", "2026-03-11T12:00:00Z");
        TransactionRequest req6 = new TransactionRequest(
                TransactionType.RAIL, null, null, null, null, null, null, railMeta);
        service.updateTransaction("txn_test", req6);
        assertThat(existing.getRailMetadata()).isNotNull();

        // 7. Update navan_fee metadata
        existing.setType(TransactionType.NAVAN_FEE);
        ObjectNode feeMeta = objectMapper.createObjectNode();
        feeMeta.put("fee_kind", "trip_fee").put("description", "Trip fee");
        TransactionRequest req7 = new TransactionRequest(
                TransactionType.NAVAN_FEE, null, null, null, null, null, null, feeMeta);
        service.updateTransaction("txn_test", req7);
        assertThat(existing.getNavanFeeMetadata()).isNotNull();
    }
}

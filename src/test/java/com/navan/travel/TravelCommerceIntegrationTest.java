package com.navan.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.travel.dto.CreateInvoiceRequest;
import com.navan.travel.dto.InvoiceResponse;
import com.navan.travel.dto.TransactionResponse;
import com.navan.travel.model.entity.TransactionEntity;
import com.navan.travel.repository.InvoiceLineRepository;
import com.navan.travel.repository.InvoiceRepository;
import com.navan.travel.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class TravelCommerceIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InvoiceLineRepository invoiceLineRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode seeds;

    @BeforeEach
    void setUp() throws Exception {
        restTemplate.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());

        invoiceLineRepository.deleteAll();
        invoiceRepository.deleteAll();
        transactionRepository.deleteAll();

        // Load fixtures
        Path seedPath = Path.of("task-b/fixtures/task-b/seed-payloads.json");
        if (!Files.exists(seedPath)) {
            seedPath = Path.of("../task-b/fixtures/task-b/seed-payloads.json");
        }
        seeds = objectMapper.readTree(Files.readString(seedPath));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("1. Create flight, hotel, rail, and two navan_fee kinds; GET returns full metadata")
    void test1_createSeedTransactions_fullMetadataReturned() {
        // Create flight
        ResponseEntity<JsonNode> flightRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        assertThat(flightRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String flightId = flightRes.getBody().get("id").asText();

        // Verify flight GET
        ResponseEntity<JsonNode> getFlight = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getFlight.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode flightMeta = getFlight.getBody().get("metadata");
        assertThat(flightMeta.get("origin").asText()).isEqualTo("TXL");
        assertThat(flightMeta.get("destination").asText()).isEqualTo("LHR");
        assertThat(flightMeta.get("coupons").isArray()).isTrue();
        assertThat(flightMeta.get("coupons").get(0).get("flight_number").asText()).isEqualTo("BA982");

        // Create hotel
        ResponseEntity<JsonNode> hotelRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("hotel"), jsonHeaders()),
                JsonNode.class);
        assertThat(hotelRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String hotelId = hotelRes.getBody().get("id").asText();

        // Verify hotel GET
        ResponseEntity<JsonNode> getHotel = restTemplate.getForEntity("/transactions/" + hotelId, JsonNode.class);
        JsonNode hotelMeta = getHotel.getBody().get("metadata");
        assertThat(hotelMeta.get("name").asText()).isEqualTo("Hotel Mitte London");
        assertThat(hotelMeta.get("address").get("street").asText()).isEqualTo("12 Kingsway");
        assertThat(hotelMeta.get("address").get("city").asText()).isEqualTo("London");
        assertThat(hotelMeta.get("address").get("country").asText()).isEqualTo("GB");

        // Create rail
        ResponseEntity<JsonNode> railRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("rail"), jsonHeaders()),
                JsonNode.class);
        assertThat(railRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String railId = railRes.getBody().get("id").asText();

        // Verify rail GET
        ResponseEntity<JsonNode> getRail = restTemplate.getForEntity("/transactions/" + railId, JsonNode.class);
        JsonNode railMeta = getRail.getBody().get("metadata");
        assertThat(railMeta.get("legs").size()).isEqualTo(2);
        assertThat(railMeta.get("legs").get(0).get("origin").asText()).isEqualTo("London St Pancras");
        assertThat(railMeta.get("legs").get(1).get("destination").asText()).isEqualTo("Paris Nord");

        // Create trip_fee
        ResponseEntity<JsonNode> tripFeeRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("trip_fee"), jsonHeaders()),
                JsonNode.class);
        assertThat(tripFeeRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tripFeeId = tripFeeRes.getBody().get("id").asText();

        ResponseEntity<JsonNode> getTripFee = restTemplate.getForEntity("/transactions/" + tripFeeId, JsonNode.class);
        assertThat(getTripFee.getBody().get("metadata").get("fee_kind").asText()).isEqualTo("trip_fee");

        // Create agent_call_fee
        ResponseEntity<JsonNode> agentFeeRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("agent_call_fee"), jsonHeaders()),
                JsonNode.class);
        assertThat(agentFeeRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String agentFeeId = agentFeeRes.getBody().get("id").asText();

        ResponseEntity<JsonNode> getAgentFee = restTemplate.getForEntity("/transactions/" + agentFeeId, JsonNode.class);
        assertThat(getAgentFee.getBody().get("metadata").get("fee_kind").asText()).isEqualTo("agent_call_fee");
    }

    @Test
    @DisplayName("2. Duplicate external_id: second POST returns 409 with existing id; exactly one row stored")
    void test2_duplicateExternalId_returns409WithExistingId() {
        ResponseEntity<JsonNode> res1 = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String existingId = res1.getBody().get("id").asText();

        // Second POST with same external_id
        ResponseEntity<JsonNode> res2 = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("duplicate_flight"), jsonHeaders()),
                JsonNode.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody().get("id").asText()).isEqualTo(existingId);

        // Verify only 1 row exists
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("3. Invoice by transaction ids: trip invoice totals match expected-invoice.json (grand total 685.70 EUR)")
    void test3_tripInvoiceTotalsAndRollupsMatchExpected() {
        String flightId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();
        String hotelId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("hotel"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();
        String railId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("rail"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();
        String tripFeeId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("trip_fee"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();
        String agentFeeId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("agent_call_fee"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(null, List.of(flightId, hotelId, railId, tripFeeId, agentFeeId));
        ResponseEntity<JsonNode> invoiceRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);

        assertThat(invoiceRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = invoiceRes.getBody();

        assertThat(body.get("currency").asText()).isEqualTo("EUR");
        JsonNode totals = body.get("totals");
        assertThat(totals.get("flight").asDouble()).isEqualTo(220.00);
        assertThat(totals.get("hotel").asDouble()).isEqualTo(331.70);
        assertThat(totals.get("rail").asDouble()).isEqualTo(94.00);
        assertThat(totals.get("trip_fee").asDouble()).isEqualTo(25.00);
        assertThat(totals.get("agent_call_fee").asDouble()).isEqualTo(15.00);
        assertThat(totals.get("grand_total").asDouble()).isEqualTo(685.70);

        // Tax rollup verification
        JsonNode taxRollup = body.get("tax_rollup");
        assertThat(taxRollup.size()).isEqualTo(2);
        boolean foundAirportTax = false;
        boolean foundVat = false;
        for (JsonNode t : taxRollup) {
            if ("Airport tax".equals(t.get("name").asText())) {
                assertThat(t.get("amount").asDouble()).isEqualTo(15.00);
                foundAirportTax = true;
            } else if ("VAT".equals(t.get("name").asText())) {
                assertThat(t.get("rate").asDouble()).isEqualTo(0.07);
                assertThat(t.get("amount").asDouble()).isEqualTo(21.70);
                foundVat = true;
            }
        }
        assertThat(foundAirportTax).isTrue();
        assertThat(foundVat).isTrue();

        // Fee rollup verification
        JsonNode feeRollup = body.get("fee_rollup");
        assertThat(feeRollup.size()).isEqualTo(2);
        boolean foundYq = false;
        boolean foundBookingFee = false;
        for (JsonNode f : feeRollup) {
            if ("YQ".equals(f.get("name").asText())) {
                assertThat(f.get("amount").asDouble()).isEqualTo(25.00);
                foundYq = true;
            } else if ("Booking fee".equals(f.get("name").asText())) {
                assertThat(f.get("amount").asDouble()).isEqualTo(4.00);
                foundBookingFee = true;
            }
        }
        assertThat(foundYq).isTrue();
        assertThat(foundBookingFee).isTrue();
    }

    @Test
    @DisplayName("4. Snapshot: mutating underlying transaction table row does not alter existing invoice")
    void test4_invoiceSnapshotImmutability() {
        String flightId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        ResponseEntity<JsonNode> invoiceRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);
        String invoiceId = invoiceRes.getBody().get("id").asText();
        assertThat(invoiceRes.getBody().get("totals").get("grand_total").asDouble()).isEqualTo(220.00);

        // Mutate stored transaction in the database directly
        TransactionEntity tx = transactionRepository.findById(flightId).orElseThrow();
        tx.setTotal(new BigDecimal("999.00"));
        transactionRepository.saveAndFlush(tx);

        // Fetch invoice: snapshot totals must remain frozen at 220.00
        ResponseEntity<JsonNode> getInvoice = restTemplate.getForEntity("/invoices/" + invoiceId, JsonNode.class);
        assertThat(getInvoice.getBody().get("totals").get("flight").asDouble()).isEqualTo(220.00);
        assertThat(getInvoice.getBody().get("totals").get("grand_total").asDouble()).isEqualTo(220.00);
    }

    @Test
    @DisplayName("5. Mixed currency: invoice of EUR + USD returns 409")
    void test5_mixedCurrency_returns409() throws Exception {
        String eurId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        // Create USD transaction
        String usdPayload = objectMapper.writeValueAsString(seeds.get("hotel")).replace("\"EUR\"", "\"USD\"").replace("hotel-LON-2026-03-11", "hotel-USD-2026");
        String usdId = restTemplate.postForEntity("/transactions", new HttpEntity<>(usdPayload, jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(null, List.of(eurId, usdId));
        ResponseEntity<JsonNode> invoiceRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);
        assertThat(invoiceRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("6. Concurrent duplicate create: overlapping POSTs with same external_id leave exactly one row")
    void test6_concurrentDuplicateCreate_leavesOneRow() throws Exception {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(1);

        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        JsonNode payload = seeds.get("flight");

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return restTemplate.postForEntity("/transactions", new HttpEntity<>(payload, jsonHeaders()), JsonNode.class);
            }));
        }

        latch.countDown(); // Release all threads concurrently
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        int createdCount = 0;
        int conflictCount = 0;
        String createdId = null;

        for (Future<ResponseEntity<JsonNode>> f : futures) {
            ResponseEntity<JsonNode> res = f.get();
            if (res.getStatusCode() == HttpStatus.CREATED) {
                createdCount++;
                createdId = res.getBody().get("id").asText();
            } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                conflictCount++;
                assertThat(res.getBody().get("id").asText()).isNotBlank();
            }
        }

        assertThat(createdCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threads - 1);
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("7. PATCH transaction after it is on an invoice returns 409; invoice snapshot totals unchanged")
    void test7_patchInvoicedTransaction_returns409() throws Exception {
        String flightId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        String invoiceId = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        // Attempt PATCH on invoiced transaction
        String patchBody = "{\"total\": 500.00}";
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> patchEntity = new HttpEntity<>(patchBody, headers);

        ResponseEntity<JsonNode> patchRes = restTemplate.exchange("/transactions/" + flightId, HttpMethod.PATCH, patchEntity, JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Transaction total remains 220.00
        ResponseEntity<JsonNode> getTx = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getTx.getBody().get("total").asDouble()).isEqualTo(220.00);

        // Invoice total remains 220.00
        ResponseEntity<JsonNode> getInv = restTemplate.getForEntity("/invoices/" + invoiceId, JsonNode.class);
        assertThat(getInv.getBody().get("totals").get("grand_total").asDouble()).isEqualTo(220.00);
    }

    @Test
    @DisplayName("8. DELETE transaction on an invoice returns 409; row still exists")
    void test8_deleteInvoicedTransaction_returns409() {
        String flightId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);

        // Attempt DELETE
        ResponseEntity<JsonNode> delRes = restTemplate.exchange("/transactions/" + flightId, HttpMethod.DELETE, null, JsonNode.class);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Verify transaction still exists
        ResponseEntity<JsonNode> getTx = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getTx.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("9. DELETE invoice removes the invoice; transactions still GET")
    void test9_deleteInvoice_preservesTransactions() {
        String flightId = restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        String invoiceId = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class).getBody().get("id").asText();

        // DELETE invoice
        ResponseEntity<Void> delRes = restTemplate.exchange("/invoices/" + invoiceId, HttpMethod.DELETE, null, Void.class);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Invoice is gone
        ResponseEntity<JsonNode> getInv = restTemplate.getForEntity("/invoices/" + invoiceId, JsonNode.class);
        assertThat(getInv.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Transaction still exists
        ResponseEntity<JsonNode> getTx = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getTx.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("10. GET /transactions without unbounded dump: default page size 50 and limit=1000 is rejected with 400")
    void test10_paginationBounds() throws Exception {
        // Seed 55 simple transactions
        for (int i = 0; i < 55; i++) {
            String payload = objectMapper.writeValueAsString(seeds.get("flight"))
                    .replace("flight-TXL-LHR-2026-03-11", "flight-batch-" + i);
            restTemplate.postForEntity("/transactions", new HttpEntity<>(payload, jsonHeaders()), JsonNode.class);
        }

        // Default query: should return 50 items
        ResponseEntity<JsonNode> defaultRes = restTemplate.getForEntity("/transactions", JsonNode.class);
        assertThat(defaultRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(defaultRes.getBody().size()).isEqualTo(50);

        // limit=1000 exceeds max allowed (100) -> 400 Bad Request
        ResponseEntity<JsonNode> cappedRes = restTemplate.getForEntity("/transactions?limit=1000", JsonNode.class);
        assertThat(cappedRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // offset=50, limit=10 -> remaining 5 items
        ResponseEntity<JsonNode> offsetRes = restTemplate.getForEntity("/transactions?offset=50&limit=10", JsonNode.class);
        assertThat(offsetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(offsetRes.getBody().size()).isEqualTo(5);
    }

    @Test
    @DisplayName("11. Unknown type or navan_fee fee_kind returns 400")
    void test11_unknownTypeAndFeeKind_returns400() {
        // Unknown transaction type
        ObjectNode unknownType = (ObjectNode) seeds.get("flight").deepCopy();
        unknownType.put("type", "spaceship");
        unknownType.put("external_id", "spaceship-test-11");
        ResponseEntity<JsonNode> res1 = restTemplate.postForEntity("/transactions", new HttpEntity<>(unknownType, jsonHeaders()), JsonNode.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Unknown navan_fee fee_kind
        ObjectNode unknownFeeKind = (ObjectNode) seeds.get("trip_fee").deepCopy();
        ((ObjectNode) unknownFeeKind.get("metadata")).put("fee_kind", "vip_lounge_fee");
        unknownFeeKind.put("external_id", "fee-unknown-test-11");
        ResponseEntity<JsonNode> res2 = restTemplate.postForEntity("/transactions", new HttpEntity<>(unknownFeeKind, jsonHeaders()), JsonNode.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("12. Health check returns UP")
    void test12_healthEndpoint() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity("/health", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("status").asText()).isEqualTo("UP");
    }
}

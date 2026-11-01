package com.navan.travel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.navan.travel.dto.CreateInvoiceRequest;
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
import org.springframework.lang.NonNull;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
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

    @NonNull
    private static <T> T body(ResponseEntity<T> response) {
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        return Objects.requireNonNull(response.getBody());
    }

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
        String flightId = body(flightRes).get("id").asText();

        // Verify flight GET
        ResponseEntity<JsonNode> getFlight = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getFlight.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode flightMeta = body(getFlight).get("metadata");
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
        String hotelId = body(hotelRes).get("id").asText();

        // Verify hotel GET
        ResponseEntity<JsonNode> getHotel = restTemplate.getForEntity("/transactions/" + hotelId, JsonNode.class);
        JsonNode hotelMeta = body(getHotel).get("metadata");
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
        String railId = body(railRes).get("id").asText();

        // Verify rail GET
        ResponseEntity<JsonNode> getRail = restTemplate.getForEntity("/transactions/" + railId, JsonNode.class);
        JsonNode railMeta = body(getRail).get("metadata");
        assertThat(railMeta.get("legs").size()).isEqualTo(2);
        assertThat(railMeta.get("legs").get(0).get("origin").asText()).isEqualTo("London St Pancras");
        assertThat(railMeta.get("legs").get(1).get("destination").asText()).isEqualTo("Paris Nord");

        // Create trip_fee
        ResponseEntity<JsonNode> tripFeeRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("trip_fee"), jsonHeaders()),
                JsonNode.class);
        assertThat(tripFeeRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tripFeeId = body(tripFeeRes).get("id").asText();

        ResponseEntity<JsonNode> getTripFee = restTemplate.getForEntity("/transactions/" + tripFeeId, JsonNode.class);
        assertThat(body(getTripFee).get("metadata").get("fee_kind").asText()).isEqualTo("trip_fee");

        // Create agent_call_fee
        ResponseEntity<JsonNode> agentFeeRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("agent_call_fee"), jsonHeaders()),
                JsonNode.class);
        assertThat(agentFeeRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String agentFeeId = body(agentFeeRes).get("id").asText();

        ResponseEntity<JsonNode> getAgentFee = restTemplate.getForEntity("/transactions/" + agentFeeId, JsonNode.class);
        assertThat(body(getAgentFee).get("metadata").get("fee_kind").asText()).isEqualTo("agent_call_fee");
    }

    @Test
    @DisplayName("2. Duplicate external_id: second POST returns 409 with existing id; exactly one row stored")
    void test2_duplicateExternalId_returns409WithExistingId() {
        ResponseEntity<JsonNode> res1 = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String existingId = body(res1).get("id").asText();

        // Second POST with same external_id
        ResponseEntity<JsonNode> res2 = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("duplicate_flight"), jsonHeaders()),
                JsonNode.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(body(res2).get("id").asText()).isEqualTo(existingId);

        // Verify only 1 row exists
        assertThat(transactionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("3. Invoice by transaction ids: trip invoice totals match expected-invoice.json (grand total 685.70 EUR)")
    void test3_tripInvoiceTotalsAndRollupsMatchExpected() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String hotelId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("hotel"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String railId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("rail"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String tripFeeId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("trip_fee"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String agentFeeId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("agent_call_fee"), jsonHeaders()), JsonNode.class)).get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(null, List.of(flightId, hotelId, railId, tripFeeId, agentFeeId));
        ResponseEntity<JsonNode> invoiceRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);

        assertThat(invoiceRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = body(invoiceRes);

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
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        ResponseEntity<JsonNode> invoiceRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class);
        String invoiceId = body(invoiceRes).get("id").asText();
        assertThat(body(invoiceRes).get("totals").get("grand_total").asDouble()).isEqualTo(220.00);

        // Mutate stored transaction in the database directly
        TransactionEntity tx = transactionRepository.findById(flightId).orElseThrow();
        tx.setTotal(new BigDecimal("999.00"));
        transactionRepository.saveAndFlush(tx);

        // Fetch invoice: snapshot totals must remain frozen at 220.00
        ResponseEntity<JsonNode> getInvoice = restTemplate.getForEntity("/invoices/" + invoiceId, JsonNode.class);
        assertThat(body(getInvoice).get("totals").get("flight").asDouble()).isEqualTo(220.00);
        assertThat(body(getInvoice).get("totals").get("grand_total").asDouble()).isEqualTo(220.00);
    }

    @Test
    @DisplayName("5. Mixed currency: invoice of EUR + USD returns 409")
    void test5_mixedCurrency_returns409() throws Exception {
        String eurId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        // Create USD transaction
        String usdPayload = objectMapper.writeValueAsString(seeds.get("hotel")).replace("\"EUR\"", "\"USD\"").replace("hotel-LON-2026-03-11", "hotel-USD-2026");
        String usdId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(usdPayload, jsonHeaders()), JsonNode.class)).get("id").asText();

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
                createdId = body(res).get("id").asText();
            } else if (res.getStatusCode() == HttpStatus.CONFLICT) {
                conflictCount++;
                assertThat(body(res).get("id").asText()).isNotBlank();
            } else {
                System.err.println("UNEXPECTED STATUS in test6: " + res.getStatusCode() + " body=" + body(res));
            }
        }

        assertThat(createdCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(threads - 1);
        assertThat(createdId).isNotBlank();
        assertThat(transactionRepository.findAll()).hasSize(1);
        assertThat(transactionRepository.findAll().get(0).getId()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("7. PATCH transaction after it is on an invoice returns 409; invoice snapshot totals unchanged")
    void test7_patchInvoicedTransaction_returns409() throws Exception {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        String invoiceId = body(restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class)).get("id").asText();

        // Attempt PATCH on invoiced transaction
        String patchBody = "{\"total\": 500.00}";
        HttpHeaders headers = jsonHeaders();
        HttpEntity<String> patchEntity = new HttpEntity<>(patchBody, headers);

        ResponseEntity<JsonNode> patchRes = restTemplate.exchange("/transactions/" + flightId, HttpMethod.PATCH, patchEntity, JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Transaction total remains 220.00
        ResponseEntity<JsonNode> getTx = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(body(getTx).get("total").asDouble()).isEqualTo(220.00);

        // Invoice total remains 220.00
        ResponseEntity<JsonNode> getInv = restTemplate.getForEntity("/invoices/" + invoiceId, JsonNode.class);
        assertThat(body(getInv).get("totals").get("grand_total").asDouble()).isEqualTo(220.00);
    }

    @Test
    @DisplayName("8. DELETE transaction on an invoice returns 409; row still exists")
    void test8_deleteInvoicedTransaction_returns409() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

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
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest(flightId, null);
        String invoiceId = body(restTemplate.postForEntity("/invoices", new HttpEntity<>(invoiceReq, jsonHeaders()), JsonNode.class)).get("id").asText();

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
        assertThat(body(defaultRes).size()).isEqualTo(50);

        // limit=1000 exceeds max allowed (100) -> 400 Bad Request
        ResponseEntity<JsonNode> cappedRes = restTemplate.getForEntity("/transactions?limit=1000", JsonNode.class);
        assertThat(cappedRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // offset=50, limit=10 -> remaining 5 items
        ResponseEntity<JsonNode> offsetRes = restTemplate.getForEntity("/transactions?offset=50&limit=10", JsonNode.class);
        assertThat(offsetRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(offsetRes).size()).isEqualTo(5);
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
    @DisplayName("12. Health check returns UP, including database and disk components")
    void test12_healthEndpoint() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity("/health", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode health = body(res);
        assertThat(health.get("status").asText()).isEqualTo("UP");
        assertThat(health.path("components").path("db").path("status").asText()).isEqualTo("UP");
        assertThat(health.path("components").path("diskSpace").path("status").asText()).isEqualTo("UP");
    }

    @Test
    @DisplayName("13. GET /transactions/{id} not found returns 404")
    void test13_getTransaction_notFound_returns404() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity("/transactions/txn_nonexistent", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(body(res).get("error").asText()).contains("not found");
    }

    @Test
    @DisplayName("14. GET /transactions with negative offset or zero limit returns 400")
    void test14_listTransactions_invalidPagination_returns400() {
        ResponseEntity<JsonNode> res1 = restTemplate.getForEntity("/transactions?limit=0", JsonNode.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> res2 = restTemplate.getForEntity("/transactions?limit=-5", JsonNode.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> res3 = restTemplate.getForEntity("/transactions?offset=-1", JsonNode.class);
        assertThat(res3.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("15. PATCH unbilled transaction succeeds and updates fields")
    void test15_patchUnbilledTransaction_success() {
        ResponseEntity<JsonNode> createRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        String txId = body(createRes).get("id").asText();

        String patchJson = "{\"total\": 299.99, \"occurred_at\": \"2026-03-12T10:00:00Z\"}";
        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/transactions/" + txId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchJson, jsonHeaders()),
                JsonNode.class);

        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(patchRes).get("total").asDouble()).isEqualTo(299.99);
        assertThat(body(patchRes).get("occurred_at").asText()).isEqualTo("2026-03-12T10:00:00Z");

        // Verify GET also returns the updated total
        ResponseEntity<JsonNode> getRes = restTemplate.getForEntity("/transactions/" + txId, JsonNode.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(getRes).get("total").asDouble()).isEqualTo(299.99);
    }

    @Test
    @DisplayName("16. PATCH non-existent transaction returns 404")
    void test16_patchTransaction_notFound_returns404() {
        String patchJson = "{\"total\": 100.00}";
        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/transactions/txn_nonexistent",
                HttpMethod.PATCH,
                new HttpEntity<>(patchJson, jsonHeaders()),
                JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("17. PATCH transaction with invalid type or invalid date returns 400")
    void test17_patchTransaction_invalidData_returns400() {
        ResponseEntity<JsonNode> createRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        String txId = body(createRes).get("id").asText();

        // Invalid type
        ResponseEntity<JsonNode> res1 = restTemplate.exchange(
                "/transactions/" + txId,
                HttpMethod.PATCH,
                new HttpEntity<>("{\"type\": \"spaceship\"}", jsonHeaders()),
                JsonNode.class);
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Invalid date
        ResponseEntity<JsonNode> res2 = restTemplate.exchange(
                "/transactions/" + txId,
                HttpMethod.PATCH,
                new HttpEntity<>("{\"occurred_at\": \"not-a-date\"}", jsonHeaders()),
                JsonNode.class);
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("18. DELETE unbilled transaction succeeds (204) and removes record")
    void test18_deleteUnbilledTransaction_success() {
        ResponseEntity<JsonNode> createRes = restTemplate.postForEntity(
                "/transactions",
                new HttpEntity<>(seeds.get("flight"), jsonHeaders()),
                JsonNode.class);
        String txId = body(createRes).get("id").asText();

        ResponseEntity<Void> delRes = restTemplate.exchange(
                "/transactions/" + txId,
                HttpMethod.DELETE,
                null,
                Void.class);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify GET returns 404
        ResponseEntity<JsonNode> getRes = restTemplate.getForEntity("/transactions/" + txId, JsonNode.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Verify database is empty
        assertThat(transactionRepository.findById(txId)).isEmpty();
    }

    @Test
    @DisplayName("19. DELETE non-existent transaction returns 404")
    void test19_deleteTransaction_notFound_returns404() {
        ResponseEntity<Void> delRes = restTemplate.exchange(
                "/transactions/txn_nonexistent",
                HttpMethod.DELETE,
                null,
                Void.class);
        assertThat(delRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("20. POST /transactions validation errors: empty metadata, cross-type metadata, malformed JSON return 400")
    void test20_createTransaction_validationErrors_returns400() {
        // Empty metadata {}
        ObjectNode emptyMeta = (ObjectNode) seeds.get("flight").deepCopy();
        emptyMeta.set("metadata", objectMapper.createObjectNode());
        emptyMeta.put("external_id", "empty-meta-tx");
        ResponseEntity<JsonNode> resEmpty = restTemplate.postForEntity("/transactions", new HttpEntity<>(emptyMeta, jsonHeaders()), JsonNode.class);
        assertThat(resEmpty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Flight with hotel metadata (do not store flight as hotel blob)
        ObjectNode flightWithHotelMeta = (ObjectNode) seeds.get("flight").deepCopy();
        flightWithHotelMeta.set("metadata", seeds.get("hotel").get("metadata"));
        flightWithHotelMeta.put("external_id", "flight-with-hotel-meta");
        ResponseEntity<JsonNode> resCross = restTemplate.postForEntity("/transactions", new HttpEntity<>(flightWithHotelMeta, jsonHeaders()), JsonNode.class);
        assertThat(resCross.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Flight with empty coupons array
        ObjectNode flightEmptyCoupons = (ObjectNode) seeds.get("flight").deepCopy();
        ((ObjectNode) flightEmptyCoupons.get("metadata")).putArray("coupons");
        flightEmptyCoupons.put("external_id", "flight-empty-coupons");
        ResponseEntity<JsonNode> resEmptyCoupons = restTemplate.postForEntity("/transactions", new HttpEntity<>(flightEmptyCoupons, jsonHeaders()), JsonNode.class);
        assertThat(resEmptyCoupons.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Hotel with missing address fields
        ObjectNode hotelMissingAddress = (ObjectNode) seeds.get("hotel").deepCopy();
        ((ObjectNode) hotelMissingAddress.get("metadata")).set("address", objectMapper.createObjectNode());
        hotelMissingAddress.put("external_id", "hotel-missing-addr");
        ResponseEntity<JsonNode> resHotelBad = restTemplate.postForEntity("/transactions", new HttpEntity<>(hotelMissingAddress, jsonHeaders()), JsonNode.class);
        assertThat(resHotelBad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Rail with empty legs
        ObjectNode railEmptyLegs = (ObjectNode) seeds.get("rail").deepCopy();
        ((ObjectNode) railEmptyLegs.get("metadata")).putArray("legs");
        railEmptyLegs.put("external_id", "rail-empty-legs");
        ResponseEntity<JsonNode> resRailBad = restTemplate.postForEntity("/transactions", new HttpEntity<>(railEmptyLegs, jsonHeaders()), JsonNode.class);
        assertThat(resRailBad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Invalid ISO timestamp
        ObjectNode badDate = (ObjectNode) seeds.get("flight").deepCopy();
        badDate.put("occurred_at", "invalid-date");
        badDate.put("external_id", "bad-date-tx");
        ResponseEntity<JsonNode> resDate = restTemplate.postForEntity("/transactions", new HttpEntity<>(badDate, jsonHeaders()), JsonNode.class);
        assertThat(resDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Missing required field: external_id missing
        ObjectNode missingExtId = (ObjectNode) seeds.get("flight").deepCopy();
        missingExtId.remove("external_id");
        ResponseEntity<JsonNode> resMissingField = restTemplate.postForEntity("/transactions", new HttpEntity<>(missingExtId, jsonHeaders()), JsonNode.class);
        assertThat(resMissingField.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Malformed JSON string
        ResponseEntity<JsonNode> resMalformed = restTemplate.postForEntity("/transactions", new HttpEntity<>("{not json}", jsonHeaders()), JsonNode.class);
        assertThat(resMalformed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("21. POST /invoices failures: empty ids, not found tx, duplicate tx in list")
    void test21_createInvoice_failures() {
        // Empty request
        ResponseEntity<JsonNode> resEmpty = restTemplate.postForEntity("/invoices", new HttpEntity<>("{}", jsonHeaders()), JsonNode.class);
        assertThat(resEmpty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Empty transaction_ids list
        ResponseEntity<JsonNode> resEmptyList = restTemplate.postForEntity("/invoices", new HttpEntity<>("{\"transaction_ids\": []}", jsonHeaders()), JsonNode.class);
        assertThat(resEmptyList.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Non-existent transaction ID -> 404
        ResponseEntity<JsonNode> resNotFound = restTemplate.postForEntity("/invoices", new HttpEntity<>("{\"transaction_ids\": [\"txn_nonexistent\"]}", jsonHeaders()), JsonNode.class);
        assertThat(resNotFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Duplicate transaction ID in list -> 409
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();
        ResponseEntity<JsonNode> resDup = restTemplate.postForEntity(
                "/invoices",
                new HttpEntity<>(new CreateInvoiceRequest(null, List.of(flightId, flightId)), jsonHeaders()),
                JsonNode.class);
        assertThat(resDup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("22. GET /invoices/{id} not found returns 404")
    void test22_getInvoice_notFound_returns404() {
        ResponseEntity<JsonNode> res = restTemplate.getForEntity("/invoices/inv_nonexistent", JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("23. GET /invoices pagination success and error validation")
    void test23_listInvoices_pagination_successAndFailure() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String hotelId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("hotel"), jsonHeaders()), JsonNode.class)).get("id").asText();

        restTemplate.postForEntity("/invoices", new HttpEntity<>(new CreateInvoiceRequest(flightId, null), jsonHeaders()), JsonNode.class);
        restTemplate.postForEntity("/invoices", new HttpEntity<>(new CreateInvoiceRequest(hotelId, null), jsonHeaders()), JsonNode.class);

        // Success list
        ResponseEntity<JsonNode> listRes = restTemplate.getForEntity("/invoices", JsonNode.class);
        assertThat(listRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(listRes).size()).isEqualTo(2);

        // Limit and offset
        ResponseEntity<JsonNode> pageRes = restTemplate.getForEntity("/invoices?limit=1&offset=1", JsonNode.class);
        assertThat(pageRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(pageRes).size()).isEqualTo(1);

        // limit > 100 -> 400
        assertThat(restTemplate.getForEntity("/invoices?limit=101", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // limit < 1 -> 400
        assertThat(restTemplate.getForEntity("/invoices?limit=0", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // offset < 0 -> 400
        assertThat(restTemplate.getForEntity("/invoices?offset=-1", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("24. PATCH /invoices/{id} updates snapshot lines, recalculates totals, and validates errors")
    void test24_patchInvoice_successAndFailures() throws Exception {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String hotelId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("hotel"), jsonHeaders()), JsonNode.class)).get("id").asText();
        String railId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("rail"), jsonHeaders()), JsonNode.class)).get("id").asText();

        // USD transaction for mixed currency test
        String usdPayload = objectMapper.writeValueAsString(seeds.get("trip_fee")).replace("\"EUR\"", "\"USD\"").replace("fee-trip-001", "fee-trip-usd");
        String usdId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(usdPayload, jsonHeaders()), JsonNode.class)).get("id").asText();

        // Create invoice with [flightId]
        ResponseEntity<JsonNode> invRes = restTemplate.postForEntity("/invoices", new HttpEntity<>(new CreateInvoiceRequest(flightId, null), jsonHeaders()), JsonNode.class);
        String invId = body(invRes).get("id").asText();
        assertThat(body(invRes).get("totals").get("grand_total").asDouble()).isEqualTo(220.00);

        // PATCH invoice to [hotelId, railId]
        CreateInvoiceRequest patchReq = new CreateInvoiceRequest(null, List.of(hotelId, railId));
        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/invoices/" + invId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchReq, jsonHeaders()),
                JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        // Hotel 331.70 + Rail 94.00 = 425.70
        assertThat(body(patchRes).get("totals").get("grand_total").asDouble()).isEqualTo(425.70);
        assertThat(body(patchRes).get("transactions").size()).isEqualTo(2);

        // Failure: invoice not found -> 404
        assertThat(restTemplate.exchange("/invoices/inv_nonexistent", HttpMethod.PATCH, new HttpEntity<>(patchReq, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Failure: transaction in list not found -> 404
        CreateInvoiceRequest notFoundTxReq = new CreateInvoiceRequest(null, List.of(flightId, "txn_nonexistent"));
        assertThat(restTemplate.exchange("/invoices/" + invId, HttpMethod.PATCH, new HttpEntity<>(notFoundTxReq, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Failure: empty transaction list -> 400
        CreateInvoiceRequest emptyReq = new CreateInvoiceRequest(null, List.of());
        assertThat(restTemplate.exchange("/invoices/" + invId, HttpMethod.PATCH, new HttpEntity<>(emptyReq, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Failure: duplicate transaction IDs -> 409
        CreateInvoiceRequest dupReq = new CreateInvoiceRequest(null, List.of(flightId, flightId));
        assertThat(restTemplate.exchange("/invoices/" + invId, HttpMethod.PATCH, new HttpEntity<>(dupReq, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // Failure: mixed currency -> 409
        CreateInvoiceRequest mixedReq = new CreateInvoiceRequest(null, List.of(flightId, usdId));
        assertThat(restTemplate.exchange("/invoices/" + invId, HttpMethod.PATCH, new HttpEntity<>(mixedReq, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("25. DELETE non-existent invoice returns 404")
    void test25_deleteInvoice_notFound_returns404() {
        ResponseEntity<Void> res = restTemplate.exchange("/invoices/inv_nonexistent", HttpMethod.DELETE, null, Void.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("26. POST /invoices with single transaction_id field succeeds")
    void test26_postInvoice_withSingleTransactionId() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();
        CreateInvoiceRequest singleReq = new CreateInvoiceRequest(flightId, null);
        ResponseEntity<JsonNode> res = restTemplate.postForEntity("/invoices", new HttpEntity<>(singleReq, jsonHeaders()), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(res).get("transactions").size()).isEqualTo(1);
        assertThat(body(res).get("totals").get("grand_total").asDouble()).isEqualTo(220.00);
    }

    @Test
    @DisplayName("27. PATCH metadata in real database updates in-place without Hibernate session conflict")
    void test27_patchMetadata_inRealDatabase_succeeds() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        ObjectNode newFlightMeta = objectMapper.createObjectNode();
        newFlightMeta.put("origin", "BER").put("destination", "JFK").put("fare", 450.00);
        ObjectNode coupon = newFlightMeta.putArray("coupons").addObject();
        coupon.put("carrier", "DL").put("flight_number", "DL100")
                .put("from", "BER").put("to", "JFK")
                .put("departure", "2026-03-15T10:00:00Z").put("arrival", "2026-03-15T18:00:00Z");

        ObjectNode patchReq = objectMapper.createObjectNode();
        patchReq.set("metadata", newFlightMeta);

        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/transactions/" + flightId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchReq, jsonHeaders()),
                JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(patchRes).get("metadata").get("origin").asText()).isEqualTo("BER");
        assertThat(body(patchRes).get("metadata").get("destination").asText()).isEqualTo("JFK");
        assertThat(body(patchRes).get("metadata").get("fare").asDouble()).isEqualTo(450.00);
        assertThat(body(patchRes).get("metadata").get("coupons").get(0).get("flight_number").asText()).isEqualTo("DL100");

        // Verify GET returns updated metadata
        ResponseEntity<JsonNode> getRes = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(getRes).get("metadata").get("origin").asText()).isEqualTo("BER");
    }

    @Test
    @DisplayName("28. PATCH with only tax_lines preserves existing fee_lines")
    void test28_patchTaxLinesOnly_preservesFeeLines() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        // Flight originally has fee line: YQ 25.00
        ObjectNode patchReq = objectMapper.createObjectNode();
        ObjectNode newTax = patchReq.putArray("tax_lines").addObject();
        newTax.put("name", "New Airport Tax").put("amount", 20.00);

        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/transactions/" + flightId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchReq, jsonHeaders()),
                JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(patchRes).get("tax_lines").size()).isEqualTo(1);
        assertThat(body(patchRes).get("tax_lines").get(0).get("name").asText()).isEqualTo("New Airport Tax");
        // Fee lines must be preserved!
        assertThat(body(patchRes).get("fee_lines").size()).isEqualTo(1);
        assertThat(body(patchRes).get("fee_lines").get(0).get("name").asText()).isEqualTo("YQ");
        assertThat(body(patchRes).get("fee_lines").get(0).get("amount").asDouble()).isEqualTo(25.00);
    }

    @Test
    @DisplayName("29. PATCH changing type (e.g. flight to hotel) returns 400 Bad Request")
    void test29_patchTypeMismatch_returns400() {
        String flightId = body(restTemplate.postForEntity("/transactions", new HttpEntity<>(seeds.get("flight"), jsonHeaders()), JsonNode.class)).get("id").asText();

        ObjectNode patchReq = objectMapper.createObjectNode();
        patchReq.put("type", "hotel");
        patchReq.set("metadata", seeds.get("hotel").get("metadata"));

        ResponseEntity<JsonNode> patchRes = restTemplate.exchange(
                "/transactions/" + flightId,
                HttpMethod.PATCH,
                new HttpEntity<>(patchReq, jsonHeaders()),
                JsonNode.class);
        assertThat(patchRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Verify transaction is still a flight
        ResponseEntity<JsonNode> getRes = restTemplate.getForEntity("/transactions/" + flightId, JsonNode.class);
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(getRes).get("type").asText()).isEqualTo("flight");
    }

    @Test
    @DisplayName("30. Bad inputs return 400 Bad Request (not 500)")
    void test30_badInputValidation_returns400() {
        // 1. Tax line with no amount
        ObjectNode badTax = (ObjectNode) seeds.get("flight").deepCopy();
        badTax.put("external_id", "bad-tax-1");
        ((ObjectNode) badTax.get("tax_lines").get(0)).remove("amount");
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(badTax, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 2. Fee line with no name
        ObjectNode badFee = (ObjectNode) seeds.get("flight").deepCopy();
        badFee.put("external_id", "bad-fee-1");
        ((ObjectNode) badFee.get("fee_lines").get(0)).remove("name");
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(badFee, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 3. Hotel extra with no amount
        ObjectNode badExtra = (ObjectNode) seeds.get("hotel").deepCopy();
        badExtra.put("external_id", "bad-extra-1");
        ((ObjectNode) badExtra.get("metadata").get("extras").get(0)).remove("amount");
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(badExtra, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 4. tax_lines: [null]
        ObjectNode nullTax = (ObjectNode) seeds.get("flight").deepCopy();
        nullTax.put("external_id", "null-tax-1");
        nullTax.putArray("tax_lines").addNull();
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(nullTax, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 5. ?limit=abc
        assertThat(restTemplate.getForEntity("/transactions?limit=abc", JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 6. transaction_ids: [null]
        ObjectNode nullTxInvoice = objectMapper.createObjectNode();
        nullTxInvoice.putArray("transaction_ids").addNull();
        assertThat(restTemplate.postForEntity("/invoices", new HttpEntity<>(nullTxInvoice, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 7. currency: "EURO"
        ObjectNode badCurr = (ObjectNode) seeds.get("flight").deepCopy();
        badCurr.put("external_id", "bad-curr-1");
        badCurr.put("currency", "EURO");
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(badCurr, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 8. negative total
        ObjectNode negTotal = (ObjectNode) seeds.get("flight").deepCopy();
        negTotal.put("external_id", "neg-total-1");
        negTotal.put("total", -10.00);
        assertThat(restTemplate.postForEntity("/transactions", new HttpEntity<>(negTotal, jsonHeaders()), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

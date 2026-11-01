package com.navan.travel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.dto.*;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.ConflictException;
import com.navan.travel.exception.NotFoundException;
import com.navan.travel.model.TransactionType;
import com.navan.travel.model.entity.InvoiceEntity;
import com.navan.travel.model.entity.InvoiceLineEntity;
import com.navan.travel.model.entity.TransactionEntity;
import com.navan.travel.repository.InvoiceRepository;
import com.navan.travel.repository.OffsetLimitRequest;
import com.navan.travel.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          TransactionRepository transactionRepository,
                          TransactionService transactionService,
                          ObjectMapper objectMapper) {
        this.invoiceRepository = invoiceRepository;
        this.transactionRepository = transactionRepository;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
        List<String> txIds = request.resolvedTransactionIds();
        if (txIds.isEmpty()) {
            throw new BadRequestException("At least one transaction_id must be provided");
        }

        // Deduplication / duplicate id check: 409 on duplicate transaction id listed on same invoice
        Set<String> uniqueIds = new HashSet<>();
        for (String id : txIds) {
            if (!uniqueIds.add(id)) {
                throw new ConflictException("Duplicate transaction_id listed on invoice: " + id);
            }
        }

        List<TransactionEntity> transactions = loadTransactions(txIds);
        validateCurrencies(transactions);

        String currency = transactions.get(0).getCurrency();
        String invoiceId = "inv_" + UUID.randomUUID().toString().replace("-", "");

        InvoiceEntity invoice = new InvoiceEntity();
        invoice.setId(invoiceId);
        invoice.setCurrency(currency);

        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (TransactionEntity tx : transactions) {
            grandTotal = grandTotal.add(tx.getTotal());
            InvoiceLineEntity line = createSnapshotLine(invoice, tx);
            invoice.getLines().add(line);
        }
        invoice.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP));

        InvoiceEntity saved = invoiceRepository.saveAndFlush(invoice);
        return toInvoiceResponse(saved);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(String id) {
        InvoiceEntity invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));
        return toInvoiceResponse(invoice);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new BadRequestException("offset cannot be negative");
        }

        Page<InvoiceEntity> page = invoiceRepository.findAll(new OffsetLimitRequest(limit, offset));
        return page.getContent().stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    @Transactional
    public InvoiceResponse updateInvoice(String id, CreateInvoiceRequest request) {
        InvoiceEntity invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));

        List<String> txIds = request.resolvedTransactionIds();
        if (txIds.isEmpty()) {
            throw new BadRequestException("At least one transaction_id must be provided");
        }

        Set<String> uniqueIds = new HashSet<>();
        for (String txId : txIds) {
            if (!uniqueIds.add(txId)) {
                throw new ConflictException("Duplicate transaction_id listed on invoice: " + txId);
            }
        }

        List<TransactionEntity> currentTransactions = loadTransactions(txIds);
        validateCurrencies(currentTransactions);

        String currency = currentTransactions.get(0).getCurrency();
        invoice.setCurrency(currency);

        invoice.getLines().clear();
        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (TransactionEntity tx : currentTransactions) {
            grandTotal = grandTotal.add(tx.getTotal());
            InvoiceLineEntity line = createSnapshotLine(invoice, tx);
            invoice.getLines().add(line);
        }
        invoice.setGrandTotal(grandTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setUpdatedAt(Instant.now());

        InvoiceEntity saved = invoiceRepository.saveAndFlush(invoice);
        return toInvoiceResponse(saved);
    }

    @Transactional
    public void deleteInvoice(String id) {
        InvoiceEntity invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice not found: " + id));

        // Delete invoice and snapshot lines (transactions are left intact)
        invoiceRepository.delete(invoice);
    }

    private List<TransactionEntity> loadTransactions(List<String> ids) {
        List<TransactionEntity> found = transactionRepository.findAllById(ids);
        Map<String, TransactionEntity> byId = new HashMap<>();
        for (TransactionEntity tx : found) {
            byId.put(tx.getId(), tx);
        }
        List<TransactionEntity> result = new ArrayList<>();
        for (String id : ids) {
            TransactionEntity tx = byId.get(id);
            if (tx == null) {
                throw new NotFoundException("Transaction not found: " + id);
            }
            result.add(tx);
        }
        return result;
    }

    private void validateCurrencies(List<TransactionEntity> transactions) {
        if (transactions.isEmpty()) return;
        String firstCurrency = transactions.get(0).getCurrency();
        for (TransactionEntity tx : transactions) {
            if (!firstCurrency.equalsIgnoreCase(tx.getCurrency())) {
                throw new ConflictException("Mixed currencies not allowed on invoice: found "
                        + firstCurrency + " and " + tx.getCurrency());
            }
        }
    }

    private InvoiceLineEntity createSnapshotLine(InvoiceEntity invoice, TransactionEntity tx) {
        InvoiceLineEntity line = new InvoiceLineEntity();
        line.setInvoice(invoice);
        line.setTransactionId(tx.getId());
        line.setExternalId(tx.getExternalId());
        line.setTransactionType(tx.getType().getValue());
        line.setOccurredAt(tx.getOccurredAt().toString());
        line.setTotal(tx.getTotal().setScale(2, RoundingMode.HALF_UP));

        TransactionResponse txResponse = transactionService.toResponse(tx);

        try {
            line.setTaxLinesJson(objectMapper.writeValueAsString(txResponse.taxLines()));
            line.setFeeLinesJson(objectMapper.writeValueAsString(txResponse.feeLines()));
            line.setMetadataJson(objectMapper.writeValueAsString(txResponse.metadata()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize transaction snapshot: " + e.getMessage(), e);
        }

        return line;
    }

    public InvoiceResponse toInvoiceResponse(InvoiceEntity invoice) {
        List<TransactionResponse> txSnapshots = new ArrayList<>();
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<TaxGroupKey, BigDecimal> taxRollupMap = new LinkedHashMap<>();
        Map<String, BigDecimal> feeRollupMap = new LinkedHashMap<>();

        BigDecimal grandTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (InvoiceLineEntity line : invoice.getLines()) {
            grandTotal = grandTotal.add(line.getTotal());

            List<TaxLineDto> taxLines;
            List<FeeLineDto> feeLines;
            Object metadata;
            try {
                taxLines = objectMapper.readValue(line.getTaxLinesJson(), new TypeReference<List<TaxLineDto>>() {});
                feeLines = objectMapper.readValue(line.getFeeLinesJson(), new TypeReference<List<FeeLineDto>>() {});
                metadata = objectMapper.readValue(line.getMetadataJson(), Object.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize transaction snapshot: " + e.getMessage(), e);
            }

            // Category breakdown for totals
            String category = determineCategory(line.getTransactionType(), metadata);
            totals.put(category, totals.getOrDefault(category, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).add(line.getTotal()));

            // Tax rollup aggregation
            for (TaxLineDto tax : taxLines) {
                TaxGroupKey key = new TaxGroupKey(tax.name(), tax.rate());
                taxRollupMap.put(key, taxRollupMap.getOrDefault(key, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).add(tax.amount()));
            }

            // Fee rollup aggregation
            for (FeeLineDto fee : feeLines) {
                feeRollupMap.put(fee.name(), feeRollupMap.getOrDefault(fee.name(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)).add(fee.amount()));
            }

            txSnapshots.add(new TransactionResponse(
                    line.getTransactionId(),
                    TransactionType.fromString(line.getTransactionType()),
                    line.getExternalId(),
                    invoice.getCurrency(),
                    line.getOccurredAt(),
                    line.getTotal(),
                    taxLines,
                    feeLines,
                    metadata
            ));
        }

        totals.put("grand_total", grandTotal.setScale(2, RoundingMode.HALF_UP));

        List<TaxRollupDto> taxRollup = taxRollupMap.entrySet().stream()
                .map(entry -> new TaxRollupDto(entry.getKey().name(), entry.getKey().rate(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();

        List<FeeRollupDto> feeRollup = feeRollupMap.entrySet().stream()
                .map(entry -> new FeeRollupDto(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();

        return new InvoiceResponse(
                invoice.getId(),
                invoice.getCurrency(),
                totals,
                taxRollup,
                feeRollup,
                txSnapshots,
                invoice.getCreatedAt().toString(),
                invoice.getUpdatedAt().toString()
        );
    }

    private String determineCategory(String type, Object metadata) {
        if ("navan_fee".equals(type) && metadata instanceof Map<?, ?> metaMap) {
            Object feeKind = metaMap.get("fee_kind");
            if (feeKind != null) {
                return feeKind.toString();
            }
        }
        return type;
    }

    private record TaxGroupKey(String name, BigDecimal rate) {}
}

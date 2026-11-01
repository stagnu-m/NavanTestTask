package com.navan.travel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.dto.*;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.ConflictException;
import com.navan.travel.exception.NotFoundException;
import com.navan.travel.model.TransactionType;
import com.navan.travel.model.entity.*;
import com.navan.travel.repository.InvoiceLineRepository;
import com.navan.travel.repository.OffsetLimitRequest;
import com.navan.travel.repository.TransactionRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private static final Set<String> ALLOWED_FEE_KINDS = Set.of("trip_fee", "agent_call_fee");

    private final TransactionRepository transactionRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Validator validator;

    public TransactionService(TransactionRepository transactionRepository,
                              InvoiceLineRepository invoiceLineRepository,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this(transactionRepository, invoiceLineRepository, objectMapper, transactionManager,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                              InvoiceLineRepository invoiceLineRepository,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager,
                              Validator validator) {
        this.transactionRepository = transactionRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.validator = validator != null ? validator : Validation.buildDefaultValidatorFactory().getValidator();
    }

    public TransactionResponse createTransaction(TransactionRequest request) {
        if (request.type() == null || request.type() == TransactionType.UNSUPPORTED) {
            throw new BadRequestException("Unknown transaction type: " + (request.type() != null ? request.type().getValue() : "null"));
        }

        Currency currency = validateCurrency(request.currency());
        BigDecimal scaledTotal = validateMoney(request.total(), "total", currency);

        // Fast-path: check if external_id already exists before starting transaction
        Optional<TransactionEntity> existingOpt = transactionRepository.findByExternalId(request.externalId());
        if (existingOpt.isPresent()) {
            throw new ConflictException("Transaction with external_id already exists", existingOpt.get().getId());
        }

        validateMetadataNode(request.type(), request.metadata(), currency);

        try {
            return transactionTemplate.execute(status -> {
                Optional<TransactionEntity> inTxOpt = transactionRepository.findByExternalId(request.externalId());
                if (inTxOpt.isPresent()) {
                    throw new ConflictException("Transaction with external_id already exists", inTxOpt.get().getId());
                }

                String id = "txn_" + UUID.randomUUID().toString().replace("-", "");
                TransactionEntity entity = new TransactionEntity();
                entity.setId(id);
                entity.setType(request.type());
                entity.setExternalId(request.externalId());
                entity.setCurrency(currency.getCurrencyCode());
                entity.setOccurredAt(parseInstant(request.occurredAt()));
                entity.setTotal(scaledTotal);

                populateTaxAndFeeLines(entity, request.taxLines(), request.feeLines(), currency);
                populateMetadataEntity(entity, request.type(), request.metadata(), currency);

                TransactionEntity saved = transactionRepository.saveAndFlush(entity);
                return toResponse(saved);
            });
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            // Concurrent duplicate create race: retry lookup to return winner ID
            for (int i = 0; i < 10; i++) {
                Optional<TransactionEntity> raceExisting = transactionRepository.findByExternalId(request.externalId());
                if (raceExisting.isPresent()) {
                    throw new ConflictException("Transaction with external_id already exists", raceExisting.get().getId());
                }
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            throw new ConflictException("Transaction with external_id already exists");
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(int limit, int offset) {
        if (limit < 1 || limit > 100) {
            throw new BadRequestException("limit must be between 1 and 100");
        }
        if (offset < 0) {
            throw new BadRequestException("offset cannot be negative");
        }
        Page<TransactionEntity> page = transactionRepository.findAll(new OffsetLimitRequest(limit, offset));
        return page.getContent().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TransactionResponse updateTransaction(String id, TransactionRequest request) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));

        // Invoiced freeze rule: cannot modify transaction if on any invoice
        if (invoiceLineRepository.existsByTransactionId(id)) {
            throw new ConflictException("Transaction is included on an invoice and cannot be updated");
        }

        if (request.type() != null) {
            if (request.type() == TransactionType.UNSUPPORTED) {
                throw new BadRequestException("Unknown transaction type: " + request.type().getValue());
            }
            if (request.type() != entity.getType()) {
                throw new BadRequestException("Changing transaction type is not supported");
            }
        }

        if (request.externalId() != null && !request.externalId().equals(entity.getExternalId())) {
            throw new BadRequestException("Changing external_id is not supported");
        }

        if (request.currency() != null && !request.currency().equalsIgnoreCase(entity.getCurrency())) {
            throw new BadRequestException("Changing currency is not supported");
        }

        Currency currency = Currency.getInstance(entity.getCurrency());

        if (request.occurredAt() != null) {
            entity.setOccurredAt(parseInstant(request.occurredAt()));
        }
        if (request.total() != null) {
            entity.setTotal(validateMoney(request.total(), "total", currency));
        }

        if (request.taxLines() != null || request.feeLines() != null) {
            populateTaxAndFeeLines(entity,
                    request.taxLines() != null ? request.taxLines() : Collections.emptyList(),
                    request.feeLines() != null ? request.feeLines() : Collections.emptyList(),
                    currency);
        }

        if (request.metadata() != null) {
            validateMetadataNode(entity.getType(), request.metadata(), currency);
            populateMetadataEntity(entity, entity.getType(), request.metadata(), currency);
        }

        TransactionEntity saved = transactionRepository.saveAndFlush(entity);
        return toResponse(saved);
    }

    @Transactional
    public void deleteTransaction(String id) {
        TransactionEntity entity = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));

        // Invoiced freeze rule: cannot delete transaction if on any invoice
        if (invoiceLineRepository.existsByTransactionId(id)) {
            throw new ConflictException("Transaction is included on an invoice and cannot be deleted");
        }

        transactionRepository.delete(entity);
    }

    public void validateMetadataNode(TransactionType type, JsonNode metadata) {
        validateMetadataNode(type, metadata, Currency.getInstance("EUR"));
    }

    public void validateMetadataNode(TransactionType type, JsonNode metadata, Currency currency) {
        if (type == null || type == TransactionType.UNSUPPORTED) {
            throw new BadRequestException("Unknown transaction type: " + (type != null ? type.getValue() : "null"));
        }
        if (metadata == null || metadata.isNull() || !metadata.isObject() || metadata.isEmpty()) {
            throw new BadRequestException("Metadata must be a non-empty object for transaction type: " + type.getValue());
        }

        try {
            switch (type) {
                case FLIGHT -> {
                    FlightMetadataDto dto = objectMapper.treeToValue(metadata, FlightMetadataDto.class);
                    validateWithValidator(dto, "flight");
                    if (dto.coupons() == null || dto.coupons().isEmpty()) {
                        throw new BadRequestException("Invalid flight metadata: coupons are required");
                    }
                    validateMoney(dto.fare(), "flight fare", currency);
                    for (FlightCouponDto coupon : dto.coupons()) {
                        if (coupon == null || coupon.flightNumber() == null || coupon.flightNumber().isBlank() ||
                            coupon.from() == null || coupon.to() == null ||
                            coupon.departure() == null || coupon.arrival() == null) {
                            throw new BadRequestException("Flight coupon must have flight_number, from, to, departure, arrival");
                        }
                    }
                }
                case HOTEL -> {
                    HotelMetadataDto dto = objectMapper.treeToValue(metadata, HotelMetadataDto.class);
                    validateWithValidator(dto, "hotel");
                    if (dto.nights() == null || dto.nights() <= 0) {
                        throw new BadRequestException("Hotel nights must be greater than zero");
                    }
                    validateMoney(dto.roomRatePerNight(), "hotel room_rate_per_night", currency);
                    if (dto.extras() != null) {
                        for (HotelExtraDto extra : dto.extras()) {
                            if (extra == null || extra.name() == null || extra.name().isBlank()) {
                                throw new BadRequestException("Hotel extra name cannot be blank");
                            }
                            validateMoney(extra.amount(), "hotel extra amount", currency);
                        }
                    }
                }
                case RAIL -> {
                    RailMetadataDto dto = objectMapper.treeToValue(metadata, RailMetadataDto.class);
                    validateWithValidator(dto, "rail");
                    if (dto.legs() == null || dto.legs().isEmpty()) {
                        throw new BadRequestException("Rail metadata must include at least one leg");
                    }
                    for (RailLegDto leg : dto.legs()) {
                        if (leg == null || leg.origin() == null || leg.destination() == null ||
                            leg.departure() == null || leg.arrival() == null) {
                            throw new BadRequestException("Rail leg must include origin, destination, departure, arrival");
                        }
                    }
                }
                case NAVAN_FEE -> {
                    NavanFeeMetadataDto dto = objectMapper.treeToValue(metadata, NavanFeeMetadataDto.class);
                    if (dto.feeKind() == null || !ALLOWED_FEE_KINDS.contains(dto.feeKind())) {
                        throw new BadRequestException("Unknown navan_fee fee_kind: " + (dto.feeKind() != null ? dto.feeKind() : "null"));
                    }
                    validateWithValidator(dto, "navan_fee");
                }
                default -> throw new BadRequestException("Unknown transaction type: " + type.getValue());
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Malformed metadata JSON: " + e.getMessage());
        }
    }

    private void validateWithValidator(Object target, String context) {
        Set<ConstraintViolation<Object>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            String msg = violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining("; "));
            throw new BadRequestException("Invalid " + context + " metadata: " + msg);
        }
    }

    private Currency validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("currency cannot be blank");
        }
        try {
            return Currency.getInstance(currency.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid ISO-4217 currency code: " + currency);
        }
    }

    private BigDecimal validateMoney(BigDecimal amount, String fieldName, Currency currency) {
        if (amount == null) {
            throw new BadRequestException(fieldName + " cannot be null");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException(fieldName + " cannot be negative");
        }
        int maxScale = Math.max(0, currency.getDefaultFractionDigits());
        int effectiveScale = amount.compareTo(BigDecimal.ZERO) == 0 ? 0 : amount.stripTrailingZeros().scale();
        if (effectiveScale > maxScale) {
            throw new BadRequestException(fieldName + " scale exceeds maximum " + maxScale + " decimal places for currency " + currency.getCurrencyCode());
        }
        return amount.setScale(maxScale, RoundingMode.HALF_UP);
    }

    private void populateTaxAndFeeLines(TransactionEntity entity, List<TaxLineDto> taxLines, List<FeeLineDto> feeLines, Currency currency) {
        entity.getTaxLines().clear();
        if (taxLines != null) {
            for (TaxLineDto taxDto : taxLines) {
                if (taxDto == null || taxDto.name() == null || taxDto.name().isBlank()) {
                    throw new BadRequestException("Tax name cannot be blank");
                }
                BigDecimal amount = validateMoney(taxDto.amount(), "Tax amount", currency);
                TransactionTaxLineEntity taxEntity = new TransactionTaxLineEntity();
                taxEntity.setTransaction(entity);
                taxEntity.setName(taxDto.name());
                taxEntity.setRate(taxDto.rate());
                taxEntity.setAmount(amount);
                entity.getTaxLines().add(taxEntity);
            }
        }

        entity.getFeeLines().clear();
        if (feeLines != null) {
            for (FeeLineDto feeDto : feeLines) {
                if (feeDto == null || feeDto.name() == null || feeDto.name().isBlank()) {
                    throw new BadRequestException("Fee name cannot be blank");
                }
                BigDecimal amount = validateMoney(feeDto.amount(), "Fee amount", currency);
                TransactionFeeLineEntity feeEntity = new TransactionFeeLineEntity();
                feeEntity.setTransaction(entity);
                feeEntity.setName(feeDto.name());
                feeEntity.setAmount(amount);
                entity.getFeeLines().add(feeEntity);
            }
        }
    }

    private void populateMetadataEntity(TransactionEntity entity, TransactionType type, JsonNode metadataNode, Currency currency) {
        try {
            switch (type) {
                case FLIGHT -> {
                    FlightMetadataDto dto = objectMapper.treeToValue(metadataNode, FlightMetadataDto.class);
                    FlightMetadataEntity flightEntity = new FlightMetadataEntity();
                    flightEntity.setTransaction(entity);
                    flightEntity.setOrigin(dto.origin());
                    flightEntity.setDestination(dto.destination());
                    flightEntity.setFare(validateMoney(dto.fare(), "flight fare", currency));

                    int order = 0;
                    for (FlightCouponDto c : dto.coupons()) {
                        FlightCouponEntity couponEntity = new FlightCouponEntity();
                        couponEntity.setFlightMetadata(flightEntity);
                        couponEntity.setCouponOrder(order++);
                        couponEntity.setCarrier(c.carrier());
                        couponEntity.setFlightNumber(c.flightNumber());
                        couponEntity.setFromLocation(c.from());
                        couponEntity.setToLocation(c.to());
                        couponEntity.setDeparture(c.departure());
                        couponEntity.setArrival(c.arrival());
                        flightEntity.getCoupons().add(couponEntity);
                    }
                    entity.setFlightMetadata(flightEntity);
                }
                case HOTEL -> {
                    HotelMetadataDto dto = objectMapper.treeToValue(metadataNode, HotelMetadataDto.class);
                    HotelMetadataEntity hotelEntity = new HotelMetadataEntity();
                    hotelEntity.setTransaction(entity);
                    hotelEntity.setName(dto.name());
                    hotelEntity.setStreet(dto.address().street());
                    hotelEntity.setCity(dto.address().city());
                    hotelEntity.setCountry(dto.address().country());
                    hotelEntity.setCheckIn(dto.checkIn());
                    hotelEntity.setCheckOut(dto.checkOut());
                    hotelEntity.setNights(dto.nights());
                    hotelEntity.setRoomRatePerNight(validateMoney(dto.roomRatePerNight(), "hotel room_rate_per_night", currency));

                    if (dto.extras() != null) {
                        for (HotelExtraDto extraDto : dto.extras()) {
                            HotelExtraEntity extraEntity = new HotelExtraEntity();
                            extraEntity.setHotelMetadata(hotelEntity);
                            extraEntity.setName(extraDto.name());
                            extraEntity.setAmount(validateMoney(extraDto.amount(), "hotel extra amount", currency));
                            hotelEntity.getExtras().add(extraEntity);
                        }
                    }
                    entity.setHotelMetadata(hotelEntity);
                }
                case RAIL -> {
                    RailMetadataDto dto = objectMapper.treeToValue(metadataNode, RailMetadataDto.class);
                    RailMetadataEntity railEntity = new RailMetadataEntity();
                    railEntity.setTransaction(entity);

                    int order = 0;
                    for (RailLegDto legDto : dto.legs()) {
                        RailLegEntity legEntity = new RailLegEntity();
                        legEntity.setRailMetadata(railEntity);
                        legEntity.setLegOrder(order++);
                        legEntity.setOrigin(legDto.origin());
                        legEntity.setDestination(legDto.destination());
                        legEntity.setDeparture(legDto.departure());
                        legEntity.setArrival(legDto.arrival());
                        legEntity.setTrainNumber(legDto.trainNumber());
                        legEntity.setTravelClass(legDto.travelClass());
                        railEntity.getLegs().add(legEntity);
                    }
                    entity.setRailMetadata(railEntity);
                }
                case NAVAN_FEE -> {
                    NavanFeeMetadataDto dto = objectMapper.treeToValue(metadataNode, NavanFeeMetadataDto.class);
                    NavanFeeMetadataEntity feeEntity = new NavanFeeMetadataEntity();
                    feeEntity.setTransaction(entity);
                    feeEntity.setFeeKind(dto.feeKind());
                    feeEntity.setDescription(dto.description());
                    entity.setNavanFeeMetadata(feeEntity);
                }
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Malformed metadata JSON: " + e.getMessage());
        }
    }

    private Instant parseInstant(String text) {
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid ISO-8601 date-time format: " + text);
        }
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        List<TaxLineDto> taxDtos = entity.getTaxLines().stream()
                .map(t -> new TaxLineDto(t.getName(), t.getRate(), t.getAmount()))
                .toList();

        List<FeeLineDto> feeDtos = entity.getFeeLines().stream()
                .map(f -> new FeeLineDto(f.getName(), f.getAmount()))
                .toList();

        Object metadataObj = buildMetadataDto(entity);

        return new TransactionResponse(
                entity.getId(),
                entity.getType(),
                entity.getExternalId(),
                entity.getCurrency(),
                entity.getOccurredAt().toString(),
                entity.getTotal(),
                taxDtos,
                feeDtos,
                metadataObj
        );
    }

    public Object buildMetadataDto(TransactionEntity entity) {
        return switch (entity.getType()) {
            case FLIGHT -> {
                FlightMetadataEntity flight = entity.getFlightMetadata();
                if (flight == null) yield Collections.emptyMap();
                List<FlightCouponDto> coupons = flight.getCoupons().stream()
                        .map(c -> new FlightCouponDto(
                                c.getCarrier(),
                                c.getFlightNumber(),
                                c.getFromLocation(),
                                c.getToLocation(),
                                c.getDeparture(),
                                c.getArrival()))
                        .toList();
                yield new FlightMetadataDto(
                        flight.getOrigin(),
                        flight.getDestination(),
                        flight.getFare(),
                        coupons);
            }
            case HOTEL -> {
                HotelMetadataEntity hotel = entity.getHotelMetadata();
                if (hotel == null) yield Collections.emptyMap();
                List<HotelExtraDto> extras = hotel.getExtras().stream()
                        .map(e -> new HotelExtraDto(e.getName(), e.getAmount()))
                        .toList();
                yield new HotelMetadataDto(
                        hotel.getName(),
                        new AddressDto(hotel.getStreet(), hotel.getCity(), hotel.getCountry()),
                        hotel.getCheckIn(),
                        hotel.getCheckOut(),
                        hotel.getNights(),
                        hotel.getRoomRatePerNight(),
                        extras);
            }
            case RAIL -> {
                RailMetadataEntity rail = entity.getRailMetadata();
                if (rail == null) yield Collections.emptyMap();
                List<RailLegDto> legs = rail.getLegs().stream()
                        .map(l -> new RailLegDto(
                                l.getOrigin(),
                                l.getDestination(),
                                l.getDeparture(),
                                l.getArrival(),
                                l.getTrainNumber(),
                                l.getTravelClass()))
                        .toList();
                yield new RailMetadataDto(legs);
            }
            case NAVAN_FEE -> {
                NavanFeeMetadataEntity fee = entity.getNavanFeeMetadata();
                if (fee == null) yield Collections.emptyMap();
                yield new NavanFeeMetadataDto(
                        fee.getFeeKind(),
                        fee.getDescription(),
                        fee.getRelatedExternalId());
            }
            default -> Collections.emptyMap();
        };
    }
}

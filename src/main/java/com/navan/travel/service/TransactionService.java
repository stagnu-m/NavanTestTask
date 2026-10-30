package com.navan.travel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.travel.dto.*;
import com.navan.travel.exception.BadRequestException;
import com.navan.travel.exception.ConflictException;
import com.navan.travel.exception.NotFoundException;
import com.navan.travel.model.entity.*;
import com.navan.travel.repository.InvoiceLineRepository;
import com.navan.travel.repository.OffsetLimitRequest;
import com.navan.travel.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class TransactionService {

    private static final Set<String> ALLOWED_TYPES = Set.of("flight", "hotel", "rail", "navan_fee");
    private static final Set<String> ALLOWED_FEE_KINDS = Set.of("trip_fee", "agent_call_fee");

    private final TransactionRepository transactionRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              InvoiceLineRepository invoiceLineRepository,
                              ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        if (!ALLOWED_TYPES.contains(request.type())) {
            throw new BadRequestException("Unknown transaction type: " + request.type());
        }

        // Check if external_id already exists sequentially
        Optional<TransactionEntity> existingOpt = transactionRepository.findByExternalId(request.externalId());
        if (existingOpt.isPresent()) {
            throw new ConflictException("Transaction with external_id already exists", existingOpt.get().getId());
        }

        validateMetadataNode(request.type(), request.metadata());

        String id = "txn_" + UUID.randomUUID().toString().replace("-", "");
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        entity.setType(request.type());
        entity.setExternalId(request.externalId());
        entity.setCurrency(request.currency().toUpperCase());
        entity.setOccurredAt(parseInstant(request.occurredAt()));
        entity.setTotal(request.total().setScale(2, RoundingMode.HALF_UP));

        populateTaxAndFeeLines(entity, request.taxLines(), request.feeLines());
        populateMetadataEntity(entity, request.type(), request.metadata());

        try {
            TransactionEntity saved = transactionRepository.saveAndFlush(entity);
            return toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent race condition handling: re-query by external_id
            Optional<TransactionEntity> raceExisting = transactionRepository.findByExternalId(request.externalId());
            if (raceExisting.isPresent()) {
                throw new ConflictException("Transaction with external_id already exists", raceExisting.get().getId());
            }
            throw ex;
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

        if (request.type() != null && !ALLOWED_TYPES.contains(request.type())) {
            throw new BadRequestException("Unknown transaction type: " + request.type());
        }

        if (request.occurredAt() != null) {
            entity.setOccurredAt(parseInstant(request.occurredAt()));
        }
        if (request.total() != null) {
            entity.setTotal(request.total().setScale(2, RoundingMode.HALF_UP));
        }

        if (request.taxLines() != null || request.feeLines() != null) {
            populateTaxAndFeeLines(entity,
                    request.taxLines() != null ? request.taxLines() : Collections.emptyList(),
                    request.feeLines() != null ? request.feeLines() : Collections.emptyList());
        }

        if (request.metadata() != null) {
            String type = request.type() != null ? request.type() : entity.getType();
            validateMetadataNode(type, request.metadata());
            populateMetadataEntity(entity, type, request.metadata());
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

    public void validateMetadataNode(String type, JsonNode metadata) {
        if (metadata == null || metadata.isNull() || !metadata.isObject() || metadata.isEmpty()) {
            throw new BadRequestException("Metadata must be a non-empty object for transaction type: " + type);
        }

        try {
            switch (type) {
                case "flight" -> {
                    FlightMetadataDto dto = objectMapper.treeToValue(metadata, FlightMetadataDto.class);
                    if (dto.origin() == null || dto.origin().isBlank() ||
                        dto.destination() == null || dto.destination().isBlank() ||
                        dto.fare() == null || dto.coupons() == null) {
                        throw new BadRequestException("Invalid flight metadata: origin, destination, fare, and coupons are required");
                    }
                    for (FlightCouponDto coupon : dto.coupons()) {
                        if (coupon.flightNumber() == null || coupon.flightNumber().isBlank() ||
                            coupon.from() == null || coupon.to() == null ||
                            coupon.departure() == null || coupon.arrival() == null) {
                            throw new BadRequestException("Flight coupon must have flight_number, from, to, departure, arrival");
                        }
                    }
                }
                case "hotel" -> {
                    HotelMetadataDto dto = objectMapper.treeToValue(metadata, HotelMetadataDto.class);
                    if (dto.name() == null || dto.name().isBlank() ||
                        dto.address() == null || dto.checkIn() == null ||
                        dto.checkOut() == null || dto.nights() == null ||
                        dto.roomRatePerNight() == null) {
                        throw new BadRequestException("Invalid hotel metadata: name, address, check_in, check_out, nights, room_rate_per_night required");
                    }
                    if (dto.address().street() == null || dto.address().city() == null || dto.address().country() == null) {
                        throw new BadRequestException("Hotel address must include street, city, country");
                    }
                }
                case "rail" -> {
                    RailMetadataDto dto = objectMapper.treeToValue(metadata, RailMetadataDto.class);
                    if (dto.legs() == null || dto.legs().isEmpty()) {
                        throw new BadRequestException("Rail metadata must include at least one leg");
                    }
                    for (RailLegDto leg : dto.legs()) {
                        if (leg.origin() == null || leg.destination() == null ||
                            leg.departure() == null || leg.arrival() == null) {
                            throw new BadRequestException("Rail leg must include origin, destination, departure, arrival");
                        }
                    }
                }
                case "navan_fee" -> {
                    NavanFeeMetadataDto dto = objectMapper.treeToValue(metadata, NavanFeeMetadataDto.class);
                    if (dto.feeKind() == null || !ALLOWED_FEE_KINDS.contains(dto.feeKind())) {
                        throw new BadRequestException("Unknown navan_fee fee_kind: " + (dto.feeKind() != null ? dto.feeKind() : "null"));
                    }
                }
                default -> throw new BadRequestException("Unknown transaction type: " + type);
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Malformed metadata JSON: " + e.getMessage());
        }
    }

    private void populateTaxAndFeeLines(TransactionEntity entity, List<TaxLineDto> taxLines, List<FeeLineDto> feeLines) {
        entity.getTaxLines().clear();
        if (taxLines != null) {
            for (TaxLineDto taxDto : taxLines) {
                TransactionTaxLineEntity taxEntity = new TransactionTaxLineEntity();
                taxEntity.setTransaction(entity);
                taxEntity.setName(taxDto.name());
                taxEntity.setRate(taxDto.rate());
                taxEntity.setAmount(taxDto.amount().setScale(2, RoundingMode.HALF_UP));
                entity.getTaxLines().add(taxEntity);
            }
        }

        entity.getFeeLines().clear();
        if (feeLines != null) {
            for (FeeLineDto feeDto : feeLines) {
                TransactionFeeLineEntity feeEntity = new TransactionFeeLineEntity();
                feeEntity.setTransaction(entity);
                feeEntity.setName(feeDto.name());
                feeEntity.setAmount(feeDto.amount().setScale(2, RoundingMode.HALF_UP));
                entity.getFeeLines().add(feeEntity);
            }
        }
    }

    private void populateMetadataEntity(TransactionEntity entity, String type, JsonNode metadataNode) {
        try {
            switch (type) {
                case "flight" -> {
                    FlightMetadataDto dto = objectMapper.treeToValue(metadataNode, FlightMetadataDto.class);
                    FlightMetadataEntity flightEntity = new FlightMetadataEntity();
                    flightEntity.setTransaction(entity);
                    flightEntity.setOrigin(dto.origin());
                    flightEntity.setDestination(dto.destination());
                    flightEntity.setFare(dto.fare().setScale(2, RoundingMode.HALF_UP));

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
                case "hotel" -> {
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
                    hotelEntity.setRoomRatePerNight(dto.roomRatePerNight().setScale(2, RoundingMode.HALF_UP));

                    if (dto.extras() != null) {
                        for (HotelExtraDto extraDto : dto.extras()) {
                            HotelExtraEntity extraEntity = new HotelExtraEntity();
                            extraEntity.setHotelMetadata(hotelEntity);
                            extraEntity.setName(extraDto.name());
                            extraEntity.setAmount(extraDto.amount().setScale(2, RoundingMode.HALF_UP));
                            hotelEntity.getExtras().add(extraEntity);
                        }
                    }
                    entity.setHotelMetadata(hotelEntity);
                }
                case "rail" -> {
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
                case "navan_fee" -> {
                    NavanFeeMetadataDto dto = objectMapper.treeToValue(metadataNode, NavanFeeMetadataDto.class);
                    NavanFeeMetadataEntity feeEntity = new NavanFeeMetadataEntity();
                    feeEntity.setTransaction(entity);
                    feeEntity.setFeeKind(dto.feeKind());
                    feeEntity.setDescription(dto.description());
                    feeEntity.setRelatedExternalId(dto.relatedExternalId());
                    entity.setNavanFeeMetadata(feeEntity);
                }
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Failed to parse metadata: " + e.getMessage());
        }
    }

    public TransactionResponse toResponse(TransactionEntity entity) {
        List<TaxLineDto> taxList = entity.getTaxLines().stream()
                .map(t -> new TaxLineDto(t.getName(), t.getRate(), t.getAmount()))
                .toList();

        List<FeeLineDto> feeList = entity.getFeeLines().stream()
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
                taxList,
                feeList,
                metadataObj
        );
    }

    public Object buildMetadataDto(TransactionEntity entity) {
        return switch (entity.getType()) {
            case "flight" -> {
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
                yield new FlightMetadataDto(flight.getOrigin(), flight.getDestination(), flight.getFare(), coupons);
            }
            case "hotel" -> {
                HotelMetadataEntity hotel = entity.getHotelMetadata();
                if (hotel == null) yield Collections.emptyMap();
                AddressDto address = new AddressDto(hotel.getStreet(), hotel.getCity(), hotel.getCountry());
                List<HotelExtraDto> extras = hotel.getExtras().stream()
                        .map(e -> new HotelExtraDto(e.getName(), e.getAmount()))
                        .toList();
                yield new HotelMetadataDto(
                        hotel.getName(),
                        address,
                        hotel.getCheckIn(),
                        hotel.getCheckOut(),
                        hotel.getNights(),
                        hotel.getRoomRatePerNight(),
                        extras
                );
            }
            case "rail" -> {
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
            case "navan_fee" -> {
                NavanFeeMetadataEntity fee = entity.getNavanFeeMetadata();
                if (fee == null) yield Collections.emptyMap();
                yield new NavanFeeMetadataDto(fee.getFeeKind(), fee.getDescription(), fee.getRelatedExternalId());
            }
            default -> Collections.emptyMap();
        };
    }

    private Instant parseInstant(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid ISO-8601 timestamp: " + dateStr);
        }
    }
}

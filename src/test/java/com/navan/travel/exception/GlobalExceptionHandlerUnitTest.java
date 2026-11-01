package com.navan.travel.exception;

import com.navan.travel.dto.ErrorDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleNotFound() {
        ResponseEntity<ErrorDto> response = handler.handleNotFound(new NotFoundException("Item missing"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Item missing");
    }

    @Test
    void testSpecificNotFoundExceptions() {
        TransactionNotFoundException txEx = new TransactionNotFoundException("txn_123");
        assertThat(txEx.getMessage()).isEqualTo("Transaction not found: txn_123");
        assertThat(txEx).isInstanceOf(NotFoundException.class);
        ResponseEntity<ErrorDto> txRes = handler.handleNotFound(txEx);
        assertThat(txRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(txRes.getBody().error()).isEqualTo("Transaction not found: txn_123");

        InvoiceNotFoundException invEx = new InvoiceNotFoundException("inv_456");
        assertThat(invEx.getMessage()).isEqualTo("Invoice not found: inv_456");
        assertThat(invEx).isInstanceOf(NotFoundException.class);
        ResponseEntity<ErrorDto> invRes = handler.handleNotFound(invEx);
        assertThat(invRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(invRes.getBody().error()).isEqualTo("Invoice not found: inv_456");

        FlightNotFoundException flEx = new FlightNotFoundException("fl_789");
        assertThat(flEx.getMessage()).isEqualTo("Flight not found: fl_789");
        assertThat(flEx).isInstanceOf(NotFoundException.class);
        ResponseEntity<ErrorDto> flRes = handler.handleNotFound(flEx);
        assertThat(flRes.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(flRes.getBody().error()).isEqualTo("Flight not found: fl_789");
    }

    @Test
    void testHandleConflict_WithAndWithoutId() {
        ResponseEntity<ErrorDto> res1 = handler.handleConflict(new ConflictException("Conflict occurred", "id_123"));
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res1.getBody()).isNotNull();
        assertThat(res1.getBody().error()).isEqualTo("Conflict occurred");
        assertThat(res1.getBody().id()).isEqualTo("id_123");

        ResponseEntity<ErrorDto> res2 = handler.handleConflict(new ConflictException("Conflict occurred"));
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody()).isNotNull();
        assertThat(res2.getBody().error()).isEqualTo("Conflict occurred");
        assertThat(res2.getBody().id()).isNull();
    }

    @Test
    void testHandleBadRequest() {
        ResponseEntity<ErrorDto> response = handler.handleBadRequest(new BadRequestException("Bad input"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Bad input");
    }

    @Test
    void testHandleHttpMessageNotReadable() {
        ResponseEntity<ErrorDto> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("Invalid JSON"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Malformed or missing request body");
    }

    @Test
    void testHandleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "field1", "Field1 is required"));
        bindingResult.addError(new FieldError("target", "field2", null));

        Method method = String.class.getMethod("toString");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ErrorDto> response = handler.handleValidationErrors(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("Field1 is required", "Validation error");
    }

    @Test
    void testHandleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "limit", null, new IllegalArgumentException());
        ResponseEntity<ErrorDto> response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("limit", "abc");
    }

    @Test
    void testHandleMissingParam() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("offset", "int");
        ResponseEntity<ErrorDto> response = handler.handleMissingParam(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("offset");
    }

    @Test
    void testHandleNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/unknown/path");
        ResponseEntity<ErrorDto> response = handler.handleNoResourceFound(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("/unknown/path");
    }

    @Test
    void testHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        ResponseEntity<ErrorDto> response = handler.handleMethodNotSupported(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("POST");
    }

    @Test
    void testHandleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<ErrorDto> response = handler.handleMediaTypeNotSupported(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).contains("text/plain");
    }

    @Test
    void testHandleDataIntegrity() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique failure", new RuntimeException("duplicate key"));
        ResponseEntity<ErrorDto> response = handler.handleDataIntegrity(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Database conflict / unique constraint violation");
    }

    @Test
    void testHandleConcurrencyFailure() {
        ConcurrencyFailureException ex = new ConcurrencyFailureException("busy snapshot");
        ResponseEntity<ErrorDto> response = handler.handleConcurrencyFailure(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Database concurrency conflict");
    }

    @Test
    void testHandleIllegalArgument() {
        ResponseEntity<ErrorDto> response = handler.handleIllegalArgument(new IllegalArgumentException("Illegal argument"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Illegal argument");
    }

    @Test
    void testHandleGenericException_ErrorResponse() {
        ErrorResponseException ex = new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY);
        ex.getBody().setDetail("Custom error detail");
        ResponseEntity<ErrorDto> response = handler.handleGenericException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Custom error detail");

        ErrorResponseException exWithoutDetail = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        ResponseEntity<ErrorDto> response2 = handler.handleGenericException(exWithoutDetail);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response2.getBody()).isNotNull();
    }

    @Test
    void testHandleGenericException_InternalServerError() {
        ResponseEntity<ErrorDto> response = handler.handleGenericException(new RuntimeException("secret db password or internal detail"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("Internal server error");
    }
}

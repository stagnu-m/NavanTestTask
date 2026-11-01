package com.navan.travel.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GlobalExceptionHandlerUnitTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void testHandleNotFound() {
        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(new NotFoundException("Item missing"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Item missing");
    }

    @Test
    void testHandleConflict_WithAndWithoutId() {
        ResponseEntity<Map<String, Object>> res1 = handler.handleConflict(new ConflictException("Conflict occurred", "id_123"));
        assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res1.getBody()).containsEntry("error", "Conflict occurred");
        assertThat(res1.getBody()).containsEntry("id", "id_123");

        ResponseEntity<Map<String, Object>> res2 = handler.handleConflict(new ConflictException("Conflict occurred"));
        assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res2.getBody()).containsEntry("error", "Conflict occurred");
        assertThat(res2.getBody()).doesNotContainKey("id");
    }

    @Test
    void testHandleBadRequest() {
        ResponseEntity<Map<String, Object>> response = handler.handleBadRequest(new BadRequestException("Bad input"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Bad input");
    }

    @Test
    void testHandleHttpMessageNotReadable() {
        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("Invalid JSON"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Malformed or missing request body");
    }

    @Test
    void testHandleValidationErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "field1", "Field1 is required"));
        bindingResult.addError(new FieldError("target", "field2", null));

        Method method = String.class.getMethod("toString");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationErrors(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("Field1 is required", "Validation error");
    }

    @Test
    void testHandleTypeMismatch() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "limit", null, new IllegalArgumentException());
        ResponseEntity<Map<String, Object>> response = handler.handleTypeMismatch(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("limit", "abc");
    }

    @Test
    void testHandleMissingParam() {
        MissingServletRequestParameterException ex = new MissingServletRequestParameterException("offset", "int");
        ResponseEntity<Map<String, Object>> response = handler.handleMissingParam(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("error").toString()).contains("offset");
    }

    @Test
    void testHandleNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "/unknown/path");
        ResponseEntity<Map<String, Object>> response = handler.handleNoResourceFound(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("error").toString()).contains("/unknown/path");
    }

    @Test
    void testHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST", List.of("GET"));
        ResponseEntity<Map<String, Object>> response = handler.handleMethodNotSupported(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().get("error").toString()).contains("POST");
    }

    @Test
    void testHandleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map<String, Object>> response = handler.handleMediaTypeNotSupported(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().get("error").toString()).contains("text/plain");
    }

    @Test
    void testHandleDataIntegrity() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("Unique failure", new RuntimeException("duplicate key"));
        ResponseEntity<Map<String, Object>> response = handler.handleDataIntegrity(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error").toString()).isEqualTo("Database conflict / unique constraint violation");
    }

    @Test
    void testHandleConcurrencyFailure() {
        ConcurrencyFailureException ex = new ConcurrencyFailureException("busy snapshot");
        ResponseEntity<Map<String, Object>> response = handler.handleConcurrencyFailure(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("error").toString()).isEqualTo("Database concurrency conflict");
    }

    @Test
    void testHandleIllegalArgument() {
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgument(new IllegalArgumentException("Illegal argument"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Illegal argument");
    }

    @Test
    void testHandleGenericException_ErrorResponse() {
        ErrorResponseException ex = new ErrorResponseException(HttpStatus.UNPROCESSABLE_ENTITY);
        ex.getBody().setDetail("Custom error detail");
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("error", "Custom error detail");

        ErrorResponseException exWithoutDetail = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
        ResponseEntity<Map<String, Object>> response2 = handler.handleGenericException(exWithoutDetail);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void testHandleGenericException_InternalServerError() {
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(new RuntimeException("secret db password or internal detail"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "Internal server error");
    }
}

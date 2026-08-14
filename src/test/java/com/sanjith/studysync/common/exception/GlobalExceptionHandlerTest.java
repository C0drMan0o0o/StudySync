package com.sanjith.studysync.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesValidationExceptionWithFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("registerRequest", "email", "must be a well-formed email address")));

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) response.getBody().get("errors");
        assertThat(fieldErrors).containsEntry("email", "must be a well-formed email address");
    }

    @Test
    void handlesEmailAlreadyInUseAsConflict() {
        ResponseEntity<Map<String, String>> response =
                handler.handleEmailInUse(new EmailAlreadyInUseException("alice@test.com"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Email already in use: alice@test.com");
    }

    @Test
    void handlesInvalidCredentialsAsUnauthorized() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("error", "Invalid email or password");
    }

    @Test
    void handlesResourceNotFoundAsNotFound() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new ResourceNotFoundException("Room not found: 99"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "Room not found: 99");
    }

    @Test
    void handlesLastGroupMemberAsConflict() {
        ResponseEntity<Map<String, String>> response = handler.handleLastGroupMember(
                new LastGroupMemberException("Cannot remove the last member of a group"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("error", "Cannot remove the last member of a group");
    }

    @Test
    void handlesInvalidBookingRequestAsBadRequest() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidBookingRequest(
                new InvalidBookingRequestException("Booking start time must be before end time"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Booking start time must be before end time");
    }

    @Test
    void rethrowsAccessDeniedInsteadOfHandlingIt() {
        AccessDeniedException ex = new AccessDeniedException("You must be a member of this group to manage it");

        assertThatThrownBy(() -> handler.rethrowAccessDenied(ex)).isSameAs(ex);
    }

    @Test
    void handlesUnexpectedExceptionsWithoutLeakingInternalMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnexpected(new RuntimeException("npe at some internal implementation detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "An unexpected error occurred");
    }

    @Test
    void handlesMalformedRequestBodyAsBadRequestInsteadOfServerError() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);

        ResponseEntity<Map<String, String>> response = handler.handleMalformedBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handlesMethodArgumentTypeMismatchAsBadRequestInsteadOfServerError() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");

        ResponseEntity<Map<String, String>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Invalid value for parameter: id");
    }

    @Test
    void handlesUnsupportedMethodAsMethodNotAllowedInsteadOfServerError() {
        ResponseEntity<Map<String, String>> response =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("DELETE"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void handlesNoResourceFoundAsNotFoundInsteadOfServerError() {
        ResponseEntity<Map<String, String>> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/no/such/path", "/no/such/path"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

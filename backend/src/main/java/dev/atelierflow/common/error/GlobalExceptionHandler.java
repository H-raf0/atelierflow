package dev.atelierflow.common.error;

import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 {@link ProblemDetail} responses.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives ProblemDetail bodies for all standard
 * Spring MVC errors (malformed JSON, unsupported media type…); the handlers below add the
 * application-specific cases. Every problem raised by the application carries a stable {@code code}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), "RESOURCE_NOT_FOUND");
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.CONFLICT, "Business rule violated", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        return unauthorized(problem(HttpStatus.UNAUTHORIZED, "Authentication failed",
                "Invalid username or password.", "INVALID_CREDENTIALS"));
    }

    /** Missing, malformed or expired access token (forwarded by the security filter chain). */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return unauthorized(problem(HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid access token is required.", "UNAUTHENTICATED"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex) {
        return unauthorized(problem(HttpStatus.UNAUTHORIZED, "Authentication required", ex.getMessage(), ex.getCode()));
    }

    /** Role check failed ({@code @PreAuthorize} or URL rule). */
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied",
                "Your role does not allow this operation.", "ACCESS_DENIED");
    }

    @ExceptionHandler(ForbiddenOperationException.class)
    ProblemDetail handleForbiddenOperation(ForbiddenOperationException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another user. Reload it and try again.", "CONCURRENT_MODIFICATION");
    }

    /** Safety net for constraints enforced by the database (unique keys, foreign keys). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        LOG.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data integrity violation",
                "The operation conflicts with existing data.", "DATA_INTEGRITY_VIOLATION");
    }

    /** Unknown property in a {@code sort} query parameter. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid sort property", ex.getMessage(), "INVALID_SORT");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request contains invalid fields.", "VALIDATION_FAILED");
        body.setProperty("violations", violations);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /** RFC 6750: a 401 on a bearer-protected API advertises the expected scheme. */
    private static ResponseEntity<ProblemDetail> unauthorized(ProblemDetail body) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }

    /** One invalid field of a request body. */
    public record FieldViolation(String field, String message) {
    }
}

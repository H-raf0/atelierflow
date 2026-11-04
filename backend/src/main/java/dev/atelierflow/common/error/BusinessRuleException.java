package dev.atelierflow.common.error;

/**
 * Thrown when a request is well-formed but conflicts with a business rule or with the current state
 * of a resource (duplicate code, forbidden state transition…); mapped to HTTP 409 Conflict.
 *
 * <p>The machine-readable {@code code} lets clients react to a specific rule without parsing the message.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

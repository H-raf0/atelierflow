package dev.atelierflow.common.error;

/**
 * The caller is authenticated but not allowed to act on this particular resource (e.g. a technician
 * starting a work order assigned to someone else); mapped to HTTP 403. Role-based checks are
 * declared with {@code @PreAuthorize}; this exception covers rules that depend on the data.
 */
public class ForbiddenOperationException extends RuntimeException {

    private final String code;

    public ForbiddenOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

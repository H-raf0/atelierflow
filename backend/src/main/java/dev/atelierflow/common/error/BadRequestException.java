package dev.atelierflow.common.error;

/** Request parameters that are individually valid but inconsistent together; mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

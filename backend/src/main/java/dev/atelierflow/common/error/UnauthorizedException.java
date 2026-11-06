package dev.atelierflow.common.error;

/** The caller is not (or no longer) authenticated, e.g. invalid refresh token; mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {

    private final String code;

    public UnauthorizedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

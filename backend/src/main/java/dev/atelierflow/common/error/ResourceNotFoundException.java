package dev.atelierflow.common.error;

/** Thrown when a resource addressed by its identifier does not exist; mapped to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super("%s %s not found".formatted(resource, id));
    }
}

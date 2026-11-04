package dev.atelierflow.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Stable JSON contract for paginated results.
 *
 * <p>Spring Data's {@code PageImpl} is an implementation detail whose JSON shape is not guaranteed;
 * exposing this record instead keeps the API (and the generated OpenAPI client) stable.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

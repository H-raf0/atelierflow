package dev.atelierflow.equipment;

import dev.atelierflow.equipment.dto.EquipmentFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the dynamic WHERE clause of the fleet search from the optional filter criteria.
 * Each criterion is added only when present, so a single query covers every combination.
 */
final class EquipmentSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private EquipmentSpecifications() {
    }

    static Specification<Equipment> matching(EquipmentFilter filter) {
        List<Specification<Equipment>> criteria = new ArrayList<>();
        if (filter.q() != null && !filter.q().isBlank()) {
            criteria.add(codeOrNameContains(filter.q()));
        }
        if (filter.workshop() != null && !filter.workshop().isBlank()) {
            criteria.add((root, query, cb) -> cb.equal(root.get("workshop"), filter.workshop()));
        }
        if (filter.criticality() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("criticality"), filter.criticality()));
        }
        if (filter.status() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        return Specification.allOf(criteria);
    }

    /** Case-insensitive "contains" on code or name; LIKE wildcards typed by the user are escaped. */
    private static Specification<Equipment> codeOrNameContains(String text) {
        String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("code")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.get("name")), pattern, LIKE_ESCAPE));
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

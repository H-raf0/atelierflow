package dev.atelierflow.workorder;

import dev.atelierflow.workorder.dto.WorkOrderFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Dynamic WHERE clause of the work order search; each criterion applies only when provided. */
final class WorkOrderSpecifications {

    private WorkOrderSpecifications() {
    }

    static Specification<WorkOrder> matching(WorkOrderFilter filter) {
        List<Specification<WorkOrder>> criteria = new ArrayList<>();
        if (filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + filter.q().trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            criteria.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern, '\\'),
                    cb.like(cb.lower(root.get("equipment").get("code")), pattern, '\\')));
        }
        if (filter.status() != null && !filter.status().isEmpty()) {
            criteria.add((root, query, cb) -> root.get("status").in(filter.status()));
        }
        if (filter.type() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("type"), filter.type()));
        }
        if (filter.priority() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("priority"), filter.priority()));
        }
        if (filter.equipmentId() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("equipment").get("id"), filter.equipmentId()));
        }
        if (filter.assigneeId() != null) {
            criteria.add((root, query, cb) -> cb.equal(root.get("assignee").get("id"), filter.assigneeId()));
        }
        return Specification.allOf(criteria);
    }
}

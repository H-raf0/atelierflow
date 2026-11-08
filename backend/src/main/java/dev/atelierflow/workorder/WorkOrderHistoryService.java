package dev.atelierflow.workorder;

import dev.atelierflow.common.audit.AuditRevision;
import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.workorder.dto.HistoryEntry;
import dev.atelierflow.workorder.dto.HistoryEntry.FieldChange;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Change history of a work order, read from the Envers audit tables.
 *
 * <p>Envers stores a full snapshot of the work order at each revision; the history is obtained by
 * comparing each snapshot with the previous one, field by field.
 */
@Service
@Transactional(readOnly = true)
public class WorkOrderHistoryService {

    /** Fields shown in the history, in display order, with how to render them as text. */
    private static final Map<String, Function<WorkOrder, Object>> TRACKED_FIELDS = new LinkedHashMap<>();

    static {
        TRACKED_FIELDS.put("status", WorkOrder::getStatus);
        TRACKED_FIELDS.put("priority", WorkOrder::getPriority);
        TRACKED_FIELDS.put("title", WorkOrder::getTitle);
        TRACKED_FIELDS.put("description", WorkOrder::getDescription);
        TRACKED_FIELDS.put("assignee", wo -> wo.getAssignee() == null ? null : wo.getAssignee().getFullName());
        TRACKED_FIELDS.put("scheduledFor", WorkOrder::getScheduledFor);
        TRACKED_FIELDS.put("timeSpentMinutes", WorkOrder::getTimeSpentMinutes);
        TRACKED_FIELDS.put("resolution", WorkOrder::getResolution);
        TRACKED_FIELDS.put("cancellationReason", WorkOrder::getCancellationReason);
    }

    private final EntityManager entityManager;
    private final WorkOrderRepository workOrders;

    public WorkOrderHistoryService(EntityManager entityManager, WorkOrderRepository workOrders) {
        this.entityManager = entityManager;
        this.workOrders = workOrders;
    }

    public List<HistoryEntry> history(Long workOrderId) {
        if (!workOrders.existsById(workOrderId)) {
            throw new ResourceNotFoundException("Work order", workOrderId);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> revisions = AuditReaderFactory.get(entityManager).createQuery()
                .forRevisionsOfEntity(WorkOrder.class, false, true)
                .add(AuditEntity.id().eq(workOrderId))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<HistoryEntry> entries = new ArrayList<>();
        WorkOrder previous = null;
        for (Object[] row : revisions) {
            WorkOrder snapshot = (WorkOrder) row[0];
            AuditRevision revision = (AuditRevision) row[1];
            RevisionType type = (RevisionType) row[2];
            entries.add(new HistoryEntry(revision.getId(), revision.getInstant(), revision.getUsername(),
                    type == RevisionType.ADD ? "CREATED" : "UPDATED", diff(previous, snapshot)));
            previous = snapshot;
        }
        return entries;
    }

    private static List<FieldChange> diff(WorkOrder before, WorkOrder after) {
        List<FieldChange> changes = new ArrayList<>();
        TRACKED_FIELDS.forEach((field, getter) -> {
            String from = before == null ? null : text(getter.apply(before));
            String to = text(getter.apply(after));
            if (!Objects.equals(from, to)) {
                changes.add(new FieldChange(field, from, to));
            }
        });
        return changes;
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }
}

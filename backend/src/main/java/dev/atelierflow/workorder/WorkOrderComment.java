package dev.atelierflow.workorder;

import dev.atelierflow.common.persistence.BaseEntity;
import dev.atelierflow.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/** A note left on a work order by anyone involved (diagnosis, parts ordered, follow-up…). */
@Entity
@Table(name = "work_order_comment")
public class WorkOrderComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false, updatable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private AppUser author;

    @Column(nullable = false, updatable = false)
    private String body;

    /** Required by JPA. */
    protected WorkOrderComment() {
    }

    public WorkOrderComment(WorkOrder workOrder, AppUser author, String body) {
        this.workOrder = Objects.requireNonNull(workOrder);
        this.author = Objects.requireNonNull(author);
        this.body = Objects.requireNonNull(body);
    }

    public WorkOrder getWorkOrder() {
        return workOrder;
    }

    public AppUser getAuthor() {
        return author;
    }

    public String getBody() {
        return body;
    }
}

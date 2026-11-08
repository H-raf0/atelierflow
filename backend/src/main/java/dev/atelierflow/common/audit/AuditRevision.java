package dev.atelierflow.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Envers revision: when and by whom a set of audited changes was committed.
 * The default revision entity only stores the time; the user is filled by {@link AuditRevisionListener}.
 */
@Entity
@Table(name = "audit_revision")
@RevisionEntity(AuditRevisionListener.class)
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @RevisionNumber
    private Long id;

    @RevisionTimestamp
    @Column(name = "revision_timestamp", nullable = false)
    private long timestamp;

    @Column(nullable = false, length = 50)
    private String username;

    public Long getId() {
        return id;
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp);
    }

    public String getUsername() {
        return username;
    }

    void setUsername(String username) {
        this.username = username;
    }
}

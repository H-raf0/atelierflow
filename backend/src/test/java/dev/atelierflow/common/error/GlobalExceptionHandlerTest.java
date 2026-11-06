package dev.atelierflow.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

/**
 * Infrastructure failures that are hard to provoke deterministically over HTTP (two users saving the
 * same row at the same millisecond, a constraint violated by a concurrent insert) are checked here.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void optimisticLockFailureBecomesConflict() {
        ProblemDetail problem = handler.handleOptimisticLock(
                new ObjectOptimisticLockingFailureException("WorkOrder", 1L));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties()).containsEntry("code", "CONCURRENT_MODIFICATION");
    }

    @Test
    void databaseConstraintViolationBecomesConflictWithoutLeakingSqlDetails() {
        ProblemDetail problem = handler.handleDataIntegrity(new DataIntegrityViolationException(
                "could not execute statement", new SQLException("duplicate key value violates uk_equipment_code")));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail()).doesNotContain("uk_equipment_code");
        assertThat(problem.getProperties()).containsEntry("code", "DATA_INTEGRITY_VIOLATION");
    }
}

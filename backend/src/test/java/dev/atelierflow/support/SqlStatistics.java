package dev.atelierflow.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.springframework.boot.test.context.TestComponent;

/** Counts the SQL statements executed by Hibernate, to prove the absence of N+1 queries. */
@TestComponent
public class SqlStatistics {

    private final Statistics statistics;

    public SqlStatistics(EntityManagerFactory entityManagerFactory) {
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    public void reset() {
        statistics.clear();
    }

    public long preparedStatements() {
        return statistics.getPrepareStatementCount();
    }
}

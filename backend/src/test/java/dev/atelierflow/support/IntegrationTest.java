package dev.atelierflow.support;

import dev.atelierflow.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base class for end-to-end tests of the HTTP API against a real PostgreSQL (Testcontainers).
 * Tables are emptied before each test so tests never depend on each other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
public abstract class IntegrationTest {

    @Autowired
    protected MockMvcTester mvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }
}

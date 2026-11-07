package dev.atelierflow.support;

import dev.atelierflow.TestcontainersConfiguration;
import dev.atelierflow.auth.AccessTokenService;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for end-to-end tests of the HTTP API against a real PostgreSQL (Testcontainers), with the
 * real security filter chain. Requests carry a real JWT issued by the application:
 * {@link #mvc} is authenticated as an administrator, {@link #as(AppUser)} as any other user.
 *
 * <p>Tables are emptied before each test so tests never depend on each other's data. All subclasses
 * share the same configuration, hence one Spring context and one PostgreSQL container.
 */
@SpringBootTest // test settings: src/test/resources/config/application.properties
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, Fixtures.class, SqlStatistics.class})
public abstract class IntegrationTest {

    @Autowired
    protected Fixtures fixtures;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private AccessTokenService accessTokens;

    /** Authenticated as {@link #admin}. */
    protected MockMvcTester mvc;

    protected AppUser admin;

    @BeforeEach
    void prepare() {
        databaseCleaner.clean();
        admin = fixtures.user("admin-test", Role.ADMIN);
        mvc = as(admin);
    }

    /** A client sending {@code Authorization: Bearer <token of user>} on every request. */
    protected MockMvcTester as(AppUser user) {
        return MockMvcTester.create(builder()
                .defaultRequest(MockMvcRequestBuilders.get("/")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.issue(user)))
                .build());
    }

    /** A client without credentials. */
    protected MockMvcTester anonymous() {
        return MockMvcTester.create(builder().build());
    }

    private DefaultMockMvcBuilder builder() {
        return MockMvcBuilders.webAppContextSetup(context).apply(SecurityMockMvcConfigurers.springSecurity());
    }
}

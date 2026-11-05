package dev.atelierflow.user;

import static org.assertj.core.api.Assertions.assertThat;

import dev.atelierflow.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class UserControllerIntegrationTest extends IntegrationTest {

    @Test
    void listsUsersSortedByNameAndFiltersByRole() {
        fixtures.user("zoe", Role.TECHNICIAN);
        fixtures.user("alice", Role.TECHNICIAN);
        fixtures.user("boss", Role.MANAGER);

        assertThat(mvc.get().uri("/api/users"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].username").asArray().containsExactly("alice", "boss", "zoe");
        assertThat(mvc.get().uri("/api/users?role=TECHNICIAN"))
                .hasStatusOk()
                .bodyJson().extractingPath("$[*].username").asArray().containsExactly("alice", "zoe");
    }
}

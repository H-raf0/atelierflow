package dev.atelierflow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    private static SecurityProperties withSecret(String secret) {
        return new SecurityProperties(secret, Duration.ofMinutes(15), Duration.ofDays(7), true, List.of());
    }

    @Test
    void refusesToStartWithoutSigningKey() {
        assertThatIllegalStateException().isThrownBy(() -> withSecret(null)).withMessageContaining("JWT_SECRET");
        assertThatIllegalStateException().isThrownBy(() -> withSecret("  ")).withMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesKeysShorterThan256Bits() {
        String short128Bits = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatIllegalStateException().isThrownBy(() -> withSecret(short128Bits))
                .withMessageContaining("256 bits");
    }

    @Test
    void buildsAnHmacSha256Key() {
        String key256Bits = Base64.getEncoder().encodeToString(new byte[32]);

        assertThat(withSecret(key256Bits).signingKey().getAlgorithm()).isEqualTo("HmacSHA256");
    }
}

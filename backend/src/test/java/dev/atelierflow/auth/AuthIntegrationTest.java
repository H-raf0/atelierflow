package dev.atelierflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.atelierflow.support.Fixtures;
import dev.atelierflow.support.IntegrationTest;
import dev.atelierflow.user.Role;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private SecurityProperties properties;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private MockMvcTester client;

    @BeforeEach
    void setUp() {
        fixtures.user("jdupont", Role.TECHNICIAN);
        client = anonymous();
    }

    private MvcTestResult login(String username, String password) {
        return client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username": "%s", "password": "%s"}
                        """.formatted(username, password))
                .exchange();
    }

    private MvcTestResult refresh(String token) {
        return client.post().uri("/api/auth/refresh").cookie(new Cookie(AuthController.REFRESH_COOKIE, token)).exchange();
    }

    private static String refreshCookie(MvcTestResult result) {
        return result.getResponse().getCookie(AuthController.REFRESH_COOKIE).getValue();
    }

    @Nested
    class Login {

        @Test
        void returnsAccessTokenAndSetsAHardenedRefreshCookie() {
            MvcTestResult result = login("jdupont", Fixtures.PASSWORD);

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
            assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
            assertThat(result).bodyJson().extractingPath("$.user.role").isEqualTo("TECHNICIAN");
            assertThat(result).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                    .contains("HttpOnly", "SameSite=Strict", "Path=/api/auth", "Max-Age=604800");
        }

        @Test
        void accessTokenGrantsAccessToTheApi() throws Exception {
            String accessToken = JsonPath.read(
                    login("jdupont", Fixtures.PASSWORD).getResponse().getContentAsString(), "$.accessToken");

            assertThat(client.get().uri("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .hasStatusOk()
                    .bodyJson().extractingPath("$.username").isEqualTo("jdupont");
        }

        @Test
        void wrongPasswordAndUnknownUserGetTheSameAnswer() {
            for (MvcTestResult result : new MvcTestResult[] {login("jdupont", "wrong"), login("nobody", "wrong")}) {
                assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
                assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_CREDENTIALS");
                assertThat(result.getResponse().getCookie(AuthController.REFRESH_COOKIE)).isNull();
            }
        }
    }

    @Nested
    class Refresh {

        @Test
        void rotatesTheRefreshToken() {
            String first = refreshCookie(login("jdupont", Fixtures.PASSWORD));

            MvcTestResult refreshed = refresh(first);

            assertThat(refreshed).hasStatusOk();
            assertThat(refreshed).bodyJson().extractingPath("$.accessToken").asString().isNotBlank();
            String second = refreshCookie(refreshed);
            assertThat(second).isNotEqualTo(first);
            assertThat(refresh(second)).hasStatusOk();
        }

        @Test
        void reusingARotatedTokenRevokesEverySessionOfTheUser() {
            String stolen = refreshCookie(login("jdupont", Fixtures.PASSWORD));
            String legitimate = refreshCookie(refresh(stolen));
            String otherDevice = refreshCookie(login("jdupont", Fixtures.PASSWORD));

            MvcTestResult replay = refresh(stolen);

            assertThat(replay).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(replay).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REFRESH_TOKEN");
            assertThat(refresh(legitimate)).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(refresh(otherDevice)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void unknownOrMissingTokenIsRejected() {
            assertThat(refresh("not-a-real-token")).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(client.post().uri("/api/auth/refresh"))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson().extractingPath("$.code").isEqualTo("INVALID_REFRESH_TOKEN");
        }

        @Test
        void tokensAreStoredHashedAndExpiredOnesArePurged() {
            String token = refreshCookie(login("jdupont", Fixtures.PASSWORD));

            assertThat(refreshTokens.findByTokenHash(token)).isEmpty();
            assertThat(refreshTokens.findByTokenHash(RefreshTokenService.sha256(token))).isPresent();

            refreshTokenService.purgeExpired();
            assertThat(refreshTokens.count()).isEqualTo(1);
        }
    }

    @Nested
    class Logout {

        @Test
        void revokesTheRefreshTokenAndClearsTheCookie() {
            String token = refreshCookie(login("jdupont", Fixtures.PASSWORD));

            MvcTestResult result = client.post().uri("/api/auth/logout")
                    .cookie(new Cookie(AuthController.REFRESH_COOKIE, token)).exchange();

            assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
            assertThat(refresh(token)).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void isIdempotentWithoutCookie() {
            assertThat(client.post().uri("/api/auth/logout")).hasStatus(HttpStatus.NO_CONTENT);
        }
    }

    @Nested
    class AccessTokens {

        @Test
        void apiRequiresAToken() {
            MvcTestResult result = client.get().uri("/api/equipment").exchange();

            assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("UNAUTHENTICATED");
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        }

        @Test
        void expiredTokenIsRejected() {
            Instant past = Instant.now().minusSeconds(3600);
            String expired = sign(properties.jwtSecret(), JwtClaimsSet.builder()
                    .issuer(AccessTokenService.ISSUER).subject("admin-test")
                    .issuedAt(past.minusSeconds(900)).expiresAt(past)
                    .claim("uid", admin.getId()).claim("role", "ADMIN").claim("name", "Admin").build());

            assertThat(client.get().uri("/api/equipment").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void tokenSignedWithAnotherKeyIsRejected() {
            String forged = sign(Base64.getEncoder().encodeToString("an-attacker-key-of-thirty-two-bytes!".getBytes()),
                    JwtClaimsSet.builder()
                            .issuer(AccessTokenService.ISSUER).subject("hacker")
                            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
                            .claim("uid", 1).claim("role", "ADMIN").claim("name", "Hacker").build());

            assertThat(client.get().uri("/api/equipment").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void publicEndpointsNeedNoToken() {
            assertThat(client.get().uri("/v3/api-docs")).hasStatusOk();
            assertThat(client.get().uri("/actuator/health")).hasStatusOk();
        }

        private static String sign(String base64Key, JwtClaimsSet claims) {
            var key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "HmacSHA256");
            return new NimbusJwtEncoder(new ImmutableSecret<>(key))
                    .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                    .getTokenValue();
        }
    }
}

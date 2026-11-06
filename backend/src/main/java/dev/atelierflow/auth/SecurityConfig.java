package dev.atelierflow.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless security: every API call carries a JWT in the {@code Authorization: Bearer} header,
 * validated by Spring Security's OAuth2 resource server support (signature, expiry, issuer).
 *
 * <p>Coarse rules (public vs authenticated) are declared here; role rules are declared next to each
 * endpoint with {@code @PreAuthorize}, and data-dependent rules (a technician only works on their own
 * work orders) are checked in the services.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/actuator/health/**", "/actuator/info"
    };

    /**
     * Authentication and authorization failures raised in the filter chain are handed to the MVC
     * exception resolver, so they get the same ProblemDetail body as every other error.
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenService accessTokenService,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        return http
                // CSRF protection targets cookie-authenticated requests; the API authenticates with a
                // header the browser never adds by itself. The only cookie (refresh) is SameSite=Strict.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(accessTokenService))
                        .authenticationEntryPoint((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex)))
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.signingKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.signingKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AccessTokenService.ISSUER));
        return decoder;
    }

    /** {@code {bcrypt}} by default, while still able to verify hashes of other algorithms (migration-friendly). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * Only needed when the SPA is served from another origin than the API. In the provided Docker
     * setup nginx serves both under the same origin, so the list is empty and no CORS header is sent.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.corsAllowedOrigins());
        cors.addAllowedMethod("*");
        cors.addAllowedHeader("*");
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}

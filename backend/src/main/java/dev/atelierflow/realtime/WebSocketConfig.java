package dev.atelierflow.realtime;

import dev.atelierflow.auth.SecurityProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket at {@code /ws}.
 *
 * <ul>
 *   <li>The server pushes to topics under {@code /topic} through an in-memory broker. With several
 *       backend instances, it would be replaced by a broker relay (RabbitMQ/ActiveMQ) so that every
 *       instance sees every message.</li>
 *   <li>Browsers cannot set headers on the WebSocket handshake, so the JWT travels in the STOMP
 *       {@code CONNECT} frame and is checked by {@link StompAuthenticationInterceptor}.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    static final String ENDPOINT = "/ws";

    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final SecurityProperties securityProperties;

    WebSocketConfig(StompAuthenticationInterceptor authenticationInterceptor, SecurityProperties securityProperties) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Same-origin handshakes only, unless other origins are explicitly allowed (as for CORS)
        registry.addEndpoint(ENDPOINT)
                .setAllowedOriginPatterns(securityProperties.corsAllowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}

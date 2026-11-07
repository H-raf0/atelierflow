package dev.atelierflow.realtime;

import dev.atelierflow.auth.AccessTokenService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Authenticates STOMP sessions with the same JWT as the REST API.
 *
 * <ul>
 *   <li>{@code CONNECT} must carry {@code Authorization: Bearer <access token>}; the token is validated
 *       by the same {@link JwtDecoder} and the user attached to the session.</li>
 *   <li>Any other frame from an unauthenticated session is refused.</li>
 *   <li>Clients may not publish to {@code /topic}: only the server broadcasts.</li>
 * </ul>
 * A rejected frame closes the session with a STOMP ERROR frame.
 */
@Component
class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final AccessTokenService accessTokenService;

    StompAuthenticationInterceptor(JwtDecoder jwtDecoder, AccessTokenService accessTokenService) {
        this.jwtDecoder = jwtDecoder;
        this.accessTokenService = accessTokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeats
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith(BEARER)) {
                throw new AccessDeniedException("Missing bearer token in the CONNECT frame");
            }
            // throws JwtException (→ ERROR frame) if the signature or expiry is invalid
            accessor.setUser(accessTokenService.convert(jwtDecoder.decode(authorization.substring(BEARER.length()))));
        } else if (command == StompCommand.SUBSCRIBE || command == StompCommand.SEND) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Not authenticated");
            }
            if (command == StompCommand.SEND && accessor.getDestination() != null
                    && accessor.getDestination().startsWith("/topic")) {
                throw new AccessDeniedException("Only the server publishes to topics");
            }
        }
        return message;
    }
}

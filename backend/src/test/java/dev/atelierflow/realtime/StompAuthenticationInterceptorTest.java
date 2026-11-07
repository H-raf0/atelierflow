package dev.atelierflow.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.atelierflow.auth.AccessTokenService;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class StompAuthenticationInterceptorTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final AccessTokenService tokens = mock(AccessTokenService.class);
    private final StompAuthenticationInterceptor interceptor = new StompAuthenticationInterceptor(decoder, tokens);
    private final MessageChannel channel = mock(MessageChannel.class);

    private static Message<byte[]> frame(StompCommand command, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void connectWithoutBearerTokenIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithValidTokenAttachesTheUser() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer abc");
        accessor.setLeaveMutable(true);
        Message<byte[]> connect = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Jwt jwt = mock(Jwt.class);
        when(decoder.decode("abc")).thenReturn(jwt);
        when(tokens.convert(jwt)).thenReturn(null);

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
    }

    @Test
    void subscribeWithoutAuthenticatedSessionIsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/work-orders", null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void clientsCannotPublishToTopics() {
        Principal user = () -> "jdupont";

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/topic/work-orders", user), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(interceptor.preSend(frame(StompCommand.SEND, "/app/ping", user), channel)).isNotNull();
        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/work-orders", user), channel)).isNotNull();
    }
}

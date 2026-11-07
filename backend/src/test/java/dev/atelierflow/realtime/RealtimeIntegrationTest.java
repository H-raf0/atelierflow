package dev.atelierflow.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.atelierflow.TestcontainersConfiguration;
import dev.atelierflow.auth.AccessTokenService;
import dev.atelierflow.support.DatabaseCleaner;
import dev.atelierflow.support.Fixtures;
import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Real server on a random port, real STOMP client: proves that a change made through the REST API
 * is pushed to subscribed clients, and that the WebSocket requires a valid JWT.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, Fixtures.class})
class RealtimeIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private AccessTokenService accessTokens;

    @Autowired
    private SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;
    private AppUser manager;
    private Equipment press;

    @BeforeEach
    void setUp() {
        databaseCleaner.clean();
        manager = fixtures.user("chef", Role.MANAGER);
        press = fixtures.equipment("PRS-001", Criticality.HIGH);
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return stompClient.connectAsync("ws://localhost:%d/ws".formatted(port), new WebSocketHttpHeaders(),
                connectHeaders, new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    /**
     * Subscribes and waits until the server has registered the subscription, so that no message can
     * be missed afterwards (the simple broker does not answer SUBSCRIBE with a RECEIPT frame).
     */
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String topic) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(topic, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() ->
                !userRegistry.findSubscriptions(s -> s.getDestination().equals(topic)).isEmpty());
        return received;
    }

    @Test
    void workOrderAndMachineChangesArePushedToSubscribers() throws Exception {
        AppUser technician = fixtures.user("jdupont", Role.TECHNICIAN);
        StompSession session = connect(accessTokens.issue(technician));
        BlockingQueue<Map<String, Object>> workOrderEvents = subscribe(session, RealtimeBroadcaster.WORK_ORDERS_TOPIC);
        BlockingQueue<Map<String, Object>> equipmentEvents = subscribe(session, RealtimeBroadcaster.EQUIPMENT_TOPIC);

        RestClient.create("http://localhost:" + port).post().uri("/api/work-orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessTokens.issue(manager))
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"type": "CORRECTIVE", "equipmentId": %d, "title": "Fuite hydraulique"}
                        """.formatted(press.getId()))
                .retrieve().toBodilessEntity();

        Map<String, Object> workOrder = workOrderEvents.poll(5, TimeUnit.SECONDS);
        assertThat(workOrder).isNotNull()
                .containsEntry("title", "Fuite hydraulique")
                .containsEntry("status", "OPEN")
                .containsEntry("equipmentCode", "PRS-001");

        Map<String, Object> equipment = equipmentEvents.poll(5, TimeUnit.SECONDS);
        assertThat(equipment).isNotNull()
                .containsEntry("code", "PRS-001")
                .containsEntry("status", "DOWN");
        session.disconnect();
    }

    @Test
    void connectionWithoutTokenIsRefused() {
        assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void connectionWithInvalidTokenIsRefused() {
        assertThatThrownBy(() -> connect("not.a.jwt")).isInstanceOf(ExecutionException.class);
    }
}

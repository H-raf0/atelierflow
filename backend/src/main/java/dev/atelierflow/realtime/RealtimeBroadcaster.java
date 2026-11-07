package dev.atelierflow.realtime;

import dev.atelierflow.equipment.EquipmentStatusChangedEvent;
import dev.atelierflow.workorder.WorkOrderChangedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes domain events to the connected browsers.
 *
 * <p>{@link TransactionalEventListener} runs <em>after commit</em>: a client is never told about a
 * change that is then rolled back, and when it reloads data in reaction, the change is visible.
 * The domain modules publish plain Spring events and know nothing about WebSocket.
 */
@Component
class RealtimeBroadcaster {

    static final String WORK_ORDERS_TOPIC = "/topic/work-orders";
    static final String EQUIPMENT_TOPIC = "/topic/equipment";

    private final SimpMessagingTemplate messaging;

    RealtimeBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onWorkOrderChanged(WorkOrderChangedEvent event) {
        messaging.convertAndSend(WORK_ORDERS_TOPIC, event.workOrder());
    }

    @TransactionalEventListener(fallbackExecution = true)
    void onEquipmentStatusChanged(EquipmentStatusChangedEvent event) {
        messaging.convertAndSend(EQUIPMENT_TOPIC, event);
    }
}

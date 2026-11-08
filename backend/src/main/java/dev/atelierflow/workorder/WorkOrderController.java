package dev.atelierflow.workorder;

import dev.atelierflow.auth.AuthenticatedUser;
import dev.atelierflow.auth.ManagerOrAdmin;
import dev.atelierflow.common.web.PageResponse;
import dev.atelierflow.workorder.dto.AddCommentRequest;
import dev.atelierflow.workorder.dto.CancelWorkOrderRequest;
import dev.atelierflow.workorder.dto.CommentResponse;
import dev.atelierflow.workorder.dto.CompleteWorkOrderRequest;
import dev.atelierflow.workorder.dto.CreateWorkOrderRequest;
import dev.atelierflow.workorder.dto.HistoryEntry;
import dev.atelierflow.workorder.dto.PlanWorkOrderRequest;
import dev.atelierflow.workorder.dto.UpdateWorkOrderRequest;
import dev.atelierflow.workorder.dto.WorkOrderDetail;
import dev.atelierflow.workorder.dto.WorkOrderFilter;
import dev.atelierflow.workorder.dto.WorkOrderSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Work orders. State changes are explicit actions ({@code POST /{id}/start}…) rather than a generic
 * status update: each action has its own payload, rules and permissions, and an illegal move returns 409.
 *
 * <p>Permissions: everyone reads and reports breakdowns; planning, assignment, edition and
 * cancellation are reserved to managers; a technician starts and completes only their own work orders.
 */
@RestController
@RequestMapping("/api/work-orders")
@Tag(name = "Work orders", description = "Maintenance jobs and their life cycle")
class WorkOrderController {

    private final WorkOrderService service;
    private final WorkOrderHistoryService historyService;

    WorkOrderController(WorkOrderService service, WorkOrderHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "Search work orders with server-side paging")
    PageResponse<WorkOrderSummary> search(
            @ParameterObject WorkOrderFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "reportedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.search(filter, pageable);
    }

    @GetMapping("/board")
    @Operation(summary = "Kanban board: active work orders and those completed in the last 7 days")
    List<WorkOrderSummary> board() {
        return service.board();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one work order")
    WorkOrderDetail get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Create a work order (a corrective one marks the machine as down; preventive: managers only)")
    ResponseEntity<WorkOrderDetail> create(@Valid @RequestBody CreateWorkOrderRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser caller) {
        WorkOrderDetail created = service.create(request, caller);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @ManagerOrAdmin
    @Operation(summary = "Edit title, description and priority of an open work order")
    WorkOrderDetail update(@PathVariable Long id, @Valid @RequestBody UpdateWorkOrderRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/plan")
    @ManagerOrAdmin
    @Operation(summary = "OPEN → PLANNED: assign a technician and a date")
    WorkOrderDetail plan(@PathVariable Long id, @Valid @RequestBody PlanWorkOrderRequest request) {
        return service.plan(id, request);
    }

    @PostMapping("/{id}/unplan")
    @ManagerOrAdmin
    @Operation(summary = "PLANNED → OPEN: withdraw the assignment")
    WorkOrderDetail unplan(@PathVariable Long id) {
        return service.unplan(id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "PLANNED → IN_PROGRESS (a technician only starts their own work orders)")
    WorkOrderDetail start(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser caller) {
        return service.start(id, caller);
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "IN_PROGRESS → DONE: record the time spent and the resolution")
    WorkOrderDetail complete(@PathVariable Long id, @Valid @RequestBody CompleteWorkOrderRequest request,
                             @AuthenticationPrincipal AuthenticatedUser caller) {
        return service.complete(id, request, caller);
    }

    @PostMapping("/{id}/cancel")
    @ManagerOrAdmin
    @Operation(summary = "OPEN or PLANNED → CANCELLED, with a reason")
    WorkOrderDetail cancel(@PathVariable Long id, @Valid @RequestBody CancelWorkOrderRequest request) {
        return service.cancel(id, request);
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Audit trail: who changed what and when (Hibernate Envers)")
    List<HistoryEntry> history(@PathVariable Long id) {
        return historyService.history(id);
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Comments of a work order, oldest first")
    List<CommentResponse> comments(@PathVariable Long id) {
        return service.comments(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment")
    CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody AddCommentRequest request,
                               @AuthenticationPrincipal AuthenticatedUser caller) {
        return service.addComment(id, request, caller);
    }
}

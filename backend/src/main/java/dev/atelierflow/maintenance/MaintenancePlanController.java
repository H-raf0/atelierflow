package dev.atelierflow.maintenance;

import dev.atelierflow.auth.ManagerOrAdmin;
import dev.atelierflow.maintenance.dto.CreateMaintenancePlanRequest;
import dev.atelierflow.maintenance.dto.GenerationResult;
import dev.atelierflow.maintenance.dto.MaintenancePlanResponse;
import dev.atelierflow.maintenance.dto.UpdateMaintenancePlanRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/maintenance-plans")
@Tag(name = "Maintenance plans", description = "Preventive maintenance by calendar or operating hours")
class MaintenancePlanController {

    private final MaintenancePlanService service;

    MaintenancePlanController(MaintenancePlanService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List plans, optionally of one machine")
    List<MaintenancePlanResponse> listMaintenancePlans(@RequestParam(required = false) Long equipmentId) {
        return service.list(equipmentId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one plan")
    MaintenancePlanResponse getMaintenancePlan(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ManagerOrAdmin
    @Operation(summary = "Create a plan")
    ResponseEntity<MaintenancePlanResponse> createMaintenancePlan(@Valid @RequestBody CreateMaintenancePlanRequest request) {
        MaintenancePlanResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @ManagerOrAdmin
    @Operation(summary = "Update title, instructions, interval or activation")
    MaintenancePlanResponse updateMaintenancePlan(@PathVariable Long id, @Valid @RequestBody UpdateMaintenancePlanRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ManagerOrAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a plan (generated work orders are kept)")
    void deleteMaintenancePlan(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/generate")
    @ManagerOrAdmin
    @Operation(summary = "Run the preventive generation now instead of waiting for the scheduler")
    GenerationResult generatePreventiveWorkOrders() {
        return new GenerationResult(service.generateDueWorkOrders());
    }
}

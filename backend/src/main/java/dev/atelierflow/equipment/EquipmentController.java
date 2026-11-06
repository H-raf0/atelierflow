package dev.atelierflow.equipment;

import dev.atelierflow.auth.AdminOnly;
import dev.atelierflow.auth.ManagerOrAdmin;
import dev.atelierflow.common.web.PageResponse;
import dev.atelierflow.equipment.dto.CreateEquipmentRequest;
import dev.atelierflow.equipment.dto.EquipmentFilter;
import dev.atelierflow.equipment.dto.EquipmentResponse;
import dev.atelierflow.equipment.dto.UpdateEquipmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/equipment")
@Tag(name = "Equipment", description = "Workshop machine fleet")
class EquipmentController {

    private final EquipmentService service;

    EquipmentController(EquipmentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Search the fleet (free text, workshop, criticality, status) with server-side paging")
    PageResponse<EquipmentResponse> search(
            @ParameterObject EquipmentFilter filter,
            @ParameterObject @PageableDefault(size = 20, sort = "code") Pageable pageable) {
        return service.search(filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one machine")
    EquipmentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/workshops")
    @Operation(summary = "List the distinct workshops, for filter drop-downs")
    List<String> workshops() {
        return service.workshops();
    }

    @PostMapping
    @ManagerOrAdmin
    @Operation(summary = "Register a machine")
    ResponseEntity<EquipmentResponse> create(@Valid @RequestBody CreateEquipmentRequest request) {
        EquipmentResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @ManagerOrAdmin
    @Operation(summary = "Update the editable attributes of a machine")
    EquipmentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateEquipmentRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a machine (refused with 409 once it has work orders)")
    void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

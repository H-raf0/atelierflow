package dev.atelierflow.equipment;

import dev.atelierflow.common.error.BusinessRuleException;
import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.common.web.PageResponse;
import dev.atelierflow.equipment.dto.CreateEquipmentRequest;
import dev.atelierflow.equipment.dto.EquipmentFilter;
import dev.atelierflow.equipment.dto.EquipmentResponse;
import dev.atelierflow.equipment.dto.UpdateEquipmentRequest;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases of the fleet. Transactions are opened here (not in the controller) and the service only
 * returns DTOs: entities never leave the transaction, so no lazy-loading surprise can reach the web layer.
 */
@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository repository;
    private final EquipmentMapper mapper;

    public EquipmentService(EquipmentRepository repository, EquipmentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public PageResponse<EquipmentResponse> search(EquipmentFilter filter, Pageable pageable) {
        return PageResponse.from(
                repository.findAll(EquipmentSpecifications.matching(filter), pageable).map(mapper::toResponse));
    }

    public EquipmentResponse get(Long id) {
        return mapper.toResponse(findOrThrow(id));
    }

    public List<String> workshops() {
        return repository.findDistinctWorkshops();
    }

    @Transactional
    public EquipmentResponse create(CreateEquipmentRequest request) {
        if (repository.existsByCode(request.code())) {
            throw new BusinessRuleException("EQUIPMENT_CODE_TAKEN",
                    "An equipment with code %s already exists".formatted(request.code()));
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional
    public EquipmentResponse update(Long id, UpdateEquipmentRequest request) {
        Equipment equipment = findOrThrow(id);
        equipment.updateDetails(request.name(), request.workshop(), request.criticality(), request.commissionedOn());
        // flush so that the response carries the new updatedAt timestamp
        return mapper.toResponse(repository.saveAndFlush(equipment));
    }

    /**
     * Deletes a machine that has no maintenance history. The rule is enforced by the foreign key of
     * the work orders (ON DELETE RESTRICT) rather than by a query here, which keeps the equipment
     * module independent from the work order module and is also safe against concurrent inserts.
     */
    @Transactional
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
        try {
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("EQUIPMENT_HAS_HISTORY",
                    "Equipment %d has work orders and cannot be deleted".formatted(id));
        }
    }

    private Equipment findOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Equipment", id));
    }
}

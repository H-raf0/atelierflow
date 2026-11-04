package dev.atelierflow.equipment;

import dev.atelierflow.equipment.dto.CreateEquipmentRequest;
import dev.atelierflow.equipment.dto.EquipmentResponse;
import org.mapstruct.Mapper;

/**
 * Entity ↔ DTO conversion, generated at compile time by MapStruct (no reflection at runtime).
 * Unmapped target properties fail the build, so adding a field to a DTO can never be forgotten here.
 */
@Mapper
public interface EquipmentMapper {

    EquipmentResponse toResponse(Equipment equipment);

    /** Uses the public constructor of {@link Equipment}, so its invariants are checked. */
    Equipment toEntity(CreateEquipmentRequest request);
}

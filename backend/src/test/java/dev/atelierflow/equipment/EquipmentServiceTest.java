package dev.atelierflow.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.atelierflow.common.error.BusinessRuleException;
import dev.atelierflow.common.error.ResourceNotFoundException;
import dev.atelierflow.equipment.dto.CreateEquipmentRequest;
import dev.atelierflow.equipment.dto.UpdateEquipmentRequest;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Pure unit test: the repository is mocked, the (generated) mapper is the real one. */
@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository repository;

    private EquipmentService service;

    @BeforeEach
    void setUp() {
        service = new EquipmentService(repository, Mappers.getMapper(EquipmentMapper.class));
    }

    @Test
    void createRefusesAnAlreadyUsedCode() {
        when(repository.existsByCode("PRS-001")).thenReturn(true);
        var request = new CreateEquipmentRequest("PRS-001", "Presse", "Emboutissage", Criticality.HIGH, null, null);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("EQUIPMENT_CODE_TAKEN");
        verify(repository, never()).save(any());
    }

    @Test
    void createPersistsANewOperationalMachine() {
        when(repository.existsByCode("PRS-001")).thenReturn(false);
        when(repository.save(any(Equipment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                new CreateEquipmentRequest("PRS-001", "Presse", "Emboutissage", Criticality.HIGH, 12.5, null));

        assertThat(response.code()).isEqualTo("PRS-001");
        assertThat(response.status()).isEqualTo(EquipmentStatus.OPERATIONAL);
        assertThat(response.operatingHours()).isEqualTo(12.5);
    }

    @Test
    void updateChangesOnlyEditableAttributes() {
        var existing = new Equipment("TOUR-01", "Tour", "Usinage", Criticality.LOW, 40, null);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        var response = service.update(1L, new UpdateEquipmentRequest("Tour CN", "Usinage 2", Criticality.HIGH, null));

        assertThat(response.name()).isEqualTo("Tour CN");
        assertThat(response.workshop()).isEqualTo("Usinage 2");
        assertThat(response.code()).isEqualTo("TOUR-01");
        assertThat(response.operatingHours()).isEqualTo(40);
    }

    @Test
    void operationsOnUnknownIdThrowNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Equipment 7 not found");
        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(ResourceNotFoundException.class);
    }
}

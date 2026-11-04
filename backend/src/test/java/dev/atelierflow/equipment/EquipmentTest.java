package dev.atelierflow.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class EquipmentTest {

    private final Equipment equipment = new Equipment("TOUR-01", "Tour", "Usinage", Criticality.LOW, 10, null);

    @Test
    void newEquipmentIsOperational() {
        assertThat(equipment.getStatus()).isEqualTo(EquipmentStatus.OPERATIONAL);
    }

    @Test
    void operatingHoursAccumulate() {
        equipment.addOperatingHours(2.5);
        equipment.addOperatingHours(1);

        assertThat(equipment.getOperatingHours()).isEqualTo(13.5);
    }

    @Test
    void operatingHourCounterCannotGoBackwards() {
        assertThatIllegalArgumentException().isThrownBy(() -> equipment.addOperatingHours(-1));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Equipment("X-01", "X", "Usinage", Criticality.LOW, -5, null));
    }

    @Test
    void transientEntitiesAreOnlyEqualToThemselves() {
        var other = new Equipment("TOUR-01", "Tour", "Usinage", Criticality.LOW, 10, null);

        assertThat(equipment).isEqualTo(equipment).isNotEqualTo(other).isNotEqualTo("TOUR-01");
        assertThat(equipment.hashCode()).isEqualTo(other.hashCode());
    }
}

package dev.atelierflow.support;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import dev.atelierflow.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;

/** Persists reference data for integration tests. */
@TestComponent
public class Fixtures {

    private final EquipmentRepository equipments;
    private final UserRepository users;

    public Fixtures(EquipmentRepository equipments, UserRepository users) {
        this.equipments = equipments;
        this.users = users;
    }

    public Equipment equipment(String code, Criticality criticality) {
        return equipments.save(new Equipment(code, "Machine " + code, "Usinage", criticality, 100, null));
    }

    public AppUser user(String username, Role role) {
        return users.save(new AppUser(username, "User " + username, role));
    }
}

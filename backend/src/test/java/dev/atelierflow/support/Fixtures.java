package dev.atelierflow.support;

import dev.atelierflow.equipment.Criticality;
import dev.atelierflow.equipment.Equipment;
import dev.atelierflow.equipment.EquipmentRepository;
import dev.atelierflow.user.AppUser;
import dev.atelierflow.user.Role;
import dev.atelierflow.user.UserRepository;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Persists reference data for integration tests. */
@TestComponent
public class Fixtures {

    /** Password of every user created by {@link #user}. */
    public static final String PASSWORD = "Test-Passw0rd";

    private final EquipmentRepository equipments;
    private final UserRepository users;
    private final String encodedPassword;

    public Fixtures(EquipmentRepository equipments, UserRepository users, PasswordEncoder passwordEncoder) {
        this.equipments = equipments;
        this.users = users;
        // BCrypt is deliberately slow: encode once, reuse for every test user
        this.encodedPassword = passwordEncoder.encode(PASSWORD);
    }

    public Equipment equipment(String code, Criticality criticality) {
        return equipments.save(new Equipment(code, "Machine " + code, "Usinage", criticality, 100, null));
    }

    public AppUser user(String username, Role role) {
        return users.save(new AppUser(username, "User " + username, role, encodedPassword));
    }
}

package dev.atelierflow.user;

import dev.atelierflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Objects;

/** A person using the application. Named {@code AppUser} because {@code user} is reserved in SQL. */
@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** Required by JPA. */
    protected AppUser() {
    }

    public AppUser(String username, String fullName, Role role) {
        this.username = Objects.requireNonNull(username);
        this.fullName = Objects.requireNonNull(fullName);
        this.role = Objects.requireNonNull(role);
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }
}

package dev.atelierflow.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    List<AppUser> findByRoleOrderByFullName(Role role);

    List<AppUser> findAllByOrderByFullName();
}

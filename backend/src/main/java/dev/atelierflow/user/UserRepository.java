package dev.atelierflow.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {

    List<AppUser> findByRoleOrderByFullName(Role role);

    List<AppUser> findAllByOrderByFullName();
}

package dev.atelierflow.user;

import dev.atelierflow.user.dto.UserSummary;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository repository;
    private final UserMapper mapper;

    public UserService(UserRepository repository, UserMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /** All users, or only those with the given role (e.g. technicians for an assignment drop-down). */
    public List<UserSummary> list(Role role) {
        List<AppUser> users = role == null
                ? repository.findAllByOrderByFullName()
                : repository.findByRoleOrderByFullName(role);
        return users.stream().map(mapper::toSummary).toList();
    }
}

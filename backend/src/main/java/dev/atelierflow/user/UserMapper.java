package dev.atelierflow.user;

import dev.atelierflow.user.dto.UserSummary;
import org.mapstruct.Mapper;

@Mapper
public interface UserMapper {

    UserSummary toSummary(AppUser user);
}

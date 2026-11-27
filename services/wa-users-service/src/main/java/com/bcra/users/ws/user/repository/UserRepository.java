package com.bcra.users.ws.user.repository;

import java.util.UUID;
import com.bcra.users.ws.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByUniquenessField(String value);
    boolean existsByDisplayName(String value);
    boolean existsByContactEmail(String value);
    boolean existsByContactPhone(String value);
}

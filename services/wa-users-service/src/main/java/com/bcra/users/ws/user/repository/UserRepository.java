package com.bcra.users.ws.user.repository;

import java.util.Optional;
import java.util.UUID;
import com.bcra.users.ws.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByUniquenessField(String value);
    boolean existsByDisplayName(String value);
    boolean existsByContactEmail(String value);
    boolean existsByContactPhone(String value);
}

package com.bcra.users.ws.user.entity;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    private UUID id;
    private String externalId;
    private String uniquenessField;
    private String displayName;
    private String contactEmail;
    private String contactPhone;
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}

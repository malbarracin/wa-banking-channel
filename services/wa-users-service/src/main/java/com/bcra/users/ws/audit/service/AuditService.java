package com.bcra.users.ws.audit.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.audit.entity.AuditAction;
import com.bcra.users.ws.audit.entity.AuditEvent;
import com.bcra.users.ws.audit.entity.AuditResult;
import com.bcra.users.ws.audit.repository.AuditEventRepository;
import com.bcra.users.ws.audit.dto.AuditEventResponse;
import com.bcra.users.ws.audit.mapper.AuditMapper;
import com.bcra.users.ws.exception.UserNotFoundException;
import com.bcra.users.ws.user.repository.UserRepository;

@Service
public class AuditService {
    private static final Set<String> ALLOWED_FIELDS = Set.of("tipoDocumento", "numeroDocumento", "nombreCompleto",
            "nombreTratamiento", "fechaNacimiento", "email", "telefono", "preferencias", "estado");
    private final AuditEventRepository repository;
    private final UserRepository users;
    private final AuditMapper mapper;

    public AuditService(AuditEventRepository repository, UserRepository users, AuditMapper mapper) {
        this.repository = repository;
        this.users = users;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> history(UUID userId) {
        if (!users.existsById(userId)) {
            throw new UserNotFoundException();
        }
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toResponse).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID userId, AuditAction action, AuditResult result, List<String> detalle,
            String correlationId, String actor) {
        if (!ALLOWED_FIELDS.containsAll(detalle)) {
            throw new IllegalArgumentException("El detalle debe contener solo nombres de campos permitidos");
        }
        repository.save(new AuditEvent(userId, action, result, detalle, correlationId, actor));
    }
}

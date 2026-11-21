package com.bcra.users.ws.audit.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.users.ws.audit.dto.AuditEventResponse;
import com.bcra.users.ws.audit.service.AuditService;

@RestController
@RequestMapping("/api/v1/users/{id}/audit")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEventResponse> history(@PathVariable UUID id) {
        return auditService.history(id);
    }
}

package com.wa.banking.channel.service;

import com.wa.banking.channel.entity.AuditAction;
import com.wa.banking.channel.entity.InteractionHistoryEntity;
import com.wa.banking.channel.entity.InteractionType;
import com.wa.banking.channel.entity.LinkAuditEntryEntity;
import com.wa.banking.channel.repository.InteractionHistoryRepository;
import com.wa.banking.channel.repository.LinkAuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Servicio auxiliar de auditoría e historial de soporte (RN8).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String SYSTEM_ACTOR = "SYSTEM";

    private final LinkAuditEntryRepository auditRepository;

    private final InteractionHistoryRepository interactionHistoryRepository;

    public void recordAudit(String linkId, AuditAction action, String result, String details) {
        auditRepository.save(LinkAuditEntryEntity.builder()
                .linkId(linkId)
                .action(action)
                .actor(SYSTEM_ACTOR)
                .result(result)
                .details(details)
                .build());
    }

    public void recordInteraction(String linkId, InteractionType type, String result, String summary) {
        interactionHistoryRepository.save(InteractionHistoryEntity.builder()
                .linkId(linkId)
                .type(type)
                .result(result)
                .summary(summary)
                .build());
    }
}

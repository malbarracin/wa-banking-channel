package com.wa.banking.channel.repository;

import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio MongoDB para vínculos WhatsApp ↔ cliente bancario.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface WhatsAppLinkRepository extends MongoRepository<WhatsAppLinkEntity, String> {

    Optional<WhatsAppLinkEntity> findByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndStatus(String phoneNumber, LinkStatus status);

    List<WhatsAppLinkEntity> findAllByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);
}

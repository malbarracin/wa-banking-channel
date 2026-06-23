package com.wa.banking.channel.repository;

import com.wa.banking.channel.entity.InteractionHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Repositorio del historial resumido de interacciones para soporte.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface InteractionHistoryRepository extends MongoRepository<InteractionHistoryEntity, String> {

    Page<InteractionHistoryEntity> findByLinkIdOrderByOccurredAtDesc(String linkId, Pageable pageable);
}

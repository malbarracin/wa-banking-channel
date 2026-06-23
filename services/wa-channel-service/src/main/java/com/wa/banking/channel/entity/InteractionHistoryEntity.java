package com.wa.banking.channel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Historial resumido de interacciones para soporte operativo (sin datos legales sensibles).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "link_interactions")
public class InteractionHistoryEntity {

    @Id
    private String id;

    @Indexed
    private String linkId;

    private InteractionType type;

    private String result;

    private String summary;

    @CreatedDate
    private Instant occurredAt;
}

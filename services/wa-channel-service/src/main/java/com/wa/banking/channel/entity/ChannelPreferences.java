package com.wa.banking.channel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Preferencias del canal WhatsApp embebidas en el vínculo.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelPreferences {

    @Builder.Default
    private String language = "es";

    @Builder.Default
    private boolean notificationsEnabled = true;

    private String quietHoursStart;

    private String quietHoursEnd;
}

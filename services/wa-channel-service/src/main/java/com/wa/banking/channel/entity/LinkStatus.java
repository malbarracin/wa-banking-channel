package com.wa.banking.channel.entity;

/**
 * Estados operativos del vínculo WhatsApp ↔ cliente bancario.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public enum LinkStatus {

    NO_LINK,
    PENDING_VERIFICATION,
    VERIFICATION_FAILED,
    ACTIVE,
    BLOCKED,
    UNLINKED
}

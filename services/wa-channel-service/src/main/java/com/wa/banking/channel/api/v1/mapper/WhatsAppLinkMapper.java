package com.wa.banking.channel.api.v1.mapper;

import com.wa.banking.channel.api.v1.dto.InteractionHistoryItemV1;
import com.wa.banking.channel.api.v1.dto.LinkResponseV1;
import com.wa.banking.channel.api.v1.dto.PreferencesResponseV1;
import com.wa.banking.channel.entity.ChannelPreferences;
import com.wa.banking.channel.entity.InteractionHistoryEntity;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper MapStruct entre entidades de vínculo y DTOs v1.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
@Mapper(componentModel = "spring")
public interface WhatsAppLinkMapper {

    LinkResponseV1 toResponse(WhatsAppLinkEntity entity);

    PreferencesResponseV1 toPreferencesResponse(ChannelPreferences preferences);

    InteractionHistoryItemV1 toHistoryItem(InteractionHistoryEntity entity);

    List<InteractionHistoryItemV1> toHistoryItems(List<InteractionHistoryEntity> entities);
}

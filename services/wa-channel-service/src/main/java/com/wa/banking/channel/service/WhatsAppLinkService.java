package com.wa.banking.channel.service;

import com.wa.banking.channel.api.v1.dto.AcceptTermsRequestV1;
import com.wa.banking.channel.api.v1.dto.BlockLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InitiateLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InteractionHistoryItemV1;
import com.wa.banking.channel.api.v1.dto.LinkResponseV1;
import com.wa.banking.channel.api.v1.dto.PreferencesRequestV1;
import com.wa.banking.channel.api.v1.dto.PreferencesResponseV1;
import com.wa.banking.channel.api.v1.dto.ProfileResponseV1;
import com.wa.banking.channel.api.v1.dto.RelinkRequestV1;
import com.wa.banking.channel.api.v1.dto.UnlinkRequestV1;
import com.wa.banking.channel.api.v1.dto.VerifyIdentityRequestV1;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Contrato del servicio de vínculo de canal WhatsApp (F1–F4).
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public interface WhatsAppLinkService {

    LinkResponseV1 findByPhone(String phone);

    LinkResponseV1 initiateLink(InitiateLinkRequestV1 request);

    LinkResponseV1 acceptTerms(String linkId, AcceptTermsRequestV1 request);

    LinkResponseV1 verifyIdentity(String linkId, VerifyIdentityRequestV1 request);

    LinkResponseV1 completeOnboarding(String linkId);

    LinkResponseV1 findById(String linkId);

    ProfileResponseV1 getProfile(String linkId);

    PreferencesResponseV1 getPreferences(String linkId);

    PreferencesResponseV1 updatePreferences(String linkId, PreferencesRequestV1 request);

    LinkResponseV1 block(String linkId, BlockLinkRequestV1 request);

    LinkResponseV1 unlink(String linkId, UnlinkRequestV1 request);

    LinkResponseV1 relink(String linkId, RelinkRequestV1 request);

    Page<InteractionHistoryItemV1> getHistory(String linkId, Pageable pageable);
}

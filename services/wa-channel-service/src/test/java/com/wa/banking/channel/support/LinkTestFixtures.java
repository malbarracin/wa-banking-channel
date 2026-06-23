package com.wa.banking.channel.support;

import com.wa.banking.channel.api.v1.dto.AcceptTermsRequestV1;
import com.wa.banking.channel.api.v1.dto.BlockLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.InitiateLinkRequestV1;
import com.wa.banking.channel.api.v1.dto.RelinkRequestV1;
import com.wa.banking.channel.api.v1.dto.UnlinkRequestV1;
import com.wa.banking.channel.api.v1.dto.VerifyIdentityRequestV1;
import com.wa.banking.channel.entity.ChannelPreferences;
import com.wa.banking.channel.entity.DocumentType;
import com.wa.banking.channel.entity.LinkStatus;
import com.wa.banking.channel.entity.WhatsAppLinkEntity;
import com.wa.banking.channel.integration.session.SessionCredentialResponse;
import com.wa.banking.channel.integration.users.UserResponseV1;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Datos de prueba reutilizables para tests del dominio canal WhatsApp.
 */
public final class LinkTestFixtures {

    public static final String PHONE = "+541112345678";

    public static final String LINK_ID = "665f1a2b3c4d5e6f7a8b9c0d";

    public static final String BANK_USER_ID = "user-bank-001";

    public static final String CREDENTIAL_ID = "cred-session-001";

    public static final String OTP_VALID = "123456";

    private LinkTestFixtures() {
    }

    public static InitiateLinkRequestV1 initiateRequest() {
        return InitiateLinkRequestV1.builder()
                .phoneNumber(PHONE)
                .build();
    }

    public static AcceptTermsRequestV1 acceptTermsRequest() {
        return AcceptTermsRequestV1.builder()
                .termsAccepted(true)
                .build();
    }

    public static VerifyIdentityRequestV1 verifyRequest() {
        return VerifyIdentityRequestV1.builder()
                .documentType(DocumentType.DNI)
                .documentNumber("12345678")
                .otpCode(OTP_VALID)
                .build();
    }

    public static BlockLinkRequestV1 blockRequest() {
        return BlockLinkRequestV1.builder()
                .confirmed(true)
                .build();
    }

    public static UnlinkRequestV1 unlinkRequest() {
        return UnlinkRequestV1.builder()
                .confirmed(true)
                .build();
    }

    public static RelinkRequestV1 relinkRequest() {
        return RelinkRequestV1.builder()
                .confirmed(true)
                .build();
    }

    public static WhatsAppLinkEntity linkEntity(LinkStatus status) {
        return WhatsAppLinkEntity.builder()
                .id(LINK_ID)
                .phoneNumber(PHONE)
                .status(status)
                .preferences(ChannelPreferences.builder().language("es").build())
                .build();
    }

    public static WhatsAppLinkEntity activeLink() {
        return WhatsAppLinkEntity.builder()
                .id(LINK_ID)
                .phoneNumber(PHONE)
                .bankUserId(BANK_USER_ID)
                .status(LinkStatus.ACTIVE)
                .identityVerified(true)
                .sessionCredentialId(CREDENTIAL_ID)
                .preferences(ChannelPreferences.builder().language("es").build())
                .build();
    }

    public static UserResponseV1 bankUser() {
        return UserResponseV1.builder()
                .id(BANK_USER_ID)
                .displayName("María García")
                .email("maria.garcia@example.com")
                .phone("+5491112345678")
                .canLinkChannel(true)
                .build();
    }

    public static SessionCredentialResponse sessionCredential() {
        return SessionCredentialResponse.builder()
                .credentialId(CREDENTIAL_ID)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();
    }
}

package com.wa.banking.channel.config;

/**
 * Ejemplos JSON reutilizables para documentación OpenAPI/Swagger del canal WhatsApp.
 *
 * @author licius-it
 * @since 2026-06-23
 * @contact marceloalejandro.albarracin@gmail.com
 */
public final class OpenApiExamples {

    public static final String INITIATE_LINK_REQUEST = """
            {
              "phoneNumber": "+541112345678"
            }
            """;

    public static final String ACCEPT_TERMS_REQUEST = """
            {
              "termsAccepted": true
            }
            """;

    public static final String VERIFY_IDENTITY_REQUEST = """
            {
              "documentType": "DNI",
              "documentNumber": "12345678",
              "otpCode": "123456"
            }
            """;

    public static final String PREFERENCES_REQUEST = """
            {
              "language": "es",
              "notificationsEnabled": true,
              "quietHoursStart": "22:00",
              "quietHoursEnd": "08:00"
            }
            """;

    public static final String BLOCK_LINK_REQUEST = """
            {
              "confirmed": true
            }
            """;

    public static final String UNLINK_REQUEST = """
            {
              "confirmed": true
            }
            """;

    public static final String RELINK_REQUEST = """
            {
              "confirmed": true
            }
            """;

    public static final String LINK_NO_LINK_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "phoneNumber": "+541112345678",
              "bankUserId": null,
              "status": "NO_LINK",
              "identityVerified": false,
              "termsAcceptedAt": null,
              "verificationAttempts": 0,
              "verificationBlockedUntil": null,
              "createdAt": "2026-06-23T10:00:00Z",
              "updatedAt": "2026-06-23T10:00:00Z"
            }
            """;

    public static final String LINK_PENDING_VERIFICATION_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "phoneNumber": "+541112345678",
              "bankUserId": null,
              "status": "PENDING_VERIFICATION",
              "identityVerified": false,
              "termsAcceptedAt": "2026-06-23T10:05:00Z",
              "verificationAttempts": 0,
              "verificationBlockedUntil": null,
              "createdAt": "2026-06-23T10:00:00Z",
              "updatedAt": "2026-06-23T10:05:00Z"
            }
            """;

    public static final String LINK_ACTIVE_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "phoneNumber": "+541112345678",
              "bankUserId": "user-bank-001",
              "status": "ACTIVE",
              "identityVerified": true,
              "termsAcceptedAt": "2026-06-23T10:05:00Z",
              "verificationAttempts": 0,
              "verificationBlockedUntil": null,
              "createdAt": "2026-06-23T10:00:00Z",
              "updatedAt": "2026-06-23T10:15:00Z"
            }
            """;

    public static final String LINK_BLOCKED_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "phoneNumber": "+541112345678",
              "bankUserId": "user-bank-001",
              "status": "BLOCKED",
              "identityVerified": true,
              "termsAcceptedAt": "2026-06-23T10:05:00Z",
              "verificationAttempts": 0,
              "verificationBlockedUntil": null,
              "createdAt": "2026-06-23T10:00:00Z",
              "updatedAt": "2026-06-23T11:00:00Z"
            }
            """;

    public static final String PROFILE_RESPONSE = """
            {
              "linkId": "665f1a2b3c4d5e6f7a8b9c0d",
              "displayName": "María García",
              "maskedEmail": "m***@example.com",
              "maskedPhone": "+5411****5678",
              "language": "es"
            }
            """;

    public static final String PREFERENCES_RESPONSE = """
            {
              "language": "es",
              "notificationsEnabled": true,
              "quietHoursStart": "22:00",
              "quietHoursEnd": "08:00"
            }
            """;

    public static final String HISTORY_PAGE_RESPONSE = """
            {
              "content": [
                {
                  "id": "hist-001",
                  "type": "VERIFICATION",
                  "result": "SUCCESS",
                  "summary": "Identity verified",
                  "occurredAt": "2026-06-23T10:10:00Z"
                }
              ],
              "pageable": {
                "pageNumber": 0,
                "pageSize": 20
              },
              "totalElements": 1,
              "totalPages": 1,
              "last": true,
              "first": true,
              "size": 20,
              "number": 0,
              "numberOfElements": 1,
              "empty": false
            }
            """;

    public static final String VALIDATION_ERROR = """
            {
              "code": "VALIDATION_ERROR",
              "message": "Validation failed",
              "details": [
                "phoneNumber: phoneNumber is required"
              ],
              "timestamp": "2026-06-23T10:30:00Z"
            }
            """;

    public static final String DUPLICATE_LINK_ERROR = """
            {
              "code": "BAD_REQUEST",
              "message": "Phone number +541112345678 is already linked to an active client",
              "details": [],
              "timestamp": "2026-06-23T10:30:00Z"
            }
            """;

    public static final String NOT_FOUND_ERROR = """
            {
              "code": "NOT_FOUND",
              "message": "Channel link not found with id +541112345678",
              "details": [],
              "timestamp": "2026-06-23T10:30:00Z"
            }
            """;

    public static final String INVALID_STATE_ERROR = """
            {
              "code": "BAD_REQUEST",
              "message": "Cannot perform accept-terms when link status is ACTIVE",
              "details": [],
              "timestamp": "2026-06-23T10:30:00Z"
            }
            """;

    public static final String INTERNAL_ERROR = """
            {
              "code": "INTERNAL_ERROR",
              "message": "An unexpected error occurred",
              "details": [],
              "timestamp": "2026-06-23T10:30:00Z"
            }
            """;

    private OpenApiExamples() {
    }
}

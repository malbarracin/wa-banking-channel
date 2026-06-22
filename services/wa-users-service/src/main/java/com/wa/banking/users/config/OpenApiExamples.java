package com.wa.banking.users.config;

/**
 * Ejemplos JSON reutilizables para documentación OpenAPI/Swagger.
 *
 * @author licius-it
 * @since 2026-06-22
 * @contact marceloalejandro.albarracin@gmail.com
 */
public final class OpenApiExamples {

    public static final String CREATE_USER_REQUEST = """
            {
              "documentType": "DNI",
              "documentNumber": "12345678",
              "displayName": "John Doe",
              "email": "john@example.com",
              "phone": "+541112345678",
              "preferences": { "lang": "es" }
            }
            """;

    public static final String USER_CREATED_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "documentType": "DNI",
              "documentNumber": "12345678",
              "displayName": "John Doe",
              "email": "john@example.com",
              "phone": "+541112345678",
              "preferences": { "lang": "es" },
              "status": "ACTIVE",
              "canLinkChannel": true,
              "createdAt": "2026-06-22T10:30:00Z",
              "updatedAt": "2026-06-22T10:30:00Z"
            }
            """;

    public static final String UPDATE_USER_REQUEST = """
            {
              "displayName": "Jane Doe",
              "email": "jane@example.com",
              "phone": "+541198765432",
              "preferences": { "lang": "es", "notifications": true }
            }
            """;

    public static final String USER_UPDATED_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "documentType": "DNI",
              "documentNumber": "12345678",
              "displayName": "Jane Doe",
              "email": "jane@example.com",
              "phone": "+541198765432",
              "preferences": { "lang": "es", "notifications": true },
              "status": "ACTIVE",
              "canLinkChannel": true,
              "createdAt": "2026-06-22T10:30:00Z",
              "updatedAt": "2026-06-22T11:15:00Z"
            }
            """;

    public static final String CHANGE_STATUS_SUSPENDED_REQUEST = """
            {
              "status": "SUSPENDED"
            }
            """;

    public static final String CHANGE_STATUS_SOFT_DELETED_REQUEST = """
            {
              "status": "SOFT_DELETED"
            }
            """;

    public static final String USER_SUSPENDED_RESPONSE = """
            {
              "id": "665f1a2b3c4d5e6f7a8b9c0d",
              "documentType": "DNI",
              "documentNumber": "12345678",
              "displayName": "John Doe",
              "email": "john@example.com",
              "phone": "+541112345678",
              "preferences": { "lang": "es" },
              "status": "SUSPENDED",
              "canLinkChannel": false,
              "createdAt": "2026-06-22T10:30:00Z",
              "updatedAt": "2026-06-22T12:00:00Z"
            }
            """;

    public static final String AUDIT_PAGE_RESPONSE = """
            {
              "content": [
                {
                  "id": "audit-002",
                  "userId": "665f1a2b3c4d5e6f7a8b9c0d",
                  "action": "STATUS_CHANGED",
                  "previousStatus": "ACTIVE",
                  "newStatus": "SUSPENDED",
                  "changedFields": [],
                  "performedAt": "2026-06-22T12:00:00Z",
                  "result": "SUCCESS"
                },
                {
                  "id": "audit-001",
                  "userId": "665f1a2b3c4d5e6f7a8b9c0d",
                  "action": "CREATED",
                  "previousStatus": null,
                  "newStatus": "ACTIVE",
                  "changedFields": [],
                  "performedAt": "2026-06-22T10:30:00Z",
                  "result": "SUCCESS"
                }
              ],
              "pageable": {
                "pageNumber": 0,
                "pageSize": 20
              },
              "totalElements": 2,
              "totalPages": 1,
              "last": true,
              "first": true,
              "size": 20,
              "number": 0,
              "numberOfElements": 2,
              "empty": false
            }
            """;

    public static final String VALIDATION_ERROR = """
            {
              "code": "VALIDATION_ERROR",
              "message": "Validation failed",
              "details": [
                "documentNumber: documentNumber is required",
                "displayName: displayName is required"
              ],
              "timestamp": "2026-06-22T10:30:00Z"
            }
            """;

    public static final String DUPLICATE_DOCUMENT_ERROR = """
            {
              "code": "BAD_REQUEST",
              "message": "User already exists with document DNI 12345678",
              "details": [],
              "timestamp": "2026-06-22T10:30:00Z"
            }
            """;

    public static final String NOT_FOUND_ERROR = """
            {
              "code": "NOT_FOUND",
              "message": "User not found with id 665f1a2b3c4d5e6f7a8b9c0d",
              "details": [],
              "timestamp": "2026-06-22T10:30:00Z"
            }
            """;

    public static final String INVALID_TRANSITION_ERROR = """
            {
              "code": "BAD_REQUEST",
              "message": "Transition from SOFT_DELETED to ACTIVE is not allowed",
              "details": [],
              "timestamp": "2026-06-22T10:30:00Z"
            }
            """;

    public static final String INTERNAL_ERROR = """
            {
              "code": "INTERNAL_ERROR",
              "message": "An unexpected error occurred",
              "details": [],
              "timestamp": "2026-06-22T10:30:00Z"
            }
            """;

    private OpenApiExamples() {
    }
}

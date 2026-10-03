package com.bank.users.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bank.users.user.dto.CreateUserRequest;
import com.bank.users.user.dto.EligibilityResponse;
import com.bank.users.user.dto.UpdateUserRequest;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * API REST del Servicio de Usuarios ({@code /api/v1/users}).
 *
 * <p>El alta crea clientes {@code ACTIVE} sin duplicar documentos; la actualización solo admite
 * {@code displayName} y {@code email}. Las consultas no requieren JWT en H1, no modifican datos y devuelven la misma respuesta para
 * todos los actores. Los errores siguen el contrato uniforme ({@code GlobalExceptionHandler}).</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Da de alta un cliente.
     *
     * @param request {@code documentType}, {@code documentNumber}, {@code firstName}, {@code lastName} y
     *                {@code birthDate} obligatorios; {@code displayName} y {@code email} opcionales (email
     *                con formato válido si se informa)
     * @return 201 con {@link UserResponse} en estado {@code ACTIVE}; 400 {@code VALIDATION_ERROR} /
     *         {@code BAD_REQUEST} si la solicitud es inválida; 409 {@code CONFLICT} si el documento ya existe
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request);
    }

    /**
     * Consulta un usuario por id interno, en cualquier estado.
     *
     * @param id identificador interno (numérico; otro formato → 400 {@code BAD_REQUEST})
     * @return 200 con {@link UserResponse}; 404 {@code NOT_FOUND} si no existe
     */
    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable Long id) {
        return userService.findById(id);
    }

    /**
     * Consulta un usuario por documento compuesto, en cualquier estado.
     *
     * @param documentType   tipo de documento (obligatorio; ausente → 400 {@code VALIDATION_ERROR},
     *                       valor no admitido → 400 {@code BAD_REQUEST})
     * @param documentNumber número de documento (obligatorio, no blanco → si no, 400 {@code VALIDATION_ERROR})
     * @return 200 con {@link UserResponse}; 404 {@code NOT_FOUND} si no existe
     */
    @GetMapping
    public UserResponse getByDocument(@RequestParam DocumentType documentType,
            @RequestParam @NotBlank String documentNumber) {
        return userService.findByDocument(documentType, documentNumber);
    }

    /**
     * Actualiza los datos permitidos de un usuario ({@code ACTIVE} o {@code SUSPENDED}).
     *
     * @param id      identificador interno (numérico; otro formato → 400 {@code BAD_REQUEST})
     * @param request {@code displayName} y/o {@code email} (al menos uno; email con formato válido)
     * @return 200 con {@link UserResponse} actualizado; 400 {@code VALIDATION_ERROR} si el payload está
     *         vacío o el email es inválido; 400 {@code BAD_REQUEST} «campo no modificable por este
     *         servicio» si incluye cualquier otro campo; 404 {@code NOT_FOUND} si no existe
     */
    @PatchMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    /**
     * Indica si el usuario puede iniciar un nuevo vínculo de canal ({@code eligible} solo si {@code ACTIVE}).
     *
     * @param id identificador interno
     * @return 200 con {@code userId} y {@code eligible}; 404 {@code NOT_FOUND} si no existe
     */
    @GetMapping("/{id}/eligible")
    public EligibilityResponse getEligibility(@PathVariable Long id) {
        return userService.checkEligibility(id);
    }
}

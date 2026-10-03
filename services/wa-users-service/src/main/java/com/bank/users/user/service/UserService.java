package com.bank.users.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bank.users.exception.UserNotFoundException;
import com.bank.users.user.dto.EligibilityResponse;
import com.bank.users.user.dto.UserResponse;
import com.bank.users.user.entity.DocumentType;
import com.bank.users.user.entity.User;
import com.bank.users.user.entity.UserStatus;
import com.bank.users.user.mapper.UserMapper;
import com.bank.users.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Casos de uso del ciclo de vida de usuarios.
 *
 * <p>Las consultas son de solo lectura: no modifican datos ni estado, devuelven la misma proyección
 * para todos los actores H1 y no exponen auditoría, historial ni datos de canal.</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Consulta un usuario por su identificador interno, en cualquier estado.
     *
     * @param id identificador interno
     * @return proyección pública del usuario
     * @throws UserNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return userMapper.toResponse(getUser(id));
    }

    /**
     * Consulta un usuario por su documento compuesto (tipo + número), en cualquier estado.
     *
     * @param documentType   tipo de documento
     * @param documentNumber número de documento
     * @return proyección pública del usuario
     * @throws UserNotFoundException si no existe (el mensaje no incluye el documento)
     */
    @Transactional(readOnly = true)
    public UserResponse findByDocument(DocumentType documentType, String documentNumber) {
        return userRepository.findByDocumentTypeAndDocumentNumber(documentType, documentNumber)
                .map(userMapper::toResponse)
                .orElseThrow(UserNotFoundException::byDocument);
    }

    /**
     * Indica si el usuario puede iniciar un nuevo vínculo de canal: solo los usuarios {@code ACTIVE}
     * son elegibles; {@code SUSPENDED} e {@code INACTIVE} no lo son.
     *
     * @param id identificador interno
     * @return {@code userId} y {@code eligible}
     * @throws UserNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public EligibilityResponse checkEligibility(Long id) {
        User user = getUser(id);
        return new EligibilityResponse(user.getId(), user.getStatus() == UserStatus.ACTIVE);
    }

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> UserNotFoundException.byId(id));
    }
}

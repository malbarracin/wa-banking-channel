package com.bcra.users.ws.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bcra.users.ws.user.entity.User;
import com.bcra.users.ws.user.exception.UserNotFoundException;
import com.bcra.users.ws.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/** Casos de uso de consulta de usuarios bancarios por documento. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repository;

    /**
     * Consulta por ambos componentes del documento, incluyendo usuarios no activos.
     * @return usuario encontrado
     * @throws UserNotFoundException cuando no existe el documento solicitado
     */
    @Transactional(readOnly = true)
    public User consultar(String tipo, String numero) {
        return repository.findByDocumentoTipoAndDocumentoNumero(tipo, numero)
                .orElseThrow(UserNotFoundException::new);
    }
}

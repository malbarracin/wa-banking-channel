package com.bcra.accounts.ws.cuenta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bcra.accounts.ws.cuenta.client.CredencialesServiceClient;
import com.bcra.accounts.ws.cuenta.mapper.CuentaMapper;
import com.bcra.accounts.ws.cuenta.repository.CuentaRepository;
import com.bcra.accounts.ws.cuenta.service.AuditoriaCuentaService;
import com.bcra.accounts.ws.cuenta.service.CuentaService;
import com.bcra.accounts.ws.exception.UnauthorizedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    @Mock
    private CredencialesServiceClient credencialesServiceClient;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private CuentaMapper cuentaMapper;

    @Mock
    private AuditoriaCuentaService auditoriaCuentaService;

    @InjectMocks
    private CuentaService cuentaService;

    @Test
    @DisplayName("Una sesión revocada no permite consultar productos ni atribuir auditoría a una identidad sin validar")
    void rechazaSesionAntesDeConsultarProductos() {
        when(credencialesServiceClient.validar("revocada"))
                .thenThrow(new UnauthorizedException("Credencial no válida"));

        assertThatThrownBy(() -> cuentaService.listar("revocada")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> cuentaService.consultarSaldo("revocada", "propia"))
                .isInstanceOf(UnauthorizedException.class);

        verifyNoInteractions(cuentaRepository, cuentaMapper, auditoriaCuentaService);
    }
}

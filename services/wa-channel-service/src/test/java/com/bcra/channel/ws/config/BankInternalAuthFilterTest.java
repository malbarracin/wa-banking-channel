package com.bcra.channel.ws.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import com.bcra.channel.ws.config.RequireInternalRole.InternalRole;
import com.bcra.channel.ws.exception.ForbiddenException;

class BankInternalAuthFilterTest {

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    private BankInternalAuthFilter filter;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        BankChannelProperties properties = new BankChannelProperties();
        properties.getInternalRoles().setSupport("soporte-banco");
        properties.getInternalRoles().setBankPolicy("politica-banco");
        filter = new BankInternalAuthFilter(properties);
        request = new MockHttpServletRequest();
    }

    @Test
    @DisplayName("Usa los valores configurados para cada rol interno")
    void resolvesConfiguredRoles() {
        assertThat(filter.resolveExpectedRole(InternalRole.SUPPORT)).isEqualTo("soporte-banco");
        assertThat(filter.resolveExpectedRole(InternalRole.BANK_POLICY)).isEqualTo("politica-banco");
    }

    @Test
    @DisplayName("El rol de política del banco correcto deja pasar")
    void bankPolicyRoleAllowed() throws Exception {
        request.addHeader(BankInternalAuthFilter.HEADER_NAME, "politica-banco");

        assertThat(filter.preHandle(request, response, handler(new Controllers(), "bankPolicy"))).isTrue();
    }

    @Test
    @DisplayName("El rol de soporte no autoriza un endpoint de política del banco")
    void supportRoleOnBankPolicyEndpointForbidden() throws Exception {
        request.addHeader(BankInternalAuthFilter.HEADER_NAME, "soporte-banco");
        HandlerMethod handler = handler(new Controllers(), "bankPolicy");

        assertThatThrownBy(() -> filter.preHandle(request, response, handler))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Un header en blanco se trata como ausente")
    void blankHeaderRejected() throws Exception {
        request.addHeader(BankInternalAuthFilter.HEADER_NAME, "   ");
        HandlerMethod handler = handler(new Controllers(), "support");

        assertThatThrownBy(() -> filter.preHandle(request, response, handler))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("La comparación del rol es exacta (distingue mayúsculas)")
    void roleComparisonIsExact() throws Exception {
        request.addHeader(BankInternalAuthFilter.HEADER_NAME, "SOPORTE-BANCO");
        HandlerMethod handler = handler(new Controllers(), "support");

        assertThatThrownBy(() -> filter.preHandle(request, response, handler))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("Un endpoint sin anotación no exige el header")
    void unannotatedEndpointAllowed() throws Exception {
        assertThat(filter.preHandle(request, response, handler(new Controllers(), "open"))).isTrue();
    }

    @Test
    @DisplayName("La anotación a nivel de clase también se aplica")
    void classLevelAnnotationApplied() throws Exception {
        HandlerMethod handler = handler(new SupportController(), "anything");

        assertThatThrownBy(() -> filter.preHandle(request, response, handler))
                .isInstanceOf(IllegalArgumentException.class);

        request.addHeader(BankInternalAuthFilter.HEADER_NAME, "soporte-banco");
        assertThat(filter.preHandle(request, response, handler)).isTrue();
    }

    @Test
    @DisplayName("Los handlers que no son métodos de controlador pasan sin control")
    void nonHandlerMethodAllowed() {
        assertThat(filter.preHandle(request, response, new Object())).isTrue();
    }

    private static HandlerMethod handler(Object bean, String methodName) throws NoSuchMethodException {
        Method method = bean.getClass().getDeclaredMethod(methodName);
        return new HandlerMethod(bean, method);
    }

    static class Controllers {

        @RequireInternalRole(InternalRole.SUPPORT)
        void support() {
        }

        @RequireInternalRole(InternalRole.BANK_POLICY)
        void bankPolicy() {
        }

        void open() {
        }
    }

    @RequireInternalRole(InternalRole.SUPPORT)
    static class SupportController {

        void anything() {
        }
    }
}

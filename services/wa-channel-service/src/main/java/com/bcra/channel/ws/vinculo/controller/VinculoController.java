package com.bcra.channel.ws.vinculo.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bcra.channel.ws.exception.ErrorResponse;
import com.bcra.channel.ws.vinculo.CredencialCanal;
import com.bcra.channel.ws.vinculo.CredencialHeaders;
import com.bcra.channel.ws.vinculo.dto.AceptarTerminosRequest;
import com.bcra.channel.ws.vinculo.dto.ActualizarPreferenciasRequest;
import com.bcra.channel.ws.vinculo.dto.BloquearVinculoRequest;
import com.bcra.channel.ws.vinculo.dto.DesvincularRequest;
import com.bcra.channel.ws.vinculo.dto.HistorialVinculoResponse;
import com.bcra.channel.ws.vinculo.dto.PerfilCanalResponse;
import com.bcra.channel.ws.vinculo.dto.VerificacionResultadoResponse;
import com.bcra.channel.ws.vinculo.dto.VerificarIdentidadRequest;
import com.bcra.channel.ws.vinculo.dto.VinculoEstadoResponse;
import com.bcra.channel.ws.vinculo.dto.VinculoResponse;
import com.bcra.channel.ws.vinculo.service.VerificacionService;
import com.bcra.channel.ws.vinculo.service.VinculoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** API del vínculo al canal WhatsApp. */
@RestController
@RequestMapping("/api/v1/vinculos")
@RequiredArgsConstructor
@Tag(name = "Vínculos", description = "Ciclo de vida del vínculo entre un número de WhatsApp y un cliente de H1")
public class VinculoController {

    private static final String VINCULO_PENDIENTE_EXAMPLE = """
            {
              "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "estado": "PENDIENTE_VERIFICACION",
              "fechaActivacion": null,
              "preferencias": {
                "idioma": null,
                "notificacionesHabilitadas": false,
                "horarioPreferido": null
              }
            }
            """;

    private static final String VINCULO_ACTIVO_EXAMPLE = """
            {
              "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "estado": "ACTIVO",
              "fechaActivacion": "2026-10-26T12:00:00Z",
              "preferencias": {
                "idioma": "es",
                "notificacionesHabilitadas": true,
                "horarioPreferido": "09:00-18:00"
              }
            }
            """;

    private static final String VINCULO_BLOQUEADO_EXAMPLE = """
            {
              "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "estado": "BLOQUEADO",
              "fechaActivacion": "2026-10-26T12:00:00Z",
              "preferencias": {
                "idioma": "es",
                "notificacionesHabilitadas": true,
                "horarioPreferido": "09:00-18:00"
              }
            }
            """;

    private static final String VINCULO_DESVINCULADO_EXAMPLE = """
            {
              "id": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "estado": "DESVINCULADO",
              "fechaActivacion": "2026-10-26T12:00:00Z",
              "preferencias": {
                "idioma": "es",
                "notificacionesHabilitadas": true,
                "horarioPreferido": "09:00-18:00"
              }
            }
            """;

    private static final String VERIFICACION_EXITOSA_EXAMPLE = """
            {
              "exitosa": true,
              "vinculoId": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "documentoTipo": "DNI",
              "documentoNumero": "30111222",
              "estadoVinculo": "ACTIVO",
              "intentosRestantes": null,
              "verificadoEn": "2026-10-26T12:00:00Z"
            }
            """;

    private static final String VERIFICACION_FALLIDA_EXAMPLE = """
            {
              "exitosa": false,
              "vinculoId": "3f2b6c1e-8a4d-4f7b-9c2e-1d5a7b8c9e01",
              "numeroWhatsapp": "+5491112345678",
              "documentoTipo": "DNI",
              "documentoNumero": "30111222",
              "estadoVinculo": "PENDIENTE_VERIFICACION",
              "intentosRestantes": 2,
              "verificadoEn": null
            }
            """;

    private static final String HISTORIAL_EXAMPLE = """
            {
              "numeroWhatsapp": "+5491112345678",
              "documentoTipo": "DNI",
              "documentoNumero": "30111222",
              "estadoActual": "ACTIVO",
              "eventos": [
                { "accion": "VINCULACION", "resultado": "EXITOSO", "fecha": "2026-10-26T12:01:00Z" },
                { "accion": "VERIFICACION_EXITOSA", "resultado": "EXITOSO", "fecha": "2026-10-26T12:00:00Z" },
                { "accion": "ACEPTACION_TERMINOS", "resultado": "EXITOSO", "fecha": "2026-10-26T11:58:00Z" }
              ]
            }
            """;

    private final VinculoService vinculoService;
    private final VerificacionService verificacionService;

    /**
     * Verifica identidad después de aceptar términos.
     *
     * @param numero número E.164
     * @param request pruebas de identidad
     * @return resultado de la verificación, sin credencial de sesión
     */
    @PostMapping("/{numero}/verificaciones")
    @Operation(
            summary = "Verificar identidad y vincular o re-vincular",
            description = """
                    Requiere un vínculo PENDIENTE_VERIFICACION creado antes con POST /terminos y que el \
                    cliente exista y esté ACTIVE en H1. Cuenta los fallos de la ventana vigente \
                    (vinculo.verificacion.max-intentos / ventana-minutos): al agotarlos responde 423 y \
                    deriva a soporte. Una re-vinculación (vínculo previo DESVINCULADO o BLOQUEADO) exige \
                    el proceso completo y no reutiliza intentos ni credenciales. El servicio por defecto \
                    rechaza toda prueba hasta que Compliance entregue el método de verificación; en ese \
                    caso responde 200 con exitosa=false. No emite credenciales: el resultado es para H0.2.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Intento procesado (éxito o fallo con intentos restantes)",
                    content = @Content(schema = @Schema(implementation = VerificacionResultadoResponse.class),
                            examples = {
                                    @ExampleObject(name = "Exitosa", value = VERIFICACION_EXITOSA_EXAMPLE),
                                    @ExampleObject(name = "Fallida", value = VERIFICACION_FALLIDA_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "400", description = "Número o body inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError",
                                            ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "404", description = "Sin términos aceptados o cliente inexistente en H1",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "NotFoundTerminos",
                                            ref = "#/components/examples/NotFoundTerminos"),
                                    @ExampleObject(name = "NotFoundCliente",
                                            ref = "#/components/examples/NotFoundCliente")
                            })),
            @ApiResponse(responseCode = "409", description = "Cliente no activo en H1 o número ya vinculado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ConflictClienteNoHabilitado",
                                            ref = "#/components/examples/ConflictClienteNoHabilitado"),
                                    @ExampleObject(name = "ConflictNumeroVinculado",
                                            ref = "#/components/examples/ConflictNumeroVinculado")
                            })),
            @ApiResponse(responseCode = "423", description = "Intentos agotados en la ventana; derivar a soporte",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Locked"))),
            @ApiResponse(responseCode = "500", description = "Error interno o fallo de H1",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public VerificacionResultadoResponse verificar(
            @Parameter(description = "Número E.164 con prefijo + (en la URL se envía codificado)",
                    example = "+5491112345678") @PathVariable String numero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = VerificarIdentidadRequest.class),
                            examples = {
                                    @ExampleObject(name = "Documento y código",
                                            value = """
                                                    {
                                                      "documentoTipo": "DNI",
                                                      "documentoNumero": "30111222",
                                                      "datosVerificacion": { "codigo": "123456" }
                                                    }
                                                    """),
                                    @ExampleObject(name = "Solo documento",
                                            value = """
                                                    {
                                                      "documentoTipo": "DNI",
                                                      "documentoNumero": "30111222",
                                                      "datosVerificacion": { "fechaNacimiento": "1990-05-15" }
                                                    }
                                                    """)
                            }))
            @Valid @RequestBody VerificarIdentidadRequest request) {
        return verificacionService.verificar(numero, request);
    }

    /**
     * Consulta anti-duplicados: si el número ya tiene vínculo, sin datos del cliente.
     * @param numero número de WhatsApp consultado
     * @return 200 con vinculado true/false; nunca 404
     */
    @GetMapping("/{numero}/estado")
    @Operation(
            summary = "Consultar si el número ya está vinculado",
            description = """
                    Consulta anti-duplicados y fraude: informa si el número tiene un vínculo en cualquier \
                    estado, sin exponer datos del cliente. Un número libre devuelve 200 con vinculado=false; \
                    este endpoint nunca responde 404.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Número vinculado o libre",
                    content = @Content(schema = @Schema(implementation = VinculoEstadoResponse.class),
                            examples = {
                                    @ExampleObject(name = "Vinculado", value = """
                                            {
                                              "numeroWhatsapp": "+5491112345678",
                                              "vinculado": true,
                                              "estado": "PENDIENTE_VERIFICACION"
                                            }
                                            """),
                                    @ExampleObject(name = "Libre", value = """
                                            {
                                              "numeroWhatsapp": "+5491112345678",
                                              "vinculado": false
                                            }
                                            """)
                            })),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public VinculoEstadoResponse consultarEstado(
            @Parameter(description = "Número E.164 consultado", example = "+5491112345678")
            @PathVariable String numero) {
        return vinculoService.consultarEstado(numero);
    }

    /**
     * Perfil permitido del canal para el titular con credencial válida y vínculo activo.
     * @param numero número de WhatsApp
     * @param credencialValida header {@link CredencialCanal#HEADER_CREDENCIAL_VALIDA} propagado aguas arriba
     * @return 200 con datos de H1 + preferencias locales; 401/409 según contrato
     */
    @GetMapping("/{numero}/perfil")
    @Operation(
            summary = "Consultar el perfil permitido del canal",
            description = """
                    Devuelve los datos de contacto de H1 y las preferencias locales del vínculo ACTIVO, \
                    sin datos legales sensibles. Exige el contexto de credencial válida que H2/H0.2 \
                    propagan tras validar la sesión; H3 no valida el token, solo su presencia. Sin \
                    credencial responde 401 para derivar a verificación; sin vínculo ACTIVO responde 409.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil permitido del canal",
                    content = @Content(schema = @Schema(implementation = PerfilCanalResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "nombre": "Ana",
                                      "apellido": "Pérez",
                                      "nombreTratamiento": "Anita",
                                      "email": "ana@example.test",
                                      "telefono": "5551234",
                                      "preferencias": {
                                        "idioma": "es",
                                        "notificacionesHabilitadas": true,
                                        "horarioPreferido": "09:00-18:00"
                                      }
                                    }
                                    """))),
            @ApiResponse(responseCode = "401", description = "Sin contexto de credencial válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "409", description = "No hay vínculo ACTIVO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ConflictNoActivo"))),
            @ApiResponse(responseCode = "500", description = "Error interno o fallo de H1",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public PerfilCanalResponse consultarPerfil(
            @Parameter(description = "Número E.164 del vínculo", example = "+5491112345678")
            @PathVariable String numero,
            @Parameter(description = "Contexto de credencial validado aguas arriba; debe ser true",
                    example = "true")
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.consultarPerfil(numero, credencialValida);
    }

    /**
     * Devuelve el historial resumido para uso interno de soporte del banco.
     *
     * @param numero número de WhatsApp consultado
     * @return 200 con estado actual y eventos descendentes; 404 si no hay vínculo registrado
     */
    @GetMapping("/{numero}/historial")
    @Operation(
            summary = "Consultar historial resumido para soporte",
            description = """
                    Uso interno del banco (soporte), sin contexto de cliente. Devuelve el estado actual del \
                    vínculo más reciente y los eventos de auditoría del número, del más reciente al más \
                    antiguo. No incluye motivos libres ni datos de verificación.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Historial del número",
                    content = @Content(schema = @Schema(implementation = HistorialVinculoResponse.class),
                            examples = @ExampleObject(value = HISTORIAL_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "Número sin vínculo registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFoundVinculo"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public HistorialVinculoResponse consultarHistorial(
            @Parameter(description = "Número E.164 consultado", example = "+5491112345678")
            @PathVariable String numero) {
        return vinculoService.consultarHistorial(numero);
    }

    /**
     * Actualiza preferencias del canal; requiere vínculo ACTIVO y contexto de credencial.
     *
     * @param numero número E.164 del vínculo
     * @param documentoTipo tipo de documento del contexto de credencial
     * @param documentoNumero número de documento del contexto de credencial
     * @param request cambios de preferencias; propiedades desconocidas se rechazan
     * @return 200 con el vínculo y las preferencias actualizadas
     */
    @PatchMapping("/{numero}/preferencias")
    @Operation(
            summary = "Actualizar preferencias del canal",
            description = """
                    Solo idioma, notificacionesHabilitadas y horarioPreferido. Los campos ausentes o null \
                    conservan el valor actual. Exige vínculo ACTIVO y el contexto de credencial \
                    propagado por H2/H0.2 en los headers de documento. Una propiedad fuera del contrato \
                    responde 400 VALIDATION_ERROR sin aplicar cambios.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preferencias actualizadas",
                    content = @Content(schema = @Schema(implementation = VinculoResponse.class),
                            examples = @ExampleObject(value = VINCULO_ACTIVO_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o propiedad desconocida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError",
                                            ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "UnknownProperty",
                                            ref = "#/components/examples/UnknownProperty")
                            })),
            @ApiResponse(responseCode = "401", description = "Sin contexto de credencial válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "404", description = "Número sin vínculo registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFoundVinculo"))),
            @ApiResponse(responseCode = "409", description = "El vínculo no está ACTIVO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ConflictNoActivo"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public VinculoResponse actualizarPreferencias(
            @Parameter(description = "Número E.164 del vínculo", example = "+5491112345678")
            @PathVariable String numero,
            @Parameter(description = "Tipo de documento del contexto de credencial", example = "DNI")
            @RequestHeader(value = CredencialHeaders.DOCUMENTO_TIPO, required = false) String documentoTipo,
            @Parameter(description = "Número de documento del contexto de credencial", example = "30111222")
            @RequestHeader(value = CredencialHeaders.DOCUMENTO_NUMERO, required = false) String documentoNumero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = ActualizarPreferenciasRequest.class),
                            examples = {
                                    @ExampleObject(name = "Idioma y notificaciones",
                                            value = """
                                                    {
                                                      "idioma": "es",
                                                      "notificacionesHabilitadas": true
                                                    }
                                                    """),
                                    @ExampleObject(name = "Solo horario",
                                            value = """
                                                    {
                                                      "horarioPreferido": "09:00-18:00"
                                                    }
                                                    """)
                            }))
            @Valid @RequestBody ActualizarPreferenciasRequest request) {
        return vinculoService.actualizarPreferencias(numero, documentoTipo, documentoNumero, request);
    }

    /**
     * Confirma la aceptación explícita de los términos del canal.
     *
     * @param request número de WhatsApp, sin datos de identificación
     * @return 201 con el vínculo pendiente de verificación
     */
    @PostMapping("/terminos")
    @Operation(
            summary = "Aceptar términos del canal",
            description = """
                    Primer paso obligatorio: registra la aceptación explícita sin pedir datos de \
                    identificación y deja el vínculo en PENDIENTE_VERIFICACION. Cada llamada crea un nuevo \
                    registro pendiente (no es idempotente); la verificación requiere el más reciente.""")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Términos aceptados; vínculo pendiente",
                    content = @Content(schema = @Schema(implementation = VinculoResponse.class),
                            examples = @ExampleObject(value = VINCULO_PENDIENTE_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Número ausente o fuera de E.164",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = {
                                    @ExampleObject(name = "ValidationError",
                                            ref = "#/components/examples/ValidationError"),
                                    @ExampleObject(name = "BadRequest",
                                            ref = "#/components/examples/BadRequest")
                            })),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public ResponseEntity<VinculoResponse> aceptarTerminos(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = AceptarTerminosRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "numeroWhatsapp": "+5491112345678"
                                    }
                                    """)))
            @Valid @RequestBody AceptarTerminosRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(vinculoService.aceptarTerminos(request.numeroWhatsapp()));
    }

    /**
     * Bloquea el vínculo (cliente o política del banco) y solicita revocación a H2.
     * @param numero número de WhatsApp
     * @param request motivo y confirmación
     * @param credencialValida obligatorio si el motivo es solicitud del cliente
     * @return 200 con estado {@code BLOQUEADO}
     */
    @PatchMapping("/{numero}/bloqueo")
    @Operation(
            summary = "Bloquear el vínculo del canal",
            description = """
                    Bloquea un vínculo ACTIVO por solicitud del cliente (SOLICITUD_CLIENTE, exige contexto \
                    de credencial válida) o por política/riesgo del banco (POLITICA_BANCO, llamada interna). \
                    Solicita la revocación de la credencial a H2 de inmediato y de forma best-effort: si H2 \
                    no confirma, el bloqueo local se mantiene y se audita el rechazo de la solicitud.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vínculo bloqueado",
                    content = @Content(schema = @Schema(implementation = VinculoResponse.class),
                            examples = @ExampleObject(value = VINCULO_BLOQUEADO_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Motivo o confirmación inválidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ValidationError"))),
            @ApiResponse(responseCode = "401", description = "Solicitud del cliente sin credencial válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "404", description = "Número sin vínculo registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFoundVinculo"))),
            @ApiResponse(responseCode = "409", description = "El vínculo no está ACTIVO",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ConflictEstado"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public VinculoResponse bloquear(
            @Parameter(description = "Número E.164 del vínculo", example = "+5491112345678")
            @PathVariable String numero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = BloquearVinculoRequest.class),
                            examples = {
                                    @ExampleObject(name = "Solicitud del cliente",
                                            value = """
                                                    {
                                                      "motivo": "SOLICITUD_CLIENTE",
                                                      "confirmacion": true
                                                    }
                                                    """),
                                    @ExampleObject(name = "Política del banco",
                                            value = """
                                                    {
                                                      "motivo": "POLITICA_BANCO",
                                                      "confirmacion": true
                                                    }
                                                    """)
                            }))
            @Valid @RequestBody BloquearVinculoRequest request,
            @Parameter(description = "Contexto de credencial validado aguas arriba; obligatorio si el motivo "
                    + "es SOLICITUD_CLIENTE", example = "true")
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.bloquear(numero, request, credencialValida);
    }

    /**
     * Desvincula el canal a pedido del cliente y solicita revocación a H2.
     * @param numero número de WhatsApp
     * @param request confirmación explícita
     * @param credencialValida contexto de credencial válida obligatorio
     * @return 200 con estado {@code DESVINCULADO}
     */
    @PatchMapping("/{numero}/desvinculacion")
    @Operation(
            summary = "Desvincular el canal a pedido del cliente",
            description = """
                    Desvincula un vínculo ACTIVO o BLOQUEADO con confirmación explícita y contexto de \
                    credencial válida. Solicita la revocación de la credencial a H2 de inmediato y de forma \
                    best-effort: si H2 no confirma, la desvinculación local se mantiene y se audita el \
                    rechazo de la solicitud.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vínculo desvinculado",
                    content = @Content(schema = @Schema(implementation = VinculoResponse.class),
                            examples = @ExampleObject(value = VINCULO_DESVINCULADO_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "Confirmación ausente o false",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ValidationError"))),
            @ApiResponse(responseCode = "401", description = "Sin contexto de credencial válida",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/Unauthorized"))),
            @ApiResponse(responseCode = "404", description = "Número sin vínculo registrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/NotFoundVinculo"))),
            @ApiResponse(responseCode = "409", description = "El vínculo no admite la desvinculación en su estado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/ConflictEstado"))),
            @ApiResponse(responseCode = "500", description = "Error interno",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(ref = "#/components/examples/InternalError")))
    })
    public VinculoResponse desvincular(
            @Parameter(description = "Número E.164 del vínculo", example = "+5491112345678")
            @PathVariable String numero,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(schema = @Schema(implementation = DesvincularRequest.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "confirmacion": true
                                    }
                                    """)))
            @Valid @RequestBody DesvincularRequest request,
            @Parameter(description = "Contexto de credencial validado aguas arriba; debe ser true",
                    example = "true")
            @RequestHeader(value = CredencialCanal.HEADER_CREDENCIAL_VALIDA, required = false)
            String credencialValida) {
        return vinculoService.desvincular(numero, request, credencialValida);
    }
}

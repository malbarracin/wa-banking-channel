package com.bank.users.exception;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce toda excepción del servicio al contrato uniforme {@link ErrorResponse}.
 *
 * <ul>
 *   <li>400 {@code VALIDATION_ERROR}: campos inválidos o faltantes (detalle por campo).</li>
 *   <li>400 {@code BAD_REQUEST}: JSON inválido, enum desconocido, campo no modificable,
 *       transición de estado no permitida.</li>
 *   <li>404 {@code NOT_FOUND}: usuario o recurso inexistente.</li>
 *   <li>409 {@code CONFLICT}: documento duplicado.</li>
 *   <li>500 {@code INTERNAL_ERROR}: fallo inesperado, sin stacktrace ni datos internos.</li>
 * </ul>
 *
 * <p>Ni las respuestas ni los logs incluyen los valores enviados por el cliente.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String VALIDATION_MESSAGE = "La solicitud contiene campos inválidos o faltantes";
    static final String MALFORMED_JSON_MESSAGE = "El cuerpo de la solicitud no es un JSON válido";
    static final String INVALID_VALUE_MESSAGE = "La solicitud contiene valores no admitidos";
    static final String INVALID_PARAMETER_MESSAGE = "Parámetro con tipo o valor no admitido";
    static final String CONFLICT_MESSAGE = "La operación entra en conflicto con datos existentes";
    static final String NOT_FOUND_MESSAGE = "Recurso no encontrado";
    static final String UNSUPPORTED_REQUEST_MESSAGE = "Solicitud no admitida";
    static final String INTERNAL_MESSAGE = "Error interno del servidor";
    static final String INVALID_FORMAT_DETAIL = "formato o tipo de dato inválido";
    static final String ENUM_DETAIL_PREFIX = "valor no admitido; valores permitidos: ";
    static final String REQUIRED_DETAIL = "es obligatorio";

    // ---------------------------------------------------------------- dominio

    /**
     * @param ex usuario inexistente
     * @return 404 NOT_FOUND
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        log.warn("Usuario no encontrado: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ErrorResponse.of(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    /**
     * @param ex documento duplicado
     * @return 409 CONFLICT
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("Alta rechazada por documento duplicado");
        return build(HttpStatus.CONFLICT, ErrorResponse.of(ErrorCode.CONFLICT, ex.getMessage()));
    }

    /**
     * @param ex transición de estado no permitida
     * @return 400 BAD_REQUEST
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStateTransitionException ex) {
        log.warn("Transición de estado rechazada: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ErrorResponse.of(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    /**
     * @param ex campos no modificables en la solicitud
     * @return 400 BAD_REQUEST con un detalle por campo
     */
    @ExceptionHandler(NonModifiableFieldException.class)
    public ResponseEntity<ErrorResponse> handleNonModifiableField(NonModifiableFieldException ex) {
        log.warn("Campos no modificables rechazados: {}", ex.getFields());
        return build(HttpStatus.BAD_REQUEST, nonModifiable(ex.getFields()));
    }

    /**
     * Violación de restricción de BD (p. ej. índice único de documento ante altas concurrentes).
     *
     * @param ex violación de integridad
     * @return 409 CONFLICT sin detalles de la BD
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violación de integridad de datos: {}", ex.getClass().getSimpleName());
        return build(HttpStatus.CONFLICT, ErrorResponse.of(ErrorCode.CONFLICT, CONFLICT_MESSAGE));
    }

    /**
     * Validación fuera del binding MVC (p. ej. {@code @Validated} en servicios).
     *
     * @param ex violaciones de restricciones
     * @return 400 VALIDATION_ERROR con los campos afectados
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.Detail> details = ex.getConstraintViolations().stream()
                .map(v -> new ErrorResponse.Detail(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return validationError(details);
    }

    /**
     * Último recurso: cualquier fallo no previsto.
     *
     * @param ex excepción inesperada
     * @return 500 INTERNAL_ERROR sin stacktrace ni mensaje interno
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Error inesperado procesando la solicitud", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorResponse.of(ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE));
    }

    // ---------------------------------------------------------------- Spring MVC

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.Detail> details = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            details.add(new ErrorResponse.Detail(error.getField(), error.getDefaultMessage()));
        }
        ex.getBindingResult().getGlobalErrors().forEach(error ->
                details.add(new ErrorResponse.Detail(error.getObjectName(), error.getDefaultMessage())));
        return asObject(validationError(details));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErrorResponse.Detail> details = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    details.add(new ErrorResponse.Detail(name, error.getDefaultMessage())));
        });
        return asObject(validationError(details));
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return asObject(validationError(List.of(new ErrorResponse.Detail(ex.getParameterName(), REQUIRED_DETAIL))));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorResponse.Detail item = new ErrorResponse.Detail(ex.getPropertyName(), typeDetail(ex.getRequiredType()));
        return asObject(build(HttpStatus.BAD_REQUEST,
                ErrorResponse.of(ErrorCode.BAD_REQUEST, INVALID_PARAMETER_MESSAGE, List.of(item))));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        PropertyBindingException unknownField = findCause(ex, PropertyBindingException.class);
        if (unknownField != null) {
            String field = jsonPath(unknownField, unknownField.getPropertyName());
            log.warn("Campo no modificable en la solicitud: {}", field);
            return asObject(build(HttpStatus.BAD_REQUEST, nonModifiable(List.of(field))));
        }
        InvalidFormatException invalidFormat = findCause(ex, InvalidFormatException.class);
        if (invalidFormat != null) {
            ErrorResponse.Detail item = new ErrorResponse.Detail(jsonPath(invalidFormat, null),
                    typeDetail(invalidFormat.getTargetType()));
            return asObject(build(HttpStatus.BAD_REQUEST,
                    ErrorResponse.of(ErrorCode.BAD_REQUEST, INVALID_VALUE_MESSAGE, List.of(item))));
        }
        JsonMappingException mapping = findCause(ex, JsonMappingException.class);
        List<ErrorResponse.Detail> details = mapping != null && !mapping.getPath().isEmpty()
                ? List.of(new ErrorResponse.Detail(jsonPath(mapping, null), INVALID_FORMAT_DETAIL))
                : List.of();
        return asObject(build(HttpStatus.BAD_REQUEST,
                ErrorResponse.of(ErrorCode.BAD_REQUEST, MALFORMED_JSON_MESSAGE, details)));
    }

    /**
     * Resto de excepciones estándar de Spring MVC (ruta inexistente, método o media type no soportado, etc.):
     * conserva el status HTTP y reemplaza el ProblemDetail por el contrato uniforme.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ErrorResponse error;
        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            error = ErrorResponse.of(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE);
        } else if (statusCode.is4xxClientError()) {
            error = ErrorResponse.of(ErrorCode.BAD_REQUEST, UNSUPPORTED_REQUEST_MESSAGE);
        } else {
            log.error("Error interno de Spring MVC", ex);
            error = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE);
        }
        return ResponseEntity.status(statusCode).headers(headers)
                .contentType(MediaType.APPLICATION_JSON).body(error);
    }

    // ---------------------------------------------------------------- helpers

    private static ErrorResponse nonModifiable(List<String> fields) {
        List<ErrorResponse.Detail> details = fields.stream()
                .map(field -> new ErrorResponse.Detail(field, NonModifiableFieldException.MESSAGE))
                .toList();
        return ErrorResponse.of(ErrorCode.BAD_REQUEST, NonModifiableFieldException.MESSAGE, details);
    }

    private static ResponseEntity<ErrorResponse> validationError(List<ErrorResponse.Detail> details) {
        return build(HttpStatus.BAD_REQUEST,
                ErrorResponse.of(ErrorCode.VALIDATION_ERROR, VALIDATION_MESSAGE, details));
    }

    private static ResponseEntity<ErrorResponse> build(HttpStatus status, ErrorResponse body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<Object> asObject(ResponseEntity<ErrorResponse> response) {
        return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders())
                .body(response.getBody());
    }

    @Nullable
    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        Throwable current = ex;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
        return null;
    }

    private static String jsonPath(JsonMappingException ex, @Nullable String fallback) {
        StringBuilder path = new StringBuilder();
        for (JsonMappingException.Reference ref : ex.getPath()) {
            if (ref.getFieldName() != null) {
                if (!path.isEmpty()) {
                    path.append('.');
                }
                path.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                path.append('[').append(ref.getIndex()).append(']');
            }
        }
        return path.isEmpty() && fallback != null ? fallback : path.toString();
    }

    private static String typeDetail(@Nullable Class<?> type) {
        if (type == null || !type.isEnum()) {
            return INVALID_FORMAT_DETAIL;
        }
        return ENUM_DETAIL_PREFIX + Arrays.stream(type.getEnumConstants())
                .map(constant -> ((Enum<?>) constant).name())
                .collect(Collectors.joining(", "));
    }

    private static String lastNode(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}

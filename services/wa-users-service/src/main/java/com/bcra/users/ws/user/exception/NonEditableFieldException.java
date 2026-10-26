package com.bcra.users.ws.user.exception;

/** Intento de modificar una propiedad fuera de los datos permitidos. */
public class NonEditableFieldException extends IllegalArgumentException {

    public NonEditableFieldException() {
        super("El campo no es editable por este servicio");
    }
}

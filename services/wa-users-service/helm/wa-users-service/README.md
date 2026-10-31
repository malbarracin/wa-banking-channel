# Credencial de base de datos

El chart exige `env.DB_PASSWORD` mediante `required` en `templates/secret.yaml`.
No hay una contraseña por defecto en los valores base ni en `values-prod.yaml`.
El chart crea el Secret que consume el Deployment a partir del valor explícito.

Para desplegar, proporcionar la credencial desde un archivo privado:

```sh
helm upgrade --install wa-users-service . -f values-prod.yaml \
  --set-file env.DB_PASSWORD=/ruta/privada/db-password
```

No versionar ese archivo ni guardar manifiestos renderizados con credenciales reales.

Verificación desde esta carpeta:

```sh
# Debe fallar con «env.DB_PASSWORD es obligatorio» (código distinto de cero).
helm template wa-users-service . -f values-prod.yaml

# También debe fallar si el valor está vacío.
helm template wa-users-service . -f values-prod.yaml --set-string env.DB_PASSWORD=

# Debe renderizar correctamente; credencial sintética solo para esta comprobación.
helm template wa-users-service . -f values-prod.yaml \
  --set-string env.DB_PASSWORD=synthetic-render-check
helm lint . -f values-prod.yaml --set-string env.DB_PASSWORD=synthetic-render-check
```

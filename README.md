# wa-banking-channel

## Verificación

Desde la raíz del repositorio:

```sh
./mvnw -q -B verify
```

En Windows (PowerShell o CMD):

```powershell
.\mvnw.cmd -q -B verify
```

Estos comandos delegan al Maven Wrapper de `services/wa-users-service`, conservan
los argumentos y devuelven el código de salida del build. El servicio exige un
mínimo de cobertura LINE del 80% durante `verify`.

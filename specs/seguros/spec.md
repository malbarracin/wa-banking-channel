# Seguros — especificación

## Propósito
Permitir al cliente consultar sus pólizas vigentes, comprender sus coberturas, denunciar un siniestro y seguir su caso por WhatsApp.
Estado: borrador para aprobación humana. Proyecto: `mi-proyecto` (project.yaml).
Fuente: https://app.notion.com/p/H6-Servicio-de-Seguros-36875bb08ca781129b47cd3ced785221

## Alcance y dependencias
- H6, componente indicado por la fuente: `services/wa-insurance-service`.
- H1: cliente registrado; H2: credencial de sesión válida; H3: vínculo activo.
- Sin dependencia funcional de H4/H5, salvo referencias a productos asegurados en core.
- Entregable indicado por la fuente: OpenAPI y handoff en `shared/contracts/wa-insurance-service/`, a cargo de los roles siguientes.
- Fuera de alcance: cotización, contratación, cancelación, montos indemnizatorios finales, negociación de liquidación y peritajes. Las fotos no reemplazan el proceso formal; solo se ofrece orientación.

## Requisitos

### Requisito: S1 — Acceso con credencial y vínculo activo
El sistema DEBE exigir credencial válida de H2 y vínculo activo de H3 en todas las consultas y denuncias, para un cliente registrado en H1. Su rol respecto de H2 es CONSUMIDOR.

#### Escenario: Acceso habilitado
- **DADO** un cliente registrado, con vínculo activo y credencial válida
- **CUANDO** solicita una operación de seguros
- **ENTONCES** el sistema DEBE habilitarla sujeta a la autorización sobre la póliza o caso.

#### Escenario: Sin credencial válida
- **DADO** que no existe una credencial válida
- **CUANDO** se solicitan pólizas, coberturas, apertura o seguimiento
- **ENTONCES** el sistema NO DEBE mostrar información ni abrir casos
- **Y** DEBE orientar: “Verificá tu identidad para consultar tus seguros.”

#### Escenario: Sin vínculo activo
- **DADO** un cliente sin vínculo activo
- **CUANDO** intenta operar por el canal
- **ENTONCES** el sistema NO DEBE habilitar la operación.

### Requisito: S2 — Autorización sobre pólizas y casos
El sistema DEBE permitir operar solo sobre pólizas donde el cliente figure como titular o asegurado en el registro del banco. Solo DEBE mostrar casos propios del titular autenticado. Un beneficiario o tercero NO DEBE operar por defecto; DEBE ser derivado al titular o canal formal.

#### Escenario: Póliza autorizada
- **DADO** que el registro identifica al cliente como titular o asegurado
- **CUANDO** consulta o selecciona esa póliza para denunciar
- **ENTONCES** el sistema DEBE permitir el acceso sujeto a los restantes requisitos.

#### Escenario: Recurso ajeno
- **DADO** una póliza sin relación autorizada o un caso de otro titular
- **CUANDO** el cliente intenta acceder
- **ENTONCES** el sistema NO DEBE revelar sus datos ni permitir operar sobre él.

#### Escenario: Beneficiario o tercero
- **DADO** un solicitante que solo es beneficiario o tercero
- **CUANDO** pide operar
- **ENTONCES** el sistema DEBE derivarlo al titular o canal formal.

### Requisito: S3 — Listado de pólizas vigentes
El sistema DEBE listar las pólizas vigentes autorizadas, mostrando ramo, vigencia e identificador corto.

#### Escenario: Mis seguros
- **DADO** un cliente autorizado con pólizas vigentes
- **CUANDO** pide “mis seguros”
- **ENTONCES** el sistema DEBE listar las pólizas con esos datos
- **Y** DEBE presentarlas con el mensaje “{{nombre}}, estas son tus pólizas vigentes: …”.

#### Escenario: Póliza no vigente
- **DADO** una póliza que no está vigente
- **CUANDO** se consulta el listado de pólizas vigentes
- **ENTONCES** el sistema NO DEBE incluirla.

### Requisito: S4 — Coberturas y exclusiones comprensibles
El sistema DEBE mostrar las coberturas principales y exclusiones relevantes resumidas en lenguaje simple, sin jerga actuarial ni letra chica completa. DEBE identificar la información como resumen y la prevalencia de las condiciones generales oficiales en caso de disputa.

#### Escenario: Selección de póliza
- **DADO** una póliza autorizada seleccionada
- **CUANDO** el cliente consulta su cobertura
- **ENTONCES** el sistema DEBE explicar coberturas y exclusiones con el mensaje “Tu seguro {{ramo}} cubre principalmente: … No incluye: …”
- **Y** DEBE aclarar el carácter resumido y la prevalencia de las condiciones oficiales.

### Requisito: S5 — Captura mínima y confirmación de denuncia
El sistema DEBE guiar la captura de póliza afectada, tipo de siniestro (hogar/auto/vida/etc., según corresponda), fecha y descripción breve. DEBE presentar un resumen y requerir confirmación explícita antes de abrir el caso.

#### Escenario: Confirmación explícita
- **DADO** los cuatro datos capturados y su resumen presentado
- **CUANDO** el cliente confirma explícitamente
- **ENTONCES** el sistema DEBE proceder a la apertura, sujeta al control de duplicados.

#### Escenario: Sin datos completos o sin confirmación
- **DADO** que falta alguno de los datos requeridos o la confirmación explícita
- **CUANDO** se tramita la denuncia
- **ENTONCES** el sistema NO DEBE abrir el caso y DEBE continuar la captura o solicitar la confirmación pendiente.

### Requisito: S6 — Prevención de denuncias duplicadas
El sistema NO DEBE crear una segunda denuncia para la misma póliza y tipo de evento dentro de una ventana configurable cuando exista una denuncia abierta. DEBE advertir el duplicado. Se consideran abiertas las denuncias en estado recibido, en análisis o documentación pendiente; resuelto permite otra denuncia.

#### Escenario: Duplicado dentro de ventana
- **DADO** un caso de la misma póliza y tipo de evento dentro de la ventana configurada, en cualquiera de los tres estados abiertos
- **CUANDO** se confirma otra denuncia
- **ENTONCES** el sistema DEBE advertir el duplicado y NO DEBE crear un segundo caso.

#### Escenario: Caso resuelto
- **DADO** un caso resuelto para la misma póliza y tipo de evento dentro de la ventana
- **CUANDO** se confirma otra denuncia y no existe otro caso abierto coincidente
- **ENTONCES** el caso resuelto NO DEBE impedir la nueva apertura.

#### Escenario: Fuera de ventana o sin coincidencia
- **DADO** que no existe caso abierto de igual póliza y tipo de evento dentro de la ventana
- **CUANDO** se confirma la denuncia
- **ENTONCES** el control de duplicados DEBE permitir la apertura.

### Requisito: S7 — Número de caso y próximos pasos
El sistema DEBE asignar un número de caso único y comunicarlo inmediatamente al abrir la denuncia confirmada, junto con los próximos pasos de documentación y plazos correspondientes.

#### Escenario: Apertura exitosa
- **DADO** una denuncia confirmada que supera las validaciones
- **CUANDO** se abre el caso
- **ENTONCES** el sistema DEBE comunicar inmediatamente “Registramos tu siniestro. Tu número de caso es {{numero_caso}}. Te indicamos qué documentación enviar por los canales oficiales.”
- **Y** DEBE indicar los próximos pasos y plazos correspondientes sin prometer una liquidación.

#### Escenario: Unicidad
- **DADO** dos aperturas válidas diferentes
- **CUANDO** se asignan sus números de caso
- **ENTONCES** sus números DEBEN ser distintos.

### Requisito: S8 — Seguimiento fiel al sistema de siniestros
El sistema DEBE consultar y reflejar el estado del sistema de siniestros y las acciones pendientes del cliente. Los estados previstos son recibido, en análisis, documentación pendiente y resuelto. El área de siniestros actualiza el sistema de origen; el canal NO DEBE inventar estados.

#### Escenario: Consulta de caso propio
- **DADO** un caso propio y un estado informado por el sistema de siniestros
- **CUANDO** el cliente envía su número de caso o pide “estado de mi reclamo”
- **ENTONCES** el sistema DEBE mostrar el mismo estado y acciones pendientes
- **Y** DEBE usar el mensaje “Tu caso {{numero_caso}} está en: {{estado_caso}}. Pendiente: {{pendiente}}.”

### Requisito: S9 — Recordatorio de documentación pendiente
El sistema DEBE proporcionar un recordatorio de documentación pendiente con una lista simple y orientar el envío por canales oficiales.

#### Escenario: Documentación pendiente
- **DADO** un caso propio con documentación requerida pendiente
- **CUANDO** se proporciona el recordatorio al cliente
- **ENTONCES** el sistema DEBE enumerar de forma simple los documentos pendientes y orientar su envío formal.

### Requisito: S10 — Privacidad y límites del canal
El sistema DEBE limitar los datos del siniestro al mínimo necesario. NO DEBE pedir datos médicos sensibles por chat sin política aprobada ni incluir datos médicos no autorizados o montos de liquidación en mensajes. NO DEBE ofrecer cotización ni contratación ni ejecutar las operaciones excluidas del alcance.

#### Escenario: Sin política médica aprobada
- **DADO** que no existe una política aprobada que autorice la captura médica sensible
- **CUANDO** se guía la denuncia y se responde al cliente
- **ENTONCES** el sistema NO DEBE solicitar esos datos ni exponer datos médicos no autorizados.

#### Escenario: Solicitud fuera de alcance
- **DADO** una solicitud de cotización, contratación, cancelación, negociación o monto final de liquidación
- **CUANDO** el cliente la plantea por WhatsApp
- **ENTONCES** el sistema NO DEBE ofrecer ni ejecutar esa operación ni comunicar montos de liquidación.

#### Escenario: Fotos y peritajes
- **DADO** una solicitud de reemplazar el proceso formal por fotos o peritaje en el chat
- **CUANDO** se orienta al cliente
- **ENTONCES** el sistema NO DEBE presentar el chat como reemplazo del proceso formal.

## Decisiones y supuestos de la fuente
- Resolved via user decision: respuesta A, recibida en el paso `004-analista-sigue1` de la corrida `20261027-b0a3`: recibido, en análisis y documentación pendiente impiden duplicados en ventana; resuelto permite otra denuncia (S6).
- La fuente supone un sistema de siniestros integrado con estados estándar y pólizas vigentes actualizadas diariamente. No se añaden acuerdos de rendimiento, auditoría ni retención no establecidos por H6.
- Riesgos a considerar: expectativas de indemnización por chat, denuncias incompletas y confusión entre resumen y póliza legal.

## Preguntas abiertas
- La duración de la ventana queda como parámetro configurable según H6; la fuente no fija un valor concreto.
- La documentación y los plazos dependen del proceso formal correspondiente; H6 no fija listas ni duraciones universales.
- Cualquier autorización para datos médicos sensibles exige política aprobada; mientras no exista, aplica la prohibición de S10.
- H6 no especifica frecuencia ni disparador automático del recordatorio; no se exige programación de envíos ni se inventa una cadencia.

Estas cuestiones delimitan políticas no fijadas por la fuente; no alteran la decisión resuelta sobre estados abiertos ni agregan obligaciones sin fuente.

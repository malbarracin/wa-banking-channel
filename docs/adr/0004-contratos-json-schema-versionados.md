# ADR-0004 — Contratos como JSON Schema versionados, sin registro ni librería compartida

- **Estado**: Aceptada
- **Fecha**: 2026-11-21
- **Alcance**: `shared/contracts/events/` y todo productor o consumidor de eventos `wa.*`

## Contexto

Los eventos son la frontera entre componentes que construyen equipos distintos y en momentos distintos. Hace falta
una definición única, legible y verificable de cada evento, y una forma de detectar temprano que alguien publicó algo
que el otro lado no puede leer.

El programa fija además una restricción: **los componentes no comparten código**, se hablan por los contratos de
`shared/contracts/`.

## Decisión

Cada evento se define como un **JSON Schema draft 2020-12 versionado** en `shared/contracts/events/`
(`envelope.v1.schema.json` y un archivo por evento y versión), y **se valida en los dos lados**: el productor antes
de enviar (inválido es un bug: excepción y no se publica) y el consumidor al recibir (inválido va a DLQ sin
reintentos).

- El sobre lleva `additionalProperties: false`. Cada esquema de evento referencia el sobre con `allOf` y solo
  **restringe** propiedades que el sobre ya declara.
- **No hay librería Java compartida.** Cada componente implementa el sobre con sus propios records; lo único que se
  comparte son los archivos de esquema, copiados al classpath en el build con `maven-resources-plugin` desde
  `shared/contracts/events/`. Fuente única, sin dependencia binaria entre componentes.
- El valor del registro viaja como **texto JSON** (`StringSerializer` / `StringDeserializer`) y se valida el JSON
  exacto que se envía y el exacto que llega. Queda **prohibida la deserialización polimórfica** (headers de tipo de
  `JsonDeserializer`, `spring.json.trusted.packages=*`): el `eventType` del sobre decide a qué record se mapea, con
  `FAIL_ON_UNKNOWN_PROPERTIES`.
- Versionado: campo opcional nuevo, misma versión; cambio incompatible, topic y esquema `v<n+1>` con publicación
  doble hasta que migren los consumidores. Invariante verificada: `eventVersion` del sobre, sufijo `.v<n>` del topic
  y versión del esquema siempre coinciden.

## Alternativas consideradas

- **Confluent Schema Registry con Avro o Protobuf**: compatibilidad verificada por el registro y payload compacto,
  pero agrega un servicio más para levantar, operar y asegurar, y acopla el build a un plugin de generación. Para
  cuatro topics y tres eventos, el costo supera el beneficio. Se puede adoptar más adelante sin cambiar los nombres
  de topics ni la forma del sobre. Descartada por ahora.
- **Librería Java común con el sobre y los productores/consumidores**: evitaría repetir código, pero crea una
  dependencia binaria y un acoplamiento de versiones entre componentes que el programa descarta explícitamente.
  Descartada.
- **Contrato solo en prosa, sin validación**: los desajustes aparecerían en producción, y en un canal bancario eso
  significa mensajes al cliente que no salen. Descartada.

## Consecuencias

- Un evento mal formado se detecta en el productor, antes de ensuciar el topic, y nunca queda un consumidor
  procesando basura.
- Hay algo de duplicación deliberada: cada componente escribe sus propios records del sobre. Se acepta como el precio
  de no acoplar binarios.
- Validar cuesta CPU por evento; con el catálogo cacheado al arrancar es despreciable frente a la latencia del canal.
- Si falta el esquema de un `eventType` declarado, la aplicación no arranca: es un error de build, no de runtime.
- La carpeta `shared/contracts/events/` pasa a ser material de revisión obligatoria en los pull requests que toquen
  eventos.

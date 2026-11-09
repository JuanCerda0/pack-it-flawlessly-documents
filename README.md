# Documentos de PackIt

Microservicio Spring Boot 4.1.2 y Java 21 que consume trabajos de RabbitMQ y genera comprobantes PDF de ingreso (`INTAKE`) y despacho (`DISPATCH`). Guarda el PDF, sus metadatos y la instantánea recibida en su propia base PostgreSQL.

## Ejecutar en local

1. Inicia PostgreSQL y RabbitMQ. Crea la base con `createdb -h localhost -U postgres packit_documents` si aún no existe.
2. Ejecuta `./mvnw spring-boot:run` desde este directorio. Flyway crea la tabla `documents` y la secuencia de números. La API escucha en `http://localhost:8083`.
3. Configura `DOCUMENTS_DB_URL`, `DOCUMENTS_DB_USERNAME`, `DOCUMENTS_DB_PASSWORD`, `DOCUMENTS_RABBITMQ_HOST`, `DOCUMENTS_RABBITMQ_PORT`, `DOCUMENTS_RABBITMQ_USERNAME` y `DOCUMENTS_RABBITMQ_PASSWORD` si difieren de los valores locales. `COGNITO_ISSUER_URI` indica el pool de Cognito; `DOCUMENTS_CORS_ALLOWED_ORIGINS` ajusta el origen del frontend. `DOCUMENTS_CONSUMER_ENABLED=false` desactiva la recepción en pruebas aisladas.

Paquetes publica trabajos en el exchange directo `packit.document.commands`. Documentos consume `documents.intake` y `documents.dispatch`, ambas colas classic durables con sus respectivas DLQ. Un mensaje válido genera un PDF con número `ING-{id}` o `DES-{id}`. El ACK ocurre después de confirmar el registro; los mensajes inválidos o que agotan tres intentos terminan en la DLQ. La instantánea del mensaje evita consultar la base de Paquetes y un PDF emitido no se reescribe tras cambios posteriores.

## Consultar y descargar

Ambas rutas requieren un access token de Cognito con grupo `ADMIN` o `SUPERVISOR`:

```bash
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8083/api/v1/packages/42/documents
curl -H "Authorization: Bearer $ACCESS_TOKEN" \
  -OJ http://localhost:8083/api/v1/documents/1/file
```

La primera devuelve metadatos; la segunda devuelve `application/pdf` como adjunto. El contrato de los trabajos está en `../pack-it-flawlessly-backend/docs/message-contracts.md`.

## Pruebas

- `./mvnw test`: valida contrato, persistencia, PDF, paginación, deduplicación, consumo y permisos. Necesita PostgreSQL local; no necesita RabbitMQ.
- `./mvnw -Ddocuments.rabbit.integration=true -Dtest=DocumentsRabbitIntegrationTests test`: prueba optativa con RabbitMQ y PostgreSQL locales; verifica ambas colas, una entrega duplicada y el envío de un trabajo inválido a `documents.intake.dlq`. Deja ese mensaje de demostración en la DLQ para inspeccionarlo.

## Docker local

El [Compose del repositorio Paquetes](../pack-it-flawlessly-backend/compose.yaml) construye este servicio y le asigna su propia instancia PostgreSQL (`postgres-documents`). Consulta el README de Paquetes para preparar `.env.compose`, levantar el clúster RabbitMQ y verificar el stack completo.

# messaging-service

**Messaging Service (MS)** de Foodly. Administra la experiencia conversacional: el explorador le escribe a **Lía**,
que interpreta su intención en lenguaje natural, y Messaging coordina la respuesta con **Radar** (huariques
cercanos), **Business** (detalle y menú) y **Reservation** (reservas). Guarda el historial en MySQL.

- Java 17, Spring Boot 3.5, Maven. MySQL con Flyway.
- Las respuestas de Lía llegan por **STOMP sobre WebSocket**; `GET .../messages` es la fuente de verdad.
- n8n y OpenAI quedan detrás de una capa anticorrupción; Messaging no los conoce más allá de `infrastructure/lia`.

Los nombres de clases, endpoints y tablas siguen el informe. Lo que el informe no define está en
[`docs/SPEC_RADAR_MESSAGING.md`](docs/SPEC_RADAR_MESSAGING.md) y cada diferencia con el informe, en
[`DEVIATIONS.md`](DEVIATIONS.md). [`docs/ALIGNMENT.md`](docs/ALIGNMENT.md) resume lo que el informe define por capa.
El workflow de n8n está en [`docs/n8n-lia-workflow.md`](docs/n8n-lia-workflow.md).

## Levantar con Docker

Requisitos: Docker con Compose v2. Los scripts de `scripts/` son de Bash (en Windows, Git Bash o WSL).

Antes copie `.env.example` a `.env` y defina `JWT_SECRET_BASE64` (la misma clave Base64 que usa IAM; una de prueba se
genera con `openssl rand -base64 32`). Sin ella Messaging no arranca, aunque MySQL sí.

```bash
docker compose -f docker-compose.local.yml up -d --build --wait
```

Levanta MySQL 8 y Messaging en la red `foodly-network`, en **modo stub**: sin los otros microservicios ni IA. Radar,
Business y Reservation son falsos (datos de ejemplo de Lima) y Lía clasifica por palabras clave. Con `--wait`, el
comando termina cuando los dos están sanos (el primer arranque compila la imagen: unos minutos). Los puertos se
publican solo en `127.0.0.1`.

| Servicio  | URL / puerto                         | Notas                                              |
|-----------|--------------------------------------|----------------------------------------------------|
| Messaging | http://localhost:8083                | API, Swagger, Actuator y WebSocket (`/messaging/ws`) |
| MySQL     | `localhost:3307`                     | base `messaging`, usuario y contraseña `messaging` |
| n8n       | http://localhost:5678                | opcional: `--profile n8n` (ver más abajo)          |

```bash
curl http://localhost:8083/actuator/health     # {"status":"UP"}
```

Para detener todo: `docker compose -f docker-compose.local.yml down` (con `-v` si además quiere borrar los datos).
Los puertos del equipo se cambian con `MESSAGING_HOST_PORT` (8083), `MYSQL_HOST_PORT` (3307) y `N8N_HOST_PORT` (5678).

### Variantes

| Qué                                   | Cómo                                                                                                      |
|---------------------------------------|-----------------------------------------------------------------------------------------------------------|
| Con n8n (solo local)                  | `LIA_GATEWAY_MODE=direct-n8n docker compose -f docker-compose.local.yml --profile n8n up -d --build --wait`, y arme el workflow de [`docs/n8n-lia-workflow.md`](docs/n8n-lia-workflow.md) |
| Con los microservicios reales         | `MESSAGING_PROFILE= docker compose -f docker-compose.local.yml up -d --build --wait` (quita el perfil `stub`; usa `RADAR_URL`, `BUSINESS_URL` y `RESERVATION_URL`, por defecto `http://radar:8082`, `http://business:8080` y `http://reservation:8084`; `radar` es el servicio de `../radar-service` en la misma red) |
| Ver el aviso de timeout de Lía        | `LIA_STUB_DELAY=60s LIA_TIMEOUT_SECONDS=5 docker compose -f docker-compose.local.yml up -d`: el stub tarda 60 s y a los 5 s Lía ofrece la reserva manual |

## Conversación completa en modo stub

Con el compose levantado. Los endpoints de usuario exigen un JWT de IAM con el rol `GASTRONOMIC_EXPLORER` y el id
del explorador como UUID. Para desarrollo, `scripts/dev-token.sh` genera uno como lo emite IAM (HS256, `iss=foodly-iam`, `aud` con
`foodly-messaging`), firmado con `JWT_SECRET_BASE64` (requiere OpenSSL 1.1 o superior):

```bash
export JWT_SECRET_BASE64=<la misma clave que Messaging>
BASE=http://localhost:8083
TOKEN=$(scripts/dev-token.sh)

# Escribe un mensaje. El cuerpo va por stdin (--data-binary @-) para que los acentos lleguen bien en cualquier
# sistema (en Git Bash de Windows, «-d '...menú...'» los recodifica y el servidor responde 400).
chat() {   # chat "mensaje" [campos JSON extra, p. ej. ',"latitude":-12.119,"longitude":-77.035']
  local conversation=""
  [ -n "${CONV:-}" ] && conversation=",\"conversationId\":\"$CONV\""
  curl -s -X POST "$BASE/messaging/chat" -H "Authorization: Bearer $TOKEN" \
       -H 'Content-Type: application/json' --data-binary @- <<EOF
{"content":"$1"$conversation$2}
EOF
}
history() { curl -s "$BASE/messaging/conversations/$CONV/messages" -H "Authorization: Bearer $TOKEN"; }
```

**1. Una recomendación.** Responde `202` de inmediato; la respuesta de Lía llega ~1 s después.

```bash
chat "Tengo antojo de algo rico cerca" ',"latitude":-12.1190,"longitude":-77.0350'
# {"conversationId":"d79d1d00-7e48-4e1e-bd5b-7d9a517089fa","messageId":"0355acf4-…","status":"PROCESSING"}

CONV=d79d1d00-7e48-4e1e-bd5b-7d9a517089fa      # el conversationId de la respuesta anterior
sleep 2; history
```

```json
[
  { "id": "0355acf4-…", "sender": "EXPLORER", "content": "Tengo antojo de algo rico cerca",
    "intentType": null, "huariqueId": null, "reservationId": null, "sentAt": "2026-11-13T23:21:45.673976" },
  { "id": "d9346276-…", "sender": "LIA",
    "content": "¡Claro! Te busco opciones cerca de ti…\n\nEsto es lo que encontré cerca de ti:\n1. Cevichería El Faro Miraflorino (a 660 m)\n2. Picantería La Chalaca Surquillana (a 2.7 km)\n3. Anticuchos Doña Rosa (a 3.7 km)",
    "intentType": "RECOMMENDATION_REQUEST", "huariqueId": null, "reservationId": null, "sentAt": "2026-11-13T23:21:46.782303" }
]
```

Messaging consultó a Radar (con el k-ring de `MESSAGING_RADAR_K_RING`) y pidió a Business el nombre de los 3 primeros.

**2. La consulta de un huarique.** El stub reconoce el UUID del huarique en el texto (los de ejemplo son
`a1111111-1111-4111-8111-111111111111`, `a2222222-…`, `a3333333-…` y `a4444444-…`):

```bash
chat "¿Cuál es el menú de a1111111-1111-4111-8111-111111111111?"; sleep 2; history
# Lía: «Con gusto, te cuento lo que sé de ese huarique.
#       Cevichería El Faro Miraflorino · Dirección: Av. Larco 345, Miraflores · Horario: 12:00 - 17:00
#       Ahora está abierto. Carta: - Ceviche clásico: S/ 32.00 - Jalea mixta: S/ 45.00 - Chicha morada: S/ 8.00»
```

**3. Una reserva en dos turnos.** Si falta un dato, Lía lo pide; la siguiente respuesta continúa la reserva:

```bash
chat "Quiero reservar una mesa en a1111111-1111-4111-8111-111111111111"; sleep 2; history
# Lía: «¡Con gusto! Te ayudo con tu reserva.  Para continuar necesito que me indiques para qué fecha y hora y para cuántas personas.»

chat "mañana a las 8 pm para 4 personas"; sleep 2; history
# Lía: «Envié tu solicitud de reserva para 4 personas el 14/11/2026 a las 20:00. Estado: pendiente de aprobación del dueño.»
```

El mensaje del explorador queda vinculado a la reserva (`"reservationId": "b631b75b-…"` en `GET .../messages`).
Messaging no valida horarios ni capacidad: reenvía la solicitud y Reservation decide.

**Otros casos**

```bash
curl -s "$BASE/messaging/conversations/me" -H "Authorization: Bearer $TOKEN"    # mis conversaciones

curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/messaging/chat" -H 'Content-Type: application/json' -d '{"content":"hola"}'
# 401: sin token
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$BASE/messaging/chat" -H "Authorization: Bearer $(scripts/dev-token.sh HUARIQUE_OWNER)" \
     -H 'Content-Type: application/json' -d '{"content":"hola"}'
# 403: rol HUARIQUE_OWNER
curl -s -X POST "$BASE/messaging/chat" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"content":"","latitude":95}'
# 400: {"status":400,"error":"Bad Request","message":"No se pudo procesar la solicitud: El mensaje no puede estar vacío. …"}
```

### Palabras clave del modo stub

| Palabras (sin importar mayúsculas ni tildes) | Intención                | Qué extrae                                                           |
|----------------------------------------------|--------------------------|----------------------------------------------------------------------|
| `reserva`, `mesa`                            | `RESERVATION_REQUEST`    | UUID del huarique, «N personas» / «para N», «hoy» / «mañana» / `yyyy-MM-dd` y la hora («a las 8 pm», «20:00») |
| `menú`, `precio`, `horario`                  | `HUARIQUE_INQUIRY`       | UUID del huarique                                                    |
| `cerca`, `recomienda`, `antojo`              | `RECOMMENDATION_REQUEST` | la ubicación que envió el explorador                                 |
| ninguna                                      | continúa la intención pendiente, o `UNKNOWN` | —                                     |

Si coinciden varias, gana la primera de la tabla.

## Recibir la respuesta por STOMP

No hace falta consultar en bucle: Messaging publica cada respuesta de Lía en `/topic/conversations/{conversationId}`.

| Qué              | Valor                                                                                         |
|------------------|-----------------------------------------------------------------------------------------------|
| Endpoint         | `ws://localhost:8083/messaging/ws` (WebSocket nativo, sin SockJS)                             |
| Autenticación    | header `Authorization: Bearer <JWT>` en el frame **`CONNECT`**. Sin token, con token inválido o con otro rol, la conexión se cierra con un frame `ERROR` |
| Suscripción      | `/topic/conversations/{conversationId}`, **solo de conversaciones propias** (la de otro explorador se rechaza) |
| Mensaje recibido | el mismo `MessageResource` que `GET .../messages`                                             |
| Publicar         | no se puede: todo `SEND` se rechaza                                                           |
| Latidos          | cada 10 s                                                                                     |

Con [`@stomp/stompjs`](https://stomp-js.github.io/):

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8083/messaging/ws',
  connectHeaders: { Authorization: `Bearer ${token}` },
  heartbeatIncoming: 10000,
  heartbeatOutgoing: 10000,
  onConnect: () => {
    client.subscribe(`/topic/conversations/${conversationId}`, (frame) => {
      const message = JSON.parse(frame.body);          // { id, sender, content, intentType, ... }
      showMessage(message);
    });
    loadHistory();   // GET .../messages: ver la nota de abajo
  },
  onStompError: (frame) => console.error(frame.headers.message),
});
client.activate();
```

Los frames, por si usa otro cliente:

```
CONNECT
accept-version:1.2
heart-beat:10000,10000
Authorization:Bearer <JWT>

^@
SUBSCRIBE
id:sub-0
destination:/topic/conversations/<conversationId>

^@
```

> **Después de suscribirse, lea el historial con `GET .../messages`.** El broker simple de Spring no devuelve
> `RECEIPT`, así que no hay forma de saber cuándo quedó activa la suscripción; una respuesta que llegue justo
> antes se recupera con el `GET`, que es la fuente de verdad (y el respaldo si el WebSocket se cae). Una
> suscripción ya hecha no se cierra cuando vence el JWT (60 min): reconéctese con un token nuevo.

## Endpoints

| Método | Ruta                                              | Acceso                                  | Descripción                                                     |
|--------|---------------------------------------------------|-----------------------------------------|-----------------------------------------------------------------|
| POST   | `/messaging/chat`                                 | JWT, rol `GASTRONOMIC_EXPLORER`         | Envía un mensaje a Lía. `202 { conversationId, messageId, status: "PROCESSING" }` |
| GET    | `/messaging/conversations/me`                     | JWT, rol `GASTRONOMIC_EXPLORER`         | Conversaciones del explorador, la más reciente primero          |
| GET    | `/messaging/conversations/{conversationId}/messages` | JWT, rol `GASTRONOMIC_EXPLORER`      | Mensajes de una conversación (404 si no existe o es de otro)    |
| POST   | `/messaging/callbacks/n8n`                        | Header `X-Callback-Secret` (sin JWT)    | Respuesta de Lía. Lo invoca el Integration System. `202 { status: "RECEIVED" }` |
| WS     | `/messaging/ws`                                   | JWT en el frame `CONNECT`               | Canal STOMP (ver arriba)                                        |
| GET    | `/actuator/health`                                | Público                                 | Salud del servicio y de MySQL (la usa el `HEALTHCHECK` de Docker) |
| GET    | `/swagger-ui.html`, `/v3/api-docs`                | Público                                 | Documentación interactiva y especificación OpenAPI              |

Códigos: `202` al aceptar un mensaje, `200` en las consultas, `400` con datos inválidos, `401` sin token (o con un
secreto de callback incorrecto), `403` si el rol no es explorador, `404` si la conversación no existe o es de otro
explorador y `409` si se escribe en una conversación cerrada. Todos los errores tienen el mismo formato:
`{ status, error, message, path, timestamp }`, con el mensaje en español.

Para probar en Swagger (http://localhost:8083/swagger-ui.html): genere un token con `scripts/dev-token.sh`, pulse
**Authorize**, pegue el token (sin la palabra `Bearer`) y pruebe `POST /messaging/chat`.

## Variables de entorno

Toda la configuración se lee de variables de entorno. En `docker-compose.local.yml` ya vienen definidas.

| Variable                          | Obligatoria | Por defecto              | Descripción |
|-----------------------------------|-------------|--------------------------|-------------|
| `MYSQL_URL`                       | Sí          | —                        | URL JDBC, p. ej. `jdbc:mysql://host:3306/messaging` |
| `MYSQL_USER`, `MYSQL_PASSWORD`    | Sí          | —                        | Credenciales de MySQL |
| `MYSQL_SSL_MODE`                  | No          | `REQUIRED`               | `DISABLED`, `PREFERRED`, `REQUIRED`, `VERIFY_CA` o `VERIFY_IDENTITY`. Aiven exige SSL. Un MySQL local sin SSL necesita `DISABLED` (y, con MySQL 8, `?allowPublicKeyRetrieval=true` en la URL) |
| `JWT_SECRET_BASE64`               | Sí          | —                        | Clave con la que IAM firma el JWT (HS256), en Base64 estándar. Se decodifica a bytes (mínimo 32), igual que IAM. Solo por entorno, sin valor por defecto |
| `JWT_ISSUER`                      | No          | `foodly-iam`             | Valor exacto del claim `iss` |
| `JWT_AUDIENCE`                    | No          | `foodly-messaging`       | Valor que debe estar incluido en el claim `aud` |
| `JWT_USER_ID_CLAIM`               | No          | `sub`                    | Claim con el id del usuario (un UUID) |
| `JWT_ROLE_CLAIM`                  | No          | `role`                   | Claim con el rol (texto o lista) |
| `INTEGRATION_CALLBACK_SECRET`     | Sí          | —                        | Secreto compartido con el Integration System para el callback (header `X-Callback-Secret`) |
| `LIA_GATEWAY_MODE`                | No          | `integration-system` (`stub` con el perfil `stub`) | `integration-system`, `direct-n8n` (solo local) o `stub` |
| `INTEGRATION_SYSTEM_URL`, `LIA_PATH` | En `integration-system` | —          | La solicitud va a `INTEGRATION_SYSTEM_URL` + `LIA_PATH` |
| `N8N_WEBHOOK_URL`                 | En `direct-n8n` | —                    | Webhook de producción del workflow de n8n |
| `MESSAGING_CALLBACK_URL`          | No          | `http://localhost:8083/messaging/callbacks/n8n` | Callback visto desde n8n (`direct-n8n`) |
| `LIA_TIMEOUT_SECONDS`             | No          | `20`                     | Tiempo que tiene Lía para responder; pasado ese tiempo, el explorador recibe el aviso con la reserva manual |
| `LIA_REQUEST_TIMEOUT`             | No          | `5s`                     | Tiempo máximo de la llamada que *entrega* la solicitud a Lía |
| `LIA_STUB_DELAY`                  | No          | `1s`                     | Espera del modo `stub` antes de responder |
| `LIA_TOKEN_CACHE_MARGIN_SECONDS`  | No          | `60`                     | El JWT del explorador se guarda en memoria `LIA_TIMEOUT_SECONDS` + este margen (o hasta que venza) |
| `LIA_TIMEOUT_JOB_INTERVAL_MS`     | No          | `10000`                  | Cada cuánto revisa los mensajes sin respuesta |
| `LIA_TIMEOUT_LOOKBACK_HOURS`      | No          | `24`                     | Hasta cuántas horas atrás busca mensajes sin respuesta |
| `LIA_TIMEOUT_JOB_BATCH_SIZE`      | No          | `50`                     | Máximo de avisos por revisión |
| `MESSAGING_MAX_LENGTH`            | No          | `1000`                   | Longitud máxima de un mensaje |
| `MESSAGING_RADAR_K_RING`          | No          | `2`                      | k-ring con el que se consulta a Radar |
| `RADAR_URL`, `BUSINESS_URL`, `RESERVATION_URL` | Sin los perfiles `stub` ni `local` | — (perfil `local`: `http://localhost:8082`, `:8080` y `:8084`) | URL base de cada contexto (timeout de 3 s; propagan el JWT del explorador) |
| `MESSAGING_WS_ALLOWED_ORIGINS`    | No          | `*`                      | Orígenes permitidos del handshake WebSocket |
| `SERVER_PORT`                     | No          | `8083`                   | Puerto HTTP |

Perfil de Spring `stub` (`SPRING_PROFILES_ACTIVE=stub`): reemplaza los clientes de Radar, Business y Reservation por
datos de ejemplo de Lima y pone `LIA_GATEWAY_MODE=stub` si no se indica otro. **No es para producción.**

Perfil de Spring `local` (`SPRING_PROFILES_ACTIVE=local`): los demás servicios corren en su equipo. `RADAR_URL`,
`BUSINESS_URL` y `RESERVATION_URL` valen por defecto `http://localhost:8082`, `http://localhost:8080` y
`http://localhost:8084`, el mapa de puertos del proyecto: Business 8080, IAM 8081, Radar 8082, Messaging 8083 y
Reservation 8084. No cambia ningún secreto: `JWT_SECRET_BASE64` sigue siendo obligatoria.

Rutas de los contextos proveedores (configurables en `application.yml`, a confirmar con cada contexto):
Radar `POST /radar/search`; Business `GET /business-api/huariques/{id}` y `.../{id}/menu`; Reservation
`POST /reservation-api/reservations` y `GET /reservation-api/reservations/{id}`.

## JWT de IAM: validación y reenvío a otros servicios

Messaging valida el JWT por su cuenta, en las dos puertas con la misma regla (`JwtTokenValidator`): el filtro REST y
el frame STOMP `CONNECT`. Exige firma HS256 con `JWT_SECRET_BASE64` (Base64 decodificado a bytes, mínimo 32, igual que
`JwtTokenIssuer` de IAM), expiración, `iss` igual a `JWT_ISSUER` (`foodly-iam`) y que `aud` incluya `JWT_AUDIENCE`
(`foodly-messaging`). Un token sin `iss` o `aud` (el formato anterior de IAM) responde 401 en REST y cierra la conexión
STOMP.

**El token se reenvía.** `ExplorerTokenCache` guarda el JWT del explorador y los clientes de Radar, Business y
Reservation lo envían tal cual en `Authorization: Bearer`. Cada uno lo valida con su propia audiencia, así que el `aud`
que emite IAM debe incluir **todas**:

| Destino      | Audiencia que exige | Llamadas que le reenvían el token |
|--------------|---------------------|-----------------------------------|
| Messaging    | `foodly-messaging`  | (valida el token de entrada)      |
| Radar        | `foodly-radar`      | `POST /radar/search`              |
| Business     | `foodly-business`   | `GET /business-api/huariques/{id}` y `.../menu` |
| Reservation  | `foodly-reservation`| `POST /reservation-api/reservations`, `GET .../{id}` |

Verificado el 24 de noviembre de 2026 con la rama `feature/jwt-issuer-audience` de Foodly-IAM-Service: su
configuración por defecto (`JWT_AUDIENCES=foodly-iam,foodly-business,foodly-radar,foodly-messaging,foodly-reservation,foodly-community`)
emite un token con `iss=foodly-iam` y esos seis destinatarios, y `JwtTokenIssuer` los incluye en `aud`. **Esos cambios de
IAM aún no están en su `develop`**: hasta que se fusionen, IAM no emite `iss` ni `aud` y Messaging rechaza sus tokens.
Si alguien cambia `JWT_AUDIENCES` en IAM, debe conservar esos cuatro valores o el reenvío fallará con 401 en el servicio
que falte. Reservation todavía es un esqueleto: su audiencia está reservada en IAM pero aún no se probó contra su validación.

El token vive en el cache como máximo `LIA_TIMEOUT_SECONDS` más `LIA_TOKEN_CACHE_MARGIN_SECONDS` (o hasta que venza). Los
servicios destino aplican además su propia expiración.

## Cómo responde Lía

```
POST /messaging/chat ─▶ guarda el mensaje ─▶ (después del commit) ILiaAgentGateway ─▶ n8n + OpenAI
                                                                                          │
   STOMP  ◀── guarda la respuesta ◀── consulta Radar / Business / Reservation ◀── callback (intención + texto)
```

1. `SendMessageCommandHandler` guarda el mensaje y el JWT del explorador (solo en memoria) y responde `202`.
2. `N8nLiaAgentGateway` entrega la solicitud: al **Integration System** (`integration-system`, DD-07), directo a n8n
   (`direct-n8n`, solo local) o al stub. Solo envía la solicitud; la respuesta llega por el callback.
3. El callback entrega la **intención** (qué quiere el explorador) y el texto. `IntentRoutingService` decide si
   consultar a Radar, consultar a Business, reenviar la reserva a Reservation o pedir una aclaración, y Lía responde
   con el texto de n8n más los datos reales.
4. Si Lía no responde en `LIA_TIMEOUT_SECONDS`, `LiaTimeoutJob` (cada 10 s) registra el error y guarda una respuesta
   que ofrece la **reserva manual (RF-15)**. Es idempotente: una respuesta tardía no reemplaza ese aviso.
5. Si un proveedor falla, hay timeout o el JWT ya no está en memoria, Lía responde con un mensaje amable y, si era una
   reserva, ofrece la reserva manual.

Se asume **una sola instancia** (DD-10): el cache del token, el control de respuestas simultáneas y el job son locales
al proceso. Con varias instancias haría falta un lock distribuido (por ejemplo, ShedLock) y un cache compartido.

## Desarrollo

```bash
./mvnw test        # en Windows: mvnw.cmd test
```

Las pruebas de integración usan Testcontainers (MySQL 8) y se omiten si no hay Docker. Cubren el repositorio, el
flujo con los clientes de Lima, el canal STOMP con un cliente real y el job de timeout.

Para ejecutar el servicio desde el IDE o con Maven, levante solo MySQL y use el perfil `stub`:

```bash
docker compose -f docker-compose.local.yml up -d --wait mysql
MYSQL_URL='jdbc:mysql://localhost:3307/messaging?allowPublicKeyRetrieval=true' MYSQL_USER=messaging MYSQL_PASSWORD=messaging \
MYSQL_SSL_MODE=DISABLED JWT_SECRET_BASE64=<la misma clave que usa IAM> INTEGRATION_CALLBACK_SECRET=dev \
SPRING_PROFILES_ACTIVE=stub ./mvnw spring-boot:run
```

### Estructura

```
src/main/java/pe/foodly/messaging/
  domain/           Conversation, Message, objetos de valor, IntentRoutingService y los puertos; sin Spring ni JPA
  application/      commands, queries y sus handlers; puertos de salida hacia Radar, Business y Reservation
  interfaces/       rest (controllers, resources, errores) y websocket (STOMP)
  infrastructure/   persistence/jpa (MySQL), clients (RestClient), lia (n8n y stub), security (JWT),
                    scheduling (LiaTimeoutJob) y config
src/main/resources/db/migration/   migraciones de Flyway
```

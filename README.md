# API Gateway – La Vitrina Coleccionistas

Punto único de entrada a los microservicios. Recibe las peticiones del cliente, descubre el microservicio destino en **Eureka** y le reenvía la llamada.

**Stack:** Java 25 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 (Gateway WebFlux, Eureka Client, LoadBalancer) · Springdoc (Swagger UI)

## Puertos y orden de arranque

| Servicio | Puerto | Nombre en Eureka |
|---|---|---|
| Eureka Server | 8761 | `app-lavitrinacoleccionistas-eureka-server` |
| API Gateway | 8080 | `app-lavitrinacoleccionistas-api-gateway` |
| MS Intercambios y Soporte | 8081 | `app-lavitrinacoleccionistas-ms-intercambios-soporte` |

Orden: **1) Eureka → 2) microservicios → 3) Gateway**. Verificar en http://localhost:8761 que los servicios aparezcan como `UP`.

La URL de Eureka se toma de la variable `EUREKA_URL` (por defecto `http://localhost:8761/eureka`).

## Qué contiene

- **Rutas** (`application.yaml` y `config/ApiGatewayConfig.java`): reenvían por `lb://<nombre-en-eureka>`.
  - `/api/intercambios/**` y `/api/tickets-soporte/**` → MS Intercambios y Soporte.
  - `/docs/intercambios/v3/api-docs` → se reescribe a `/api/v3/api-docs` del MS (para el Swagger).
- **`LoggingFilter`**: filtro global pre/post; registra método, ruta, estado y tiempo de cada petición.
- **`GatewayErrorHandler`**: devuelve errores en JSON controlado (`status`, `message`, `path`) sin exponer trazas (404, 503, 504, 502).
- **`ApiGatewayController`**: orquestación. `GET /orquestacion/resumen` consulta intercambios y tickets en paralelo y devuelve ambos; si un servicio falla, responde `"Servicio no disponible"` solo para esa parte.
- **Filtros por defecto**: agrega el header `X-Gateway: lavitrina` a request y response.
- **Timeouts**: conexión 3 s, respuesta 10 s.
- **Swagger UI centralizado**: http://localhost:8080/swagger-ui.html
- Seguridad/JWT: pendiente, no implementada por ahora.

## Cómo conectar un microservicio

El gateway no conoce IPs: encuentra el MS por su **nombre en Eureka**. Para que conecte bien, el MS debe cumplir:

1. **Dependencias** en el `pom.xml`: `spring-cloud-starter-netflix-eureka-client` y `springdoc-openapi-starter-webmvc-api`.
2. **Nombre único** en `spring.application.name`. Debe coincidir exactamente con el de `lb://...` en la ruta del gateway.
3. **Registrarse en Eureka**:
   ```yaml
   eureka:
     client:
       service-url:
         defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
     instance:
       prefer-ip-address: true
   ```
4. **Context path `/api`** (`server.servlet.context-path: /api`). Los controllers se mapean sin `/api` (ej. `@RequestMapping("/intercambios")`), así el gateway reenvía la ruta completa sin reescribirla.
5. **Swagger apuntando al gateway**: `server.forward-headers-strategy: framework` y `@OpenAPIDefinition(servers = @Server(url = "/api"))`, para que "Try it out" pase por el gateway.
6. **Puerto propio** distinto a 8080 y 8761 (ej. 8081, 8082...).
7. **Registrar el MS en el gateway** (en `application.yaml` y/o `ApiGatewayConfig`):
   ```yaml
   - id: <ms>
     uri: lb://<spring.application.name del MS>
     predicates:
       - Path=/api/<recurso>/**
   - id: <ms>-docs
     uri: lb://<spring.application.name del MS>
     predicates:
       - Path=/docs/<ms>/v3/api-docs
     filters:
       - RewritePath=/docs/<ms>/(?<p>.*), /api/$\{p}
   ```
   y agregar la entrada en `springdoc.swagger-ui.urls`:
   ```yaml
   - name: <Nombre visible>
     url: /docs/<ms>/v3/api-docs
   ```
8. *(Opcional)* sumar la llamada en `ApiGatewayController` si el MS participa en la orquestación.

## Cómo probar que quedó bien

- Eureka (`:8761`) muestra el gateway y el MS en `UP`.
- `GET http://localhost:8080/api/intercambios` responde igual que `GET http://localhost:8081/api/intercambios`.
- `GET http://localhost:8080/orquestacion/resumen` devuelve `intercambios` y `tickets`.
- Con el MS apagado, el gateway responde `503` en JSON, sin traza.

## Problemas frecuentes

- **503 / "Servicio no disponible"**: el MS no está registrado en Eureka o el nombre de `lb://` no coincide con su `spring.application.name`. Esperar ~30 s tras iniciar, el registro no es instantáneo.
- **404 al pasar por el gateway pero 200 directo al MS**: falta el `/api` (context path) o el `Path=` de la ruta no cubre el recurso.
- **Swagger no carga la documentación**: revisar que el MS exponga `/api/v3/api-docs` y que la ruta `-docs` y el `RewritePath` estén bien.
- **El MS no arranca**: faltan variables en su `.env` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) o el JAR `common` no está instalado en Maven local.

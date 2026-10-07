# API Gateway – La Vitrina Coleccionistas

Punto único de entrada a los microservicios. Recibe las peticiones del cliente, descubre el microservicio destino en **Eureka** y le reenvía la llamada.

**Stack:** Java 25 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 (Gateway WebFlux, Eureka Client, LoadBalancer) · Springdoc (Swagger UI)

## Puertos y orden de arranque

| Servicio | Puerto | Nombre en Eureka |
|---|---|---|
| Eureka Server | 8761 | `app-lavitrinacoleccionistas-eureka-server` |
| API Gateway | 8080 | `app-lavitrinacoleccionistas-api-gateway` |
| MS Intercambios y Soporte | 8081 | `app-lavitrinacoleccionistas-ms-intercambios-soporte` |
| MS Catálogo | 8082 | `app-lavitrinacoleccionistas-ms-catalogo` |

Orden: **1) Eureka → 2) microservicios → 3) Gateway**. Verificar en http://localhost:8761 (el dashboard, **sin** `/eureka/` al final) que los servicios aparezcan como `UP`.

La URL de Eureka se toma de la variable `EUREKA_URL` (por defecto `http://localhost:8761/eureka`).

## Qué contiene

- **Rutas** (`application.yaml`): reenvían por `lb://<nombre-en-eureka>`.
    - `/api/intercambios/**` y `/api/tickets-soporte/**` → MS Intercambios y Soporte.
    - `/api/productos/**`, `/api/publicaciones/**` y `/api/ofertas/**` → MS Catálogo. Esto incluye los detalles (`/api/publicaciones/{id}/detalles`).
    - `/docs/intercambios/v3/api-docs` y `/docs/catalogo/v3/api-docs` → se reescriben a `/api/v3/api-docs` del MS correspondiente (para el Swagger).
    - Las rutas de intercambios también están declaradas en `config/ApiGatewayConfig.java`. Las del catálogo se definen **solo en el yaml**, para no duplicarlas.
- **`LoggingFilter`**: filtro global pre/post; registra método, ruta, estado y tiempo de cada petición.
- **`GatewayErrorHandler`**: devuelve errores en JSON controlado (`status`, `message`, `path`) sin exponer trazas (404, 503, 504, 502).
- **`ApiGatewayController`**: orquestación. `GET /orquestacion/resumen` consulta intercambios y tickets en paralelo y devuelve ambos; si un servicio falla, responde `"Servicio no disponible"` solo para esa parte. El catálogo todavía no participa en la orquestación.
- **Filtros por defecto**: agrega el header `X-Gateway: lavitrina` a request y response.
- **Timeouts**: conexión 3 s, respuesta 10 s.
- **Swagger UI centralizado**: http://localhost:8080/swagger-ui.html. En el selector de arriba a la derecha están **Intercambios y Soporte** y **Catalogo**.
- Seguridad/JWT: pendiente, no implementada por ahora.

## Cómo conectar un microservicio

El gateway no conoce IPs: encuentra el MS por su **nombre en Eureka**. Para que conecte bien, el MS debe cumplir:

1. **Dependencias** en el `pom.xml`:
    - `spring-cloud-starter-netflix-eureka-client`.
    - `springdoc-openapi-starter-webmvc-api` (o `springdoc-openapi-starter-webmvc-ui`, que ya lo incluye).
    - El BOM `spring-cloud-dependencies` en `dependencyManagement`, con la misma versión que el gateway (`2025.1.2`). El MS debe usar también Spring Boot 4.1.1.
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
5. **Swagger apuntando al gateway**: `server.forward-headers-strategy: framework` y `@OpenAPIDefinition(servers = @Server(url = "/api"))` en la clase principal, para que "Try it out" pase por el gateway.
6. **Puerto propio** distinto a 8080 y 8761 (ej. 8081, 8082...). Conviene que sea configurable: `server.port: ${SERVER_PORT:8082}`.
7. **Seguridad**: si el MS usa Spring Security, las rutas de Swagger (`/swagger-ui/**`, `/v3/api-docs/**`) deben estar permitidas. Los `requestMatchers` se escriben **sin** el `/api`, porque se evalúan relativos al context path.
8. **Registrar el MS en el gateway** (en `application.yaml`):
   ```yaml
   - id: <ms>
     uri: lb://<spring.application.name del MS>
     predicates:
       - Path=/api/<recurso1>/**,/api/<recurso2>/**
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
   Definir las rutas en **un solo lugar** (yaml o `ApiGatewayConfig`), no en ambos.
9. *(Opcional)* sumar la llamada en `ApiGatewayController` si el MS participa en la orquestación.

### Ejemplo: MS Catálogo

```yaml
# application.yaml del gateway
- id: catalogo
  uri: lb://app-lavitrinacoleccionistas-ms-catalogo
  predicates:
    - Path=/api/productos/**,/api/publicaciones/**,/api/ofertas/**
- id: catalogo-docs
  uri: lb://app-lavitrinacoleccionistas-ms-catalogo
  predicates:
    - Path=/docs/catalogo/v3/api-docs
  filters:
    - RewritePath=/docs/catalogo/(?<p>.*), /api/$\{p}
```

```yaml
# application.yml del MS Catálogo (fragmento)
server:
  port: ${SERVER_PORT:8082}
  servlet:
    context-path: /api
  forward-headers-strategy: framework

spring:
  application:
    name: ${APP_NAME:app-lavitrinacoleccionistas-ms-catalogo}
```

## Cómo probar que quedó bien

- Eureka (http://localhost:8761) muestra el gateway y los MS en `UP`.
- `GET http://localhost:8080/api/intercambios` responde igual que `GET http://localhost:8081/api/intercambios`.
- `GET http://localhost:8080/api/productos` responde igual que `GET http://localhost:8082/api/productos`.
- `GET http://localhost:8080/orquestacion/resumen` devuelve `intercambios` y `tickets`.
- http://localhost:8080/swagger-ui.html muestra los dos servicios en el selector y carga la documentación de cada uno.
- Con un MS apagado, el gateway responde `503` en JSON, sin traza.

Para Postman, la colección del catálogo usa la variable de entorno `baseUrl`:
- A través del gateway: `http://localhost:8080/api`.
- Directo al MS: `http://localhost:8082/api`.

## Problemas frecuentes

- **`http://localhost:8761/eureka/` muestra "Whitelabel Error Page" (404)**: es normal. Esa es la ruta de la API de registro, no una página. El dashboard está en `http://localhost:8761`.
- **El MS arranca pero el log se llena de `Connection refused` a `localhost:8761`**: Eureka no está levantado. El MS reintenta cada 30 s y se registra solo cuando Eureka esté disponible.
- **503 / "Servicio no disponible"**: el MS no está registrado en Eureka o el nombre de `lb://` no coincide con su `spring.application.name`. Esperar ~30 s tras iniciar, el registro no es instantáneo.
- **404 al pasar por el gateway pero 200 directo al MS**: falta el `/api` (context path) o el `Path=` de la ruta no cubre el recurso.
- **Swagger no carga la documentación**: revisar que el MS exponga `/api/v3/api-docs`, que la ruta `-docs` y el `RewritePath` estén bien, y que la entrada exista en `springdoc.swagger-ui.urls`.
- **401/403 al pasar por el gateway o en Swagger**: revisar la configuración de seguridad del MS (ver punto 7).
- **El MS no arranca**: faltan variables en su `.env` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), el JAR `common` no está instalado en Maven local, o el puerto ya está en uso por otro MS.
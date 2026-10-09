# API Gateway – La Vitrina Coleccionistas

Punto de entrada a los microservicios de La Vitrina Coleccionistas. Utiliza **Spring Cloud Gateway** para enrutar solicitudes y **Eureka** para descubrir los servicios por nombre. Centraliza el acceso a la documentación Swagger.

**Tecnologías:** Java 25 · Spring Boot 4.1.1 · Spring Cloud 2025.1.2 (Gateway WebFlux, Eureka Client, LoadBalancer) · Springdoc OpenAPI.

## Servicios y puertos

| Servicio | Puerto | Nombre de aplicación |
|---|---|---|
| Eureka Server | 8761 | `app-lavitrinacoleccionistas-eureka-server` |
| API Gateway | 8080 | `app-lavitrinacoleccionistas-api-gateway` |
| Intercambios y Soporte | 8081 | `app-lavitrinacoleccionistas-ms-intercambios-soporte` |
| Catálogo | 8082 | `app-lavitrinacoleccionistas-ms-catalogo` |

**Orden de inicio:** Eureka → microservicios → API Gateway. Consultar `http://localhost:8761` para comprobar el registro de instancias.

## Configuración

Toda la configuración del Gateway se define en `src/main/resources/application.yaml`:

- **Puerto:** `8080`.
- **Descubrimiento:** Eureka en `${EUREKA_URL:http://localhost:8761/eureka}`.
- **Enrutamiento:** direcciones `lb://` resueltas mediante Eureka y LoadBalancer.
- **Filtros globales declarativos:** `AddRequestHeader` y `AddResponseHeader`, con `X-Gateway: lavitrina`.
- **Filtros de documentación:** `RewritePath` para las rutas de OpenAPI.
- **Timeouts:** conexión de 3000 ms y respuesta de 10 s.
- **Logging:** nivel `INFO` para `org.springframework.cloud.gateway`.

No se utilizan controladores de orquestación, filtros Java personalizados ni manejadores de excepciones personalizados en el Gateway. Cada microservicio es responsable de su lógica de negocio y del tratamiento de errores de su API.

## Rutas

| Ruta de entrada al Gateway | Microservicio destino |
|---|---|
| `/api/productos/**` | Catálogo |
| `/api/publicaciones/**` | Catálogo |
| `/api/ofertas/**` | Catálogo |
| `/api/intercambios/**` | Intercambios y Soporte |
| `/api/tickets-soporte/**` | Intercambios y Soporte |
| `/docs/catalogo/v3/api-docs` | Catálogo (`/api/v3/api-docs`) |
| `/docs/intercambios/v3/api-docs` | Intercambios y Soporte (`/api/v3/api-docs`) |

Las rutas se configuran exclusivamente en YAML, sin una clase `RouteLocator` adicional.

## Swagger / OpenAPI

**Interfaz centralizada:** `http://localhost:8080/swagger-ui.html`

El selector permite consultar **Catálogo** e **Intercambios y Soporte**. Cada microservicio debe exponer su especificación OpenAPI en la ruta esperada por el Gateway (`/api/v3/api-docs`). La interfaz Swagger se centraliza en el Gateway; las especificaciones las generan los microservicios.

## Ejecución y verificación

1. Iniciar Eureka Server y los dos microservicios.
2. Iniciar el API Gateway.
3. Comprobar en `http://localhost:8761` que los servicios estén registrados como `UP`.
4. Comprobar las rutas de API, por ejemplo `http://localhost:8080/api/productos` y `http://localhost:8080/api/intercambios`.
5. Abrir `http://localhost:8080/swagger-ui.html` y seleccionar cada microservicio.
6. Si Swagger no carga, comprobar primero las rutas `/api/v3/api-docs` de cada microservicio y luego las rutas `/docs/...` del Gateway.

## Requisitos de integración de nuevos microservicios

- Registrar el servicio en Eureka con un `spring.application.name` que coincida con el `lb://` configurado.
- Publicar los endpoints REST en las rutas esperadas por el Gateway.
- Exponer el documento OpenAPI en una ruta accesible desde el Gateway.
- Agregar en `application.yaml` una ruta de API, otra de documentación con `RewritePath` y una entrada en `springdoc.swagger-ui.urls`.

## Problemas frecuentes

- **503:** comprobar que el microservicio esté iniciado, registrado en Eureka y que su nombre coincida con el de `lb://`.
- **404:** comprobar que el endpoint solicitado y el prefijo de la ruta coincidan con los del microservicio.
- **Swagger no carga:** comprobar la ruta real del documento OpenAPI en el microservicio y el `RewritePath` correspondiente.
- **Eureka no conecta:** comprobar que el servidor esté disponible en la URL configurada mediante `EUREKA_URL`.

package py.com.lavitrinacoleccionistas.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiGatewayConfig {

    @Bean
    public RouteLocator customerRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("intercambios-soporte", r -> r
                        .path("/api/intercambios/**",
                                "/api/tickets-soporte/**")
                        .uri("lb://app-lavitrinacoleccionistas-ms-intercambios-soporte"))

                .route("intercambios-docs", r -> r
                        .path("/docs/intercambios/v3/api-docs")
                        .filters(f -> f.rewritePath(
                                "/docs/intercambios/(?<p>.*)",
                                "/api/${p}"
                        ))
                        .uri("lb://app-lavitrinacoleccionistas-ms-intercambios-soporte"))

                .build();
    }
}
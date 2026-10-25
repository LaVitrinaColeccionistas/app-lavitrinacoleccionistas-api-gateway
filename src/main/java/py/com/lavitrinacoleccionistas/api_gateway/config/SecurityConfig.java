package py.com.lavitrinacoleccionistas.api_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    @Bean
    ReactiveJwtDecoder jwtDecoder(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.issuer}") String issuer) {

        byte[] decoded = Base64.getDecoder().decode(secret);
        var key = new SecretKeySpec(decoded, "HmacSHA256");

        var decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((exchange, ex) ->
                                json(exchange, HttpStatus.UNAUTHORIZED, "Token inválido, expirado o ausente"))
                        .accessDeniedHandler((exchange, ex) ->
                                json(exchange, HttpStatus.FORBIDDEN, "No tienes permisos para este recurso")))
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(
                                "/api/auth/login",
                                "/api/auth/validate",
                                "/api/auth/register",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/webjars/**",
                                "/v3/api-docs/**",
                                "/docs/**"
                        ).permitAll()

                        // Catalogo publico
                        .pathMatchers(HttpMethod.GET, "/api/productos/**", "/api/publicaciones/**").permitAll()

                        // Productos y publicaciones
                        .pathMatchers(HttpMethod.DELETE, "/api/productos/**").hasRole("ADMIN")
                        .pathMatchers("/api/productos/**", "/api/publicaciones/**").hasAnyRole("ADMIN", "VENDEDOR")

                        // Ofertas
                        .pathMatchers(HttpMethod.POST, "/api/ofertas/*/aceptar", "/api/ofertas/*/rechazar")
                        .hasAnyRole("ADMIN", "VENDEDOR")
                        .pathMatchers(HttpMethod.POST, "/api/ofertas").hasAnyRole("ADMIN", "COMPRADOR")
                        .pathMatchers(HttpMethod.PUT, "/api/ofertas/*").hasAnyRole("ADMIN", "COMPRADOR")
                        .pathMatchers(HttpMethod.GET, "/api/ofertas/**").hasAnyRole("ADMIN", "VENDEDOR", "COMPRADOR")

                        .pathMatchers("/api/usuarios/**", "/api/roles/**").hasRole("ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth
                        .authenticationEntryPoint((exchange, ex) ->
                                json(exchange, HttpStatus.UNAUTHORIZED, "Token inválido, expirado o ausente"))
                        .accessDeniedHandler((exchange, ex) ->
                                json(exchange, HttpStatus.FORBIDDEN, "No tienes permisos para este recurso"))
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                            List<String> roles = Optional
                                    .ofNullable(token.getClaimAsStringList("roles"))
                                    .orElse(List.of());
                            List<SimpleGrantedAuthority> authorities = roles.stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                    .toList();

                            return Mono.just(new JwtAuthenticationToken(token, authorities));
                        }))
                )
                .build();
    }

    private static Mono<Void> json(ServerWebExchange ex, HttpStatus status, String msg) {
        var res = ex.getResponse();
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(LocalDateTime.now(), status.value(), status.getReasonPhrase(), msg);
        DataBuffer buf = res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return res.writeWith(Mono.just(buf));
    }
}
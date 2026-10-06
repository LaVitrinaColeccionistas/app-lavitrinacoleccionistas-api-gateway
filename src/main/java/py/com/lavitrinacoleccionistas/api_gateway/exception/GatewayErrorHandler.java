package py.com.lavitrinacoleccionistas.api_gateway.exception;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/*
* Cumple la parte de gestión de errores.
Devuelve respuestas controladas en vez de exponer trazas
* */
@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange ex, Throwable t) {
        log.error("Error en {} {}: {}", ex.getRequest().getMethod(),
                ex.getRequest().getPath(), t.toString());

        HttpStatusCode st = (t instanceof ResponseStatusException r)
                ? r.getStatusCode() : HttpStatus.BAD_GATEWAY;

        String msg = switch (st.value()) {
            case 404 -> "Recurso no encontrado";
            case 503 -> "Servicio no disponible";
            case 504 -> "Tiempo de espera agotado";
            default -> st.is4xxClientError()
                    ? "Solicitud inválida"
                    : "Error al comunicarse con el servicio";
        };

        var res = ex.getResponse();
        res.setStatusCode(st);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":%d,\"message\":\"%s\",\"path\":\"%s\"}"
                .formatted(st.value(), msg, ex.getRequest().getPath().value());
        return res.writeWith(Mono.just(
                res.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }
}
package py.com.lavitrinacoleccionistas.api_gateway.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/*
Implementa /orquestacion/resumen.
Es necesario porque la consigna exige orquestación de múltiples microservicios
*/
@RestController
@RequestMapping("/orquestacion")
public class ApiGatewayController {

    private static final String MS = "lb://app-lavitrinacoleccionistas-ms-intercambios-soporte/api";
    private final WebClient client;

    public ApiGatewayController(WebClient.Builder builder) {
        this.client = builder.baseUrl(MS).build();
    }

    @GetMapping("/resumen")
    public Mono<Map<String, Object>> resumen() {
        return Mono.zip(llamar("/intercambios"), llamar("/tickets-soporte"))
                .map(t -> Map.of("intercambios", t.getT1(), "tickets", t.getT2()));
    }

    private Mono<Object> llamar(String path) {
        return client.get().uri(path).retrieve().bodyToMono(Object.class)
                .onErrorResume(e -> Mono.just(Map.of("error", "Servicio no disponible")));
    }
}
package py.com.lavitrinacoleccionistas.api_gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/*
* Es un filtro global útil para pre/post procesamiento.
Cumple con la parte de filtros de pre/post procesamiento.
* */
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange ex, GatewayFilterChain chain) {
        long inicio = System.currentTimeMillis();
        log.info("--> {} {}", ex.getRequest().getMethod(), ex.getRequest().getURI().getPath());
        return chain.filter(ex).then(Mono.fromRunnable(() ->
                log.info("<-- {} {} ms", ex.getResponse().getStatusCode(),
                        System.currentTimeMillis() - inicio)));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
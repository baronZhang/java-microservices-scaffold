package com.wd.ms.gateway.web;

import com.wd.ms.common.core.ErrorCode;
import com.wd.ms.common.core.Result;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/** 网关自己产生的响应（401、429、503……）也遵守统一的 {@code {code, message, data}} 契约。 */
public class JsonResponses {

    private final JsonMapper jsonMapper;

    public JsonResponses(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, ErrorCode code, String message) {
        return write(exchange, HttpStatus.valueOf(code.httpStatus()), Result.fail(code, message));
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Result<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(jsonMapper.writeValueAsBytes(body));
        return response.writeWith(Mono.just(buffer));
    }
}

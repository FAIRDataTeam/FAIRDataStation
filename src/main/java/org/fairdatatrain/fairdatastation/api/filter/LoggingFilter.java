/**
 * The MIT License
 * Copyright © 2022 FAIR Data Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.fairdatatrain.fairdatastation.api.filter;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static java.lang.String.format;

@Slf4j
@Component
public class LoggingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        ThreadContext.put("traceId", UUID.randomUUID().toString());
        ThreadContext.put("ipAddress", toStringIfNotNull(request.getRemoteAddress()));
        ThreadContext.put("requestMethod", toStringIfNotNull(request.getMethod()));
        ThreadContext.put("requestURI", request.getURI().toString());

        log.info(format("%s %s", request.getMethod(), request.getURI()));

        Mono<Void> mono = chain.filter(exchange);

        ThreadContext.put("responseStatus", toStringIfNotNull(response.getStatusCode()));
        ThreadContext.put("contentSize", toStringIfNotNull(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH)));
        log.info(format(
                "%s %s [Response: %s]",
                request.getMethod(), request.getURI(), response.getStatusCode())
        );

        return mono;
    }

    private String toStringIfNotNull(Object object) {
        if (object == null) {
            return "";
        }
        return object.toString();
    }
}

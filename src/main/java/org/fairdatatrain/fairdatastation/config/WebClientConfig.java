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
package org.fairdatatrain.fairdatastation.config;

import org.fairdatatrain.fairdatastation.service.interaction.fetch.FetchGuard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;

import java.time.Duration;

@Configuration
public class WebClientConfig {
    private static final long TIMEOUT = 5 * 60;

    @Bean
    public WebClient webClient() {
        final HttpClient client = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(TIMEOUT));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client))
                .build();
    }

    /**
     * The client used to fetch train and payload documents, whose URIs are
     * supplied by a dispatching handler.
     *
     * <p>It differs from {@link #webClient()} in two ways that matter, and both
     * exist because the target is chosen by the caller rather than by this
     * station. Redirects are re-checked hop by hop: a guard that only inspects
     * the URI it was given is defeated by a permitted host answering
     * {@code 302 Location: http://169.254.169.254/}, so the destination of every
     * hop must pass the same test as the first. And the response body is bounded
     * by the codec, so an over-sized document is refused while being read rather
     * than after it has already been held in memory.
     */
    @Bean
    public WebClient trainFetchWebClient(FetchGuard fetchGuard) {
        final HttpClient client = HttpClient.create()
                .followRedirect((request, response) -> {
                    return redirectPermitted(fetchGuard, response);
                })
                .responseTimeout(Duration.ofSeconds(TIMEOUT));

        final WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client));

        final long maxBytes = fetchGuard.maxBytes();
        if (maxBytes > 0 && maxBytes <= Integer.MAX_VALUE) {
            builder.codecs(codecs -> {
                codecs.defaultCodecs().maxInMemorySize((int) maxBytes);
            });
        }
        return builder.build();
    }

    private boolean redirectPermitted(FetchGuard fetchGuard, HttpClientResponse response) {
        final String location = response.responseHeaders().get(HttpHeaders.LOCATION);
        if (location == null) {
            return false;
        }
        // Throws FetchNotPermittedException, which surfaces as the job's failure
        // reason, rather than silently declining to follow.
        fetchGuard.checkRedirectPermitted(location);
        return true;
    }
}

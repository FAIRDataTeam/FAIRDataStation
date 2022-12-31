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
package org.fairdatatrain.fairdatastation.service.interaction.train.fhir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.config.properties.FHIRProperties;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.request.FHIRPreparedRequest;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.request.FHIRRequest;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.request.FHIRRequestWrapper;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.response.FHIRResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

@Slf4j
@Component
@RequiredArgsConstructor
public class FHIRClient {

    private final FHIRProperties fhirProperties;

    private final WebClient webClient;

    private final ObjectMapper objectMapper;

    public boolean isReady() {
        return fhirProperties.getBase() != null;
    }

    public FHIRResponse send(FHIRPreparedRequest request) {
        log.debug("Sending FHIR {} request: {}", request.getMethod().name(), request.getUri());
        try {
            final ResponseEntity<String> response = webClient
                    .method(request.getMethod())
                    .uri(URI.create(request.getUri()))
                    .headers(headers -> request.getHeaders().forEach(headers::set))
                    .accept(MediaType.valueOf("application/fhir+json"))
                    .acceptCharset(StandardCharsets.UTF_8)
                    .retrieve()
                    .toEntity(String.class)
                    .block();
            if (response == null) {
                log.warn("No response from FHIR API for {}", request.getUri());
                throw new HttpClientErrorException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to get response from FHIR API"
                );
            }
            return FHIRResponse
                    .builder()
                    .statusCode(response.getStatusCode())
                    .headers(response.getHeaders())
                    .body(request.getBody())
                    .build();
        }
        catch (WebClientException exception) {
            log.warn("Request failed to FHIR API for {}: {}", request.getUri(), exception);
            throw new RuntimeException(
                    format("Execution: Failed to communicate with FHIR API (%s)",
                            ofNullable(exception.getMessage()).orElse("Uknown error")));
        }
    }

    public FHIRPreparedRequest parseRequest(String payload) {
        try {
            final FHIRRequestWrapper fhirRequestWrapper =
                    objectMapper.readValue(payload, FHIRRequestWrapper.class);
            return toPreparedRequest(fhirRequestWrapper.getApiRequest());
        }
        catch (JsonProcessingException exception) {
            throw new RuntimeException(exception);
        }
    }

    private FHIRPreparedRequest toPreparedRequest(FHIRRequest request) {
        final String uri = composeUri(request);
        return FHIRPreparedRequest
                .builder()
                .method(HttpMethod.valueOf(request.getMethod().toUpperCase()))
                .uri(uri)
                .body(request.getBody())
                .headers(request.getHeaders())
                .build();
    }

    private String composeUri(FHIRRequest request) {
        final UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(fhirProperties.getBase());
        builder.path(request.getResource());
        request.getParameters().forEach(param -> {
            if (param.getIsMandatory() || param.getValue() != null) {
                builder.queryParam(param.getName(), param.getValue());
            }
        });
        builder.queryParam("_pretty", true);
        builder.queryParam("_format", "json");
        return builder.build().toUriString();
    }
}

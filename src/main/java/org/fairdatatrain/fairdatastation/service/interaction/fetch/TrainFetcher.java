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
package org.fairdatatrain.fairdatastation.service.interaction.fetch;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.net.URI;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;
import static org.fairdatatrain.fairdatastation.utils.RdfUtils.read;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrainFetcher {

    private static final String MSG_MKRQ = "Making request to '%s'";
    private static final String MSG_RCV = "Request to '%s' successfully received";
    private static final String MSG_PARSE = "Request to '%s' successfully parsed";
    private static final String MSG_FAIL = "Request to '%s' failed";
    private static final String MSG_ERROR = "HTTP request failed";

    private final WebClient webClient;

    public Model fetchTrainMetadata(String trainUri) {
        return fetchModel(trainUri);
    }

    public Model fetchPayloadMetadata(String payloadUri) {
        return fetchModel(payloadUri);
    }

    public String fetchSimplePayload(String uri) {
        return fetchStringData(uri);
    }

    @SneakyThrows
    public Model fetchModel(String uri) {
        log.info(format(MSG_MKRQ, uri));
        try {
            final String response = webClient
                    .get()
                    .uri(URI.create(uri))
                    .accept(MediaType.parseMediaType(RDFFormat.TURTLE.getDefaultMIMEType()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info(format(MSG_RCV, uri));
            final Model result = read(response, uri, RDFFormat.TURTLE);
            log.info(format(MSG_PARSE, uri));
            return result;
        }
        catch (WebClientException exception) {
            log.info(format(MSG_FAIL, uri));
            throw new HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ofNullable(exception.getMessage()).orElse(MSG_ERROR)
            );
        }
    }

    @SneakyThrows
    public String fetchStringData(String uri) {
        log.info(format(MSG_MKRQ, uri));
        try {
            final String response = webClient
                    .get()
                    .uri(URI.create(uri))
                    .accept(MediaType.TEXT_PLAIN)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info(format(MSG_RCV, uri));
            return response;
        }
        catch (WebClientException exception) {
            log.info(format(MSG_FAIL, uri));
            throw new HttpClientErrorException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ofNullable(exception.getMessage()).orElse(MSG_ERROR)
            );
        }
    }
}

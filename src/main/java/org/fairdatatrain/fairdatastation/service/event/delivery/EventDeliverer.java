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
package org.fairdatatrain.fairdatastation.service.event.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDispatchDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDispatchDTO;
import org.fairdatatrain.fairdatastation.data.model.event.EventDelivery;
import org.fairdatatrain.fairdatastation.data.model.event.JobArtifact;
import org.fairdatatrain.fairdatastation.data.model.event.JobEvent;
import org.fairdatatrain.fairdatastation.service.event.job.artifact.JobArtifactService;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.sql.Timestamp;
import java.util.List;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeliverer {

    private final EventDeliveryService eventDeliveryService;

    private final JobArtifactService jobArtifactService;

    private final JobEventService jobEventService;

    private final WebClient webClient;

    private final CallbackSigner callbackSigner;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(JobArtifact jobArtifact, EventDelivery eventDelivery) {
        log.debug("Delivering job artifact {}", jobArtifact.getUuid());
        final JobArtifactDispatchDTO dto = jobArtifactService
                .getMapper()
                .toDispatchDTO(jobArtifact);
        deliver(
                eventDelivery,
                jobArtifact.getJob().getCallbackArtifact(),
                dto,
                callbackSigner.isEnabled()
                        ? callbackSigner.signArtifact(
                                jobArtifact.getJob().getRemoteId(), dto)
                        : null
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(JobEvent jobEvent, EventDelivery eventDelivery) {
        log.debug("Delivering job event {}", jobEvent.getUuid());
        final JobEventDispatchDTO dto = jobEventService
                .getMapper()
                .toDispatchDTO(jobEvent);
        deliver(
                eventDelivery,
                jobEvent.getJob().getCallbackEvent(),
                dto,
                callbackSigner.isEnabled()
                        ? callbackSigner.signEvent(
                                jobEvent.getJob().getRemoteId(), dto)
                        : null
        );
    }

    protected void deliver(
            EventDelivery eventDelivery,
            String uri,
            Object payload,
            CallbackSigner.SignedHeaders signature
    ) {
        final Timestamp dispatchedAt = now();
        try {
            dispatch(uri, payload, signature);
            eventDeliveryService.updateSuccess(eventDelivery, dispatchedAt);
        }
        catch (Exception exception) {
            log.debug("Exception while dispatching artifact", exception);
            log.warn("Failed to dispatch artifact: {}", exception.getMessage());
            eventDeliveryService.updateFailed(eventDelivery, dispatchedAt);
            eventDeliveryService.createNextDelivery(eventDelivery);
        }
    }

    private void dispatch(String uri, Object payload, CallbackSigner.SignedHeaders signature) {
        log.debug("Dispatching payload to {}", uri);
        try {
            webClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (signature != null) {
                            headers.set(CallbackSigner.HEADER_ID, signature.identity());
                            headers.set(
                                    CallbackSigner.HEADER_TIMESTAMP,
                                    Long.toString(signature.timestamp())
                            );
                            headers.set(CallbackSigner.HEADER_SIGNATURE, signature.signature());
                        }
                    })
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        }
        catch (WebClientException exception) {
            log.warn(format(
                    "Dispatching event failed: %s", exception.getMessage()
            ));
            throw new RuntimeException(
                    "Station responded with status: " + exception.getMessage()
            );
        }
    }

    public List<EventDelivery> getNextEventDeliveries() {
        return eventDeliveryService.getNextEventDeliveries();
    }
}

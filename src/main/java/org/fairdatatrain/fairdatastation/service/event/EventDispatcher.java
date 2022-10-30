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
package org.fairdatatrain.fairdatastation.service.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDispatchDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDispatchDTO;
import org.fairdatatrain.fairdatastation.data.model.event.JobArtifact;
import org.fairdatatrain.fairdatastation.data.model.event.JobEvent;
import org.fairdatatrain.fairdatastation.service.event.job.artifact.JobArtifactService;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventService;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.util.Optional;

import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDispatcher {

    private final JobArtifactService jobArtifactService;

    private final JobEventService jobEventService;

    private final WebClient webClient;

    // TODO: config
    // TODO: multi-thread? configurable?
    @Async
    @Scheduled(
            initialDelayString = "${dispatcher.dispatch.initDelay:PT10S}",
            fixedRateString = "${dispatcher.dispatch.interval:PT10S}"
    )
    @Transactional
    public void processJobs() {
        final Optional<JobArtifact> artifact = jobArtifactService.getNextToDispatch();
        if (artifact.isPresent()) {
            tryArtifact(artifact.get());
        }
        else {
            final Optional<JobEvent> event = jobEventService.getNextToDispatch();
            if (event.isPresent()) {
                tryEvent(event.get());
            }
        }
    }

    private void tryArtifact(JobArtifact artifact) {
        log.info("Dispatching artifact {}", artifact.getUuid());
        final JobArtifactDispatchDTO dto = jobArtifactService
                .getMapper()
                .toDispatchDTO(artifact);
        try {
            dispatch(artifact.getJob().getCallbackArtifact(), dto);
            jobArtifactService.updateDispatch(artifact, true);
        }
        catch (Exception exception) {
            log.debug("Exception while dispatching artifact", exception);
            log.warn("Failed to dispatch artifact: {}", exception.getMessage());
            jobArtifactService.updateDispatch(artifact, false);
        }
    }

    private void tryEvent(JobEvent event) {
        log.info("Dispatching event {}", event.getUuid());
        final JobEventDispatchDTO dto = jobEventService
                .getMapper()
                .toDispatchDTO(event);
        try {
            dispatch(event.getJob().getCallbackEvent(), dto);
            jobEventService.updateDispatch(event, true);
        }
        catch (Exception exception) {
            log.debug("Exception while dispatching event", exception);
            log.warn("Failed to dispatch event: {}", exception.getMessage());
            jobEventService.updateDispatch(event, false);
        }
    }

    private void dispatch(String uri, Object payload) {
        // TODO: logging
        try {
            webClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
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
}

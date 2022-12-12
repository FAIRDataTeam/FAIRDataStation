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
import org.fairdatatrain.fairdatastation.data.model.event.EventDelivery;
import org.fairdatatrain.fairdatastation.data.model.event.JobArtifact;
import org.fairdatatrain.fairdatastation.data.model.event.JobEvent;
import org.fairdatatrain.fairdatastation.data.repository.event.EventDeliveryRepository;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventDeliveryService {

    private static final int MAX_RETRIES = 5;

    private final EventDeliveryRepository eventDeliveryRepository;

    public void createInitialDelivery(JobEvent jobEvent) {
        final EventDelivery eventDelivery = eventDeliveryRepository.saveAndFlush(
                prepareInitialDelivery()
                    .toBuilder()
                    .jobEvent(jobEvent)
                    .build());
        log.info("Created initial event delivery {} for job event {}",
                eventDelivery.getUuid(), jobEvent.getUuid());
    }

    public void createInitialDelivery(JobArtifact jobArtifact) {
        final EventDelivery eventDelivery = eventDeliveryRepository.saveAndFlush(
                prepareInitialDelivery()
                        .toBuilder()
                        .jobArtifact(jobArtifact)
                        .build());
        log.info("Created initial event delivery {} for job artifact {}",
                eventDelivery.getUuid(), jobArtifact.getUuid());
    }

    public void updateSuccess(EventDelivery eventDelivery, Timestamp dispatchedAt) {
        final EventDelivery updatedDelivery = eventDelivery
                .toBuilder()
                .dispatchedAt(dispatchedAt)
                .delivered(true)
                .updatedAt(now())
                .build();
        eventDeliveryRepository.saveAndFlush(updatedDelivery);
    }

    public void updateFailed(EventDelivery eventDelivery, Timestamp dispatchedAt) {
        final EventDelivery updatedDelivery = eventDelivery
                .toBuilder()
                .dispatchedAt(dispatchedAt)
                .delivered(false)
                .updatedAt(now())
                .build();
        eventDeliveryRepository.saveAndFlush(updatedDelivery);
    }

    public void createNextDelivery(EventDelivery eventDelivery) {
        final Timestamp now = now();
        final int retry = eventDelivery.getRetryNumber() + 1;
        if (retry > MAX_RETRIES) {
            log.info("Event delivery reached maximal retried ({})",
                    eventDelivery.getUuid());
            return;
        }
        final long minutesNext = (long) Math.pow(2, retry);
        final Instant nextAt = Instant.now().plus(Duration.ofMinutes(minutesNext));
        final EventDelivery nextDelivery = EventDelivery
                .builder()
                .uuid(UUID.randomUUID())
                .retryNumber(retry)
                .priority(0)
                .delivered(false)
                .message("")
                .dispatchAt(Timestamp.from(nextAt))
                .dispatchedAt(null)
                .jobEvent(eventDelivery.getJobEvent())
                .jobArtifact(eventDelivery.getJobArtifact())
                .createdAt(now)
                .updatedAt(now)
                .build();
        eventDeliveryRepository.saveAndFlush(nextDelivery);
    }

    private EventDelivery prepareInitialDelivery() {
        final Timestamp now = now();
        return EventDelivery
                .builder()
                .uuid(UUID.randomUUID())
                .retryNumber(0)
                .priority(0)
                .delivered(false)
                .message("")
                .dispatchAt(now)
                .dispatchedAt(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public List<EventDelivery> getNextEventDeliveries() {
        final Timestamp now = now();
        return eventDeliveryRepository.getNextEventDeliveries(now);
    }

}

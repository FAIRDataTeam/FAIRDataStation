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
package org.fairdatatrain.fairdatastation.service.event.job.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDTO;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.data.model.event.JobEvent;
import org.fairdatatrain.fairdatastation.data.repository.event.JobEventRepository;
import org.fairdatatrain.fairdatastation.exception.NotFoundException;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobEventService {

    private static final int MAX_TRIES_DISPATCH = 5;

    private final JobEventRepository jobEventRepository;

    private final JobEventMapper jobEventMapper;

    private final JobService jobService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<JobEventDTO> getEventsForJob(UUID jobUuid) throws NotFoundException {
        final Job job = jobService.getByIdOrThrow(jobUuid);
        return job
                .getEvents()
                .stream()
                .map(jobEventMapper::toDTO)
                .toList();
    }

    public void createEvent(Job job, String message) {
        createEvent(job, message, null);
    }

    @Transactional
    public void createEvent(Job job, String message, JobStatus status) {
        final JobEvent jobEvent = jobEventRepository.saveAndFlush(
                jobEventMapper.create(job, message, status)
        );
        log.info("Created event {} for job {}", jobEvent.getUuid(), job.getUuid());
    }

    public Optional<JobEvent> getNextToDispatch() {
        // TODO: priority?
        // CHECKSTYLE.OFF: LineLength
        return jobEventRepository.findFirstByDeliveredIsFalseAndNextDispatchAtIsNotNullAndNextDispatchAtIsBeforeOrderByNextDispatchAtAsc(now());
        // CHECKSTYLE.ON: LineLength
    }

    public void updateDispatch(JobEvent jobEvent, Boolean delivered) {
        updateDispatch(jobEvent, delivered, null);
    }

    @Transactional
    public void updateDispatch(JobEvent jobEvent, Boolean delivered, String error) {
        jobEvent.setDelivered(delivered);
        if (delivered) {
            jobEvent.setNextDispatchAt(null);
            jobEvent.setLastError(null);
        }
        else {
            final int tries = jobEvent.getTries() + 1;
            jobEvent.setTries(tries);
            jobEvent.setLastError(error);
            if (tries >= MAX_TRIES_DISPATCH) {
                jobEvent.setNextDispatchAt(null);
            }
            else {
                final long minutesNext = (long) Math.pow(2, tries);
                final Instant nextAt = Instant.now().plus(Duration.ofMinutes(minutesNext));
                jobEvent.setNextDispatchAt(Timestamp.from(nextAt));
            }
        }
        jobEvent.setUpdatedAt(now());
        jobEventRepository.saveAndFlush(jobEvent);
    }

    public JobEventMapper getMapper() {
        return jobEventMapper;
    }
}

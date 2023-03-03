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
import org.fairdatatrain.fairdatastation.service.event.delivery.EventDeliveryService;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobEventService {

    private final JobEventRepository jobEventRepository;

    private final JobEventMapper jobEventMapper;

    private final JobService jobService;

    private final EventDeliveryService eventDeliveryService;

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
        eventDeliveryService.createInitialDelivery(jobEvent);
        log.info("Created event {} for job {}", jobEvent.getUuid(), job.getUuid());
    }

    public JobEventMapper getMapper() {
        return jobEventMapper;
    }
}

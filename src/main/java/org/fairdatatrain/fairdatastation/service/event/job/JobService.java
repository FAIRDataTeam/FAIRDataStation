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
package org.fairdatatrain.fairdatastation.service.event.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.JobDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.JobSimpleDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.train.TrainDispatchPayloadDTO;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.data.repository.event.JobRepository;
import org.fairdatatrain.fairdatastation.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private static final String ENTITY_NAME = "Job";

    private final JobRepository jobRepository;

    private final JobMapper jobMapper;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Page<JobSimpleDTO> getJobs(Pageable pageable) {
        return jobRepository
                .findAll(pageable)
                .map(jobMapper::toSimpleDTO);
    }

    public Job getByIdOrThrow(UUID jobUuid) throws NotFoundException {
        return jobRepository
                .findById(jobUuid)
                .orElseThrow(() -> new NotFoundException(ENTITY_NAME, jobUuid));
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public JobDTO getJob(UUID jobUuid) throws NotFoundException {
        return jobMapper.toDTO(getByIdOrThrow(jobUuid));
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public Job createJobForTrain(TrainDispatchPayloadDTO reqDto) {
        final Job job = jobMapper.fromTrainDispatchPayloadDTO(reqDto);
        return jobRepository.saveAndFlush(job);
    }

    public Optional<Job> getNextJob() {
        // TODO: priority?
        return jobRepository.findFirstByFinishedAtIsNullOrderByCreatedAtAsc();
    }

    public void updateStatus(Job job, JobStatus status) {
        jobRepository.saveAndFlush(jobMapper.updateStatus(job, status));
    }
}

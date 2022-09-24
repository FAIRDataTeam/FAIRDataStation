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
package org.fairdatatrain.fairdatastation.service.event.job.artifact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDTO;
import org.fairdatatrain.fairdatastation.data.model.enums.ArtifactStorage;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.data.model.event.JobArtifact;
import org.fairdatatrain.fairdatastation.data.repository.event.JobArtifactRepository;
import org.fairdatatrain.fairdatastation.exception.NotFoundException;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.format;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobArtifactService {

    private static final String ENTITY_NAME = "Job";

    private final JobArtifactRepository jobArtifactRepository;

    private final JobArtifactMapper jobArtifactMapper;

    private final JobService jobService;

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<JobArtifactDTO> getArtifactsForJob(UUID jobUuid) throws NotFoundException {
        final Job job = jobService.getByIdOrThrow(jobUuid);
        return job
                .getArtifacts()
                .stream()
                .map(jobArtifactMapper::toDTO)
                .toList();
    }

    public JobArtifact getByIdOrThrow(UUID jobUuid, UUID artifactUuid) throws NotFoundException {
        final JobArtifact jobArtifact = jobArtifactRepository
                .findById(artifactUuid)
                .orElseThrow(() -> new NotFoundException(ENTITY_NAME, artifactUuid));
        if (!jobArtifact.getJob().getUuid().equals(jobUuid)) {
            throw new NotFoundException(ENTITY_NAME, Map.of("jobUuid", jobUuid.toString()));
        }
        return jobArtifact;
    }

    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public byte[] getArtifactData(JobArtifact artifact) {
        if (artifact.getStorage().equals(ArtifactStorage.POSTGRES)) {
            return artifact.getData();
        }
        throw new RuntimeException(
                format("Unsupported artifact storage: %s", artifact.getStorage())
        );
    }
}

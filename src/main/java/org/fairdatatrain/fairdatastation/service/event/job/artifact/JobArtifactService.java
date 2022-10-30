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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.HashUtils.bytesToHex;
import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobArtifactService {

    private static final int MAX_TRIES_DISPATCH = 5;

    private static final String ENTITY_NAME = "JobArtifact";

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

    @Transactional
    public void createArtifact(Job job, String displayName, String filename,
                               String contentType, byte[] data) {
        final String hash = computeHash(data);
        final JobArtifact jobArtifact = jobArtifactRepository.saveAndFlush(
                jobArtifactMapper.create(job, displayName, filename, contentType, data, hash)
        );
        log.info("Created artifact {} for job {}", jobArtifact.getUuid(), job.getUuid());
    }

    private String computeHash(byte[] data) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new RuntimeException("SHA-256 hashing is not supported");
        }
        return bytesToHex(digest.digest(data));
    }

    public Optional<JobArtifact> getNextToDispatch() {
        // TODO: priority?
        // TODO: shorter methodname
        // CHECKSTYLE.OFF: LineLength
        return jobArtifactRepository.findFirstByDeliveredIsFalseAndNextDispatchAtIsNotNullAndNextDispatchAtIsBeforeOrderByNextDispatchAtAsc(now());
        // CHECKSTYLE.ON: LineLength
    }

    public void updateDispatch(JobArtifact jobArtifact, Boolean delivered) {
        updateDispatch(jobArtifact, delivered, null);
    }

    @Transactional
    public void updateDispatch(JobArtifact jobArtifact, Boolean delivered, String error) {
        jobArtifact.setDelivered(delivered);
        if (delivered) {
            jobArtifact.setNextDispatchAt(null);
            jobArtifact.setLastError(null);
        }
        else {
            final int tries = jobArtifact.getTries() + 1;
            jobArtifact.setTries(tries);
            jobArtifact.setLastError(error);
            if (tries >= MAX_TRIES_DISPATCH) {
                jobArtifact.setNextDispatchAt(null);
            }
            else {
                final long minutesNext = (long) Math.pow(2, tries);
                final Instant nextAt = Instant.now().plus(Duration.ofMinutes(minutesNext));
                jobArtifact.setNextDispatchAt(Timestamp.from(nextAt));
            }
        }
        jobArtifact.setUpdatedAt(now());
        jobArtifactRepository.saveAndFlush(jobArtifact);
    }

    public JobArtifactMapper getMapper() {
        return jobArtifactMapper;
    }
}

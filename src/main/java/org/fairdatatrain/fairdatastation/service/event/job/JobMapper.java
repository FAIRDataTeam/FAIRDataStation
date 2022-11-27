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
import org.fairdatatrain.fairdatastation.api.dto.event.job.JobDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.JobSimpleDTO;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.service.event.job.artifact.JobArtifactMapper;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JobMapper {

    private final JobEventMapper jobEventMapper;

    private final JobArtifactMapper jobArtifactMapper;

    public JobSimpleDTO toSimpleDTO(Job job) {
        return JobSimpleDTO
                .builder()
                .uuid(job.getUuid())
                .remoteId(job.getRemoteId())
                .status(job.getStatus())
                .startedAt(
                        Optional.ofNullable(job.getStartedAt())
                                .map(Timestamp::toInstant)
                                .orElse(null)
                )
                .finishedAt(
                        Optional.ofNullable(job.getFinishedAt())
                                .map(Timestamp::toInstant)
                                .orElse(null)
                )
                .artifacts(
                        job.getArtifacts()
                                .stream()
                                .map(jobArtifactMapper::toDTO)
                                .toList()
                )
                .createdAt(job.getCreatedAt().toInstant())
                .updatedAt(job.getUpdatedAt().toInstant())
                .version(job.getVersion())
                .build();
    }

    public JobDTO toDTO(Job job) {
        return JobDTO
                .builder()
                .uuid(job.getUuid())
                .remoteId(job.getRemoteId())
                .status(job.getStatus())
                .startedAt(
                        Optional.ofNullable(job.getStartedAt())
                                .map(Timestamp::toInstant)
                                .orElse(null)
                )
                .finishedAt(
                        Optional.ofNullable(job.getFinishedAt())
                                .map(Timestamp::toInstant)
                                .orElse(null)
                )
                .events(
                        job.getEvents()
                                .stream()
                                .map(jobEventMapper::toDTO)
                                .toList()
                )
                .artifacts(
                        job.getArtifacts()
                                .stream()
                                .map(jobArtifactMapper::toDTO)
                                .toList()
                )
                .createdAt(job.getCreatedAt().toInstant())
                .updatedAt(job.getUpdatedAt().toInstant())
                .version(job.getVersion())
                .build();
    }
}

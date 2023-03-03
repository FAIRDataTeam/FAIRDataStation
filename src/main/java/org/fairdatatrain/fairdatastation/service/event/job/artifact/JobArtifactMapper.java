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

import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDispatchDTO;
import org.fairdatatrain.fairdatastation.data.model.enums.ArtifactStorage;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.data.model.event.JobArtifact;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.Base64;

import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Component
public class JobArtifactMapper {

    public JobArtifactDTO toDTO(JobArtifact jobArtifact) {
        return JobArtifactDTO
                .builder()
                .uuid(jobArtifact.getUuid())
                .displayName(jobArtifact.getDisplayName())
                .filename(jobArtifact.getFilename())
                .bytesize(jobArtifact.getBytesize())
                .contentType(jobArtifact.getContentType())
                .hash(jobArtifact.getHash())
                .occurredAt(jobArtifact.getOccurredAt().toInstant())
                .createdAt(jobArtifact.getCreatedAt().toInstant())
                .updatedAt(jobArtifact.getUpdatedAt().toInstant())
                .build();
    }

    public JobArtifact create(Job job, String displayName, String filename, String contentType,
                              byte[] data, String hash) {
        final Timestamp now = now();
        return JobArtifact
                .builder()
                .job(job)
                .displayName(displayName)
                .filename(filename)
                .contentType(contentType)
                .data(data)
                .storage(ArtifactStorage.POSTGRES)
                .bytesize((long) data.length)
                .hash(hash)
                .occurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public JobArtifactDispatchDTO toDispatchDTO(JobArtifact artifact) {
        return JobArtifactDispatchDTO
                .builder()
                .remoteId(artifact.getJob().getUuid().toString())
                .secret(artifact.getJob().getSecret())
                .displayName(artifact.getDisplayName())
                .filename(artifact.getFilename())
                .bytesize(artifact.getBytesize())
                .hash(artifact.getHash())
                .contentType(artifact.getContentType())
                .base64data(Base64.getEncoder().encodeToString(artifact.getData()))
                .occurredAt(artifact.getOccurredAt().toInstant())
                .build();
    }
}

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

import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDispatchDTO;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.data.model.event.JobEvent;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

import static org.fairdatatrain.fairdatastation.utils.TimeUtils.now;

@Component
public class JobEventMapper {

    public JobEventDTO toDTO(JobEvent jobEvent) {
        return JobEventDTO
                .builder()
                .uuid(jobEvent.getUuid())
                .resultStatus(jobEvent.getResultStatus())
                .message(jobEvent.getMessage())
                .occurredAt(jobEvent.getOccurredAt().toInstant())
                .createdAt(jobEvent.getCreatedAt().toInstant())
                .updatedAt(jobEvent.getUpdatedAt().toInstant())
                .build();
    }

    public JobEvent create(Job job, String message, JobStatus status) {
        final Timestamp now = now();
        return JobEvent
                .builder()
                .job(job)
                .message(message)
                .resultStatus(status)
                .occurredAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public JobEventDispatchDTO toDispatchDTO(JobEvent event) {
        return JobEventDispatchDTO
                .builder()
                .remoteId(event.getJob().getUuid().toString())
                .secret(event.getJob().getSecret())
                .message(event.getMessage())
                .resultStatus(event.getResultStatus())
                .occurredAt(event.getOccurredAt().toInstant())
                .build();
    }
}

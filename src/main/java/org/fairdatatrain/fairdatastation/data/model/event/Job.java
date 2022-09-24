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
package org.fairdatatrain.fairdatastation.data.model.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.fairdatatrain.fairdatastation.data.model.base.BaseEntity;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.util.List;

@Entity(name = "Job")
@Table(name = "job")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class Job extends BaseEntity {

    @NotNull
    @Column(name = "secret", nullable = false)
    private String secret;

    @Column(name = "remote_id")
    private String remoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "job_status", nullable = false)
    private JobStatus status;

    @Column(name = "started_at")
    private Timestamp startedAt;

    @Column(name = "finished_at")
    private Timestamp finishedAt;

    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "job")
    private List<JobEvent> events;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "job")
    private List<JobArtifact> artifacts;
}

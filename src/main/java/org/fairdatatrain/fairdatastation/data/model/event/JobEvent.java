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

@Entity(name = "JobEvent")
@Table(name = "job_event")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class JobEvent extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", columnDefinition = "job_status")
    private JobStatus resultStatus;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Timestamp occurredAt;

    @NotNull
    @Column(name = "message", nullable = false)
    private String message;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    // TODO: delivery details (extract?)
    @NotNull
    @Column(name = "delivered", nullable = false)
    private Boolean delivered;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "next_dispatch_at")
    private Timestamp nextDispatchAt;

    @NotNull
    @Column(name = "tries")
    private Integer tries;
}

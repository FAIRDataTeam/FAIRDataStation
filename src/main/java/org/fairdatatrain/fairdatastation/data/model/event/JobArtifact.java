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
import org.fairdatatrain.fairdatastation.data.model.enums.ArtifactStorage;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.sql.Timestamp;

@Entity(name = "JobArtifact")
@Table(name = "job_artifact")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
public class JobArtifact extends BaseEntity {

    @NotNull
    @Column(name = "display_name")
    private String displayName;

    @NotNull
    @Column(name = "filename", nullable = false)
    private String filename;

    @NotNull
    @Column(name = "bytesize", nullable = false)
    private Long bytesize;

    @NotNull
    @Column(name = "hash", nullable = false)
    private String hash;

    @NotNull
    @Column(name = "content_type", nullable = false)
    private String contentType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "storage", columnDefinition = "artifact_storage", nullable = false)
    private ArtifactStorage storage;

    @NotNull
    @Column(name = "occurred_at", nullable = false)
    private Timestamp occurredAt;

    @Lob
    @Column(name = "data")
    @Type(type = "org.hibernate.type.BinaryType")
    private byte[] data;

    @NotNull
    @ManyToOne
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
}

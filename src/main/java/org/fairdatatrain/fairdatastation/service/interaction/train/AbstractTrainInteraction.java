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
package org.fairdatatrain.fairdatastation.service.interaction.train;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.dtls.fairdatapoint.vocabulary.FDT;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.service.accesscontrol.BasicAccessControlService;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.fairdatatrain.fairdatastation.service.event.job.artifact.JobArtifactService;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventService;
import org.fairdatatrain.fairdatastation.service.interaction.entity.InteractionArtifact;
import org.fairdatatrain.fairdatastation.service.interaction.fetch.TrainFetcher;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.RdfUtils.getObjectBy;
import static org.fairdatatrain.fairdatastation.utils.RdfUtils.getStringObjectBy;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractTrainInteraction {

    private final BasicAccessControlService accessControlService;

    private final JobEventService jobEventService;

    private final JobArtifactService jobArtifactService;

    private final JobService jobService;

    private final TrainFetcher trainFetcher;

    protected BasicAccessControlService getAccessControlService() {
        return accessControlService;
    }

    protected JobEventService getJobEventService() {
        return jobEventService;
    }

    protected JobArtifactService getJobArtifactService() {
        return jobArtifactService;
    }

    protected JobService getJobService() {
        return jobService;
    }

    protected TrainFetcher getTrainFetcher() {
        return trainFetcher;
    }

    protected void handleInteractionFailed(Job job, String message) {
        jobEventService.createEvent(job, message, JobStatus.FAILED);
        jobService.updateStatus(job, JobStatus.FAILED);
    }

    protected void checkAccess() {
        try {
            accessControlService.checkAccess();
        }
        catch (Exception exception) {
            throw new RuntimeException(format("Access Control: Access denied (%s)",
                    exception.getMessage()));
        }
    }

    protected void sendInfo(Job job, String message) {
        jobEventService.createEvent(job, message);
    }

    protected void sendArtifact(Job job, InteractionArtifact result) {
        jobArtifactService.createArtifact(
                job,
                result.getName(),
                result.getFilename(),
                result.getContentType(),
                result.getData()
        );
    }

    protected Model getPayloadMetadata(Job job, Resource payloadResource) {
        return trainFetcher.fetchPayloadMetadata(payloadResource.stringValue());
    }

    protected Resource getPayloadMetadataUrl(Model model, Resource trainResource) {
        final Value value = getObjectBy(model, trainResource, FDT.HASPAYLOAD);
        if (value != null && value.isResource()) {
            return (Resource) value;
        }
        return null;
    }

    protected String getPayloadUrl(Model model, Resource payloadResource) {
        return getStringObjectBy(model, payloadResource, FDT.PAYLOADDOWNLOADURL);
    }

    protected void validatePayloadResource(Resource payloadResource) {
        if (payloadResource == null) {
            throw new RuntimeException("Validation: No payload resource found");
        }
    }

    protected String fetchPayload(String payloadDownloadUrl) {
        try {
            return trainFetcher.fetchSimplePayload(payloadDownloadUrl);
        }
        catch (Exception exception) {
            throw new RuntimeException(
                    format("Fetch: Failed to fetch train payload (%s)", payloadDownloadUrl));
        }
    }
}

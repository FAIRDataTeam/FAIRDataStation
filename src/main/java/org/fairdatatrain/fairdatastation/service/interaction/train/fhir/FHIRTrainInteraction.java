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
package org.fairdatatrain.fairdatastation.service.interaction.train.fhir;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.service.accesscontrol.BasicAccessControlService;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.fairdatatrain.fairdatastation.service.event.job.artifact.JobArtifactService;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventService;
import org.fairdatatrain.fairdatastation.service.interaction.entity.InteractionArtifact;
import org.fairdatatrain.fairdatastation.service.interaction.fetch.TrainFetcher;
import org.fairdatatrain.fairdatastation.service.interaction.train.AbstractTrainInteraction;
import org.fairdatatrain.fairdatastation.service.interaction.train.ITrainInteraction;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.request.FHIRPreparedRequest;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.response.FHIRResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class FHIRTrainInteraction extends AbstractTrainInteraction implements ITrainInteraction {

    private final FHIRClient fhirClient;

    public FHIRTrainInteraction(
            BasicAccessControlService accessControlService,
            JobEventService jobEventService,
            JobArtifactService jobArtifactService,
            JobService jobService,
            TrainFetcher trainFetcher,
            FHIRClient fhirClient
    ) {
        super(accessControlService, jobEventService, jobArtifactService, jobService, trainFetcher);
        this.fhirClient = fhirClient;
    }

    @Override
    public void interact(Job job, Model model, Resource train) {
        sendInfo(job, "Processing further as FHIR train");
        try {
            final FHIRPreparedRequest fhirRequest = interactPrepare(job, model, train);

            sendInfo(job, "Validation: Validating FHIR request");
            validateRequest(fhirRequest);
            sendInfo(job, "Validation: FHIR request validated");

            sendInfo(job, "Access Control: Requesting access to Triple Store");
            checkAccess();
            sendInfo(job, "Access Control: Access to Triple Store granted");

            final List<InteractionArtifact> results = interactCommunicate(job, fhirRequest);
            results.forEach(result -> sendArtifact(job, result));

            getJobEventService().createEvent(job, "Finished!", JobStatus.FINISHED);
            getJobService().updateStatus(job, JobStatus.FINISHED);
        }
        catch (Exception exception) {
            handleInteractionFailed(job, exception.getMessage());
        }
    }

    public FHIRPreparedRequest interactPrepare(Job job, Model model, Resource train) {
        final Resource payloadResource = getPayloadMetadataUrl(model, train);
        sendInfo(job, "Validation: Validating payload resource");
        validatePayloadResource(payloadResource);
        sendInfo(job, "Validation: Payload resource validated");

        sendInfo(job, "Fetch: Fetching payload metadata");
        final Model payloadMetadata = getPayloadMetadata(job, payloadResource);
        final String payloadDownloadUrl = getPayloadUrl(payloadMetadata, payloadResource);
        sendInfo(job, "Fetch: Payload metadata fetched");

        sendInfo(job, "Fetch: Fetching train payload (FHIR request)");
        final String payload = fetchPayload(payloadDownloadUrl);
        sendInfo(job, "Fetch: Train payload (FHIR request) fetched");

        sendInfo(job, "Validation: Parsing train payload");
        final FHIRPreparedRequest fhirRequest = prepareRequest(payload);
        sendInfo(job, "Validation: Train payload parsed");

        return fhirRequest;
    }

    public List<InteractionArtifact> interactCommunicate(Job job, FHIRPreparedRequest fhirRequest) {
        sendInfo(job, "Execution: Sending FHIR request to API");
        final FHIRResponse response = fhirClient.send(fhirRequest);
        sendInfo(job, "Execution: FHIR response received from API");

        sendInfo(job, "Validation: Validating FHIR response");
        validateResponse(response);
        sendInfo(job, "Validation: FHIR response validated");

        sendInfo(job, "Execution: Preparing and sending artifact(s)");
        return responseToArtifacts(response);
    }

    private List<InteractionArtifact> responseToArtifacts(FHIRResponse response) {
        final MediaType contentType = Optional
                .ofNullable(response.getHeaders().getContentType())
                .orElse(MediaType.TEXT_PLAIN);
        if (response.getStatusCode().is2xxSuccessful()) {
            return List.of(
                    InteractionArtifact
                            .builder()
                            .name("FHIR Response")
                            .filename("fhir-response.json")
                            .contentType(contentType.toString())
                            .data(response.getBody().getBytes(StandardCharsets.UTF_8))
                            .build()
            );
        }
        else if (response.getBody() != null && !response.getBody().isBlank()) {
            return List.of(
                    InteractionArtifact
                            .builder()
                            .name("FHIR Error")
                            .filename("fhir-error.json")
                            .contentType(contentType.toString())
                            .data(response.getBody().getBytes(StandardCharsets.UTF_8))
                            .build()
            );
        }
        return List.of();
    }

    private void validateResponse(FHIRResponse response) {
        // TODO: validate (?)
    }

    private void validateRequest(FHIRPreparedRequest fhirRequest) {
        if (fhirRequest == null) {
            throw new RuntimeException("Validation: Invalid FHIR request");
        }
    }

    private FHIRPreparedRequest prepareRequest(String payload) {
        try {
            return fhirClient.parseRequest(payload);
        }
        catch (Exception exception) {
            throw new RuntimeException("Validation: Failed to parse FHIR request");
        }
    }
}

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
package org.fairdatatrain.fairdatastation.service.interaction.train.sparql;

import lombok.extern.slf4j.Slf4j;
import nl.dtls.fairdatapoint.vocabulary.FDT;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.parser.ParsedOperation;
import org.eclipse.rdf4j.query.parser.ParsedUpdate;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
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
import org.fairdatatrain.fairdatastation.service.storage.TripleStoreStorage;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.RdfUtils.getStringObjectBy;

@Slf4j
@Service
public class SPARQLTrainInteraction extends AbstractTrainInteraction implements ITrainInteraction {

    private final TripleStoreStorage tripleStoreStorage;

    public SPARQLTrainInteraction(
            BasicAccessControlService accessControlService,
            JobEventService jobEventService,
            JobArtifactService jobArtifactService,
            JobService jobService,
            TrainFetcher trainFetcher,
            TripleStoreStorage tripleStoreStorage
    ) {
        super(accessControlService, jobEventService, jobArtifactService, jobService, trainFetcher);
        this.tripleStoreStorage = tripleStoreStorage;
    }

    @Override
    public void interact(Job job, Model model, Resource train) {
        sendInfo(job, "Processing further as SPARQL train");
        try {
            final Resource payloadResource = getPayloadMetadataUrl(model, train);
            sendInfo(job, "Validation: Validating payload resource");
            validatePayloadResource(payloadResource);
            sendInfo(job, "Validation: Payload resource validated");

            sendInfo(job, "Fetch: Fetching payload metadata");
            final Model payloadMetadata = getPayloadMetadata(job, payloadResource);
            sendInfo(job, "Fetch: Payload metadata fetched");

            sendInfo(job, "Validation: Validating payload metadata");
            validatePayloadMetadata(job, payloadMetadata, payloadResource);
            final String payloadDownloadUrl = getPayloadUrl(payloadMetadata, payloadResource);
            sendInfo(job, "Validation: Payload metadata validated");

            sendInfo(job, "Fetch: Fetching train payload (SPARQL query)");
            final String sparqlQuery = fetchPayload(payloadDownloadUrl);
            sendInfo(job, "Fetch: Train payload (SPARQL query) fetched");

            sendInfo(job, "Validation: Validating train payload");
            validateSparqlQuery(sparqlQuery);
            sendInfo(job, "Validation: Train payload validated");

            sendInfo(job, "Access Control: Requesting access to Triple Store");
            checkAccess();
            sendInfo(job, "Access Control: Access to Triple Store granted");

            sendInfo(job, "Execution: Executing query from SPARQL train");
            final List<InteractionArtifact> results = executeQuery(sparqlQuery);
            sendInfo(job, "Execution: Processing query result");

            sendInfo(job, "Execution: Preparing and sending artifact(s)");
            results.forEach(result -> sendArtifact(job, result));

            getJobEventService().createEvent(job, "Finished!", JobStatus.FINISHED);
            getJobService().updateStatus(job, JobStatus.FINISHED);
        }
        catch (Exception exception) {
            handleInteractionFailed(job, exception.getMessage());
        }
    }

    private List<InteractionArtifact> executeQuery(String sparqlQuery) {
        try {
            // TODO: set accept + name based on possibilities/train metadata?
            return tripleStoreStorage.executeQuery(sparqlQuery, "Result", "*/*");
        }
        catch (Exception exception) {
            throw new RuntimeException(format("Execution: Failed to execute SPARQL query (%s)",
                    exception.getMessage()));
        }
    }

    private void validatePayloadMetadata(Job job, Model model, Resource payloadResource) {
        if (getStringObjectBy(model, payloadResource, FDT.PAYLOADDOWNLOADURL) == null) {
            throw new RuntimeException("Validation: Missing payload download URL");
        }
    }

    private void validateSparqlQuery(String sparqlQuery) {
        // parse and check non-updating
        try {
            final ParsedOperation operation =
                    QueryParserUtil.parseOperation(QueryLanguage.SPARQL, sparqlQuery, null);
            if (operation instanceof ParsedUpdate) {
                throw new RuntimeException("Validation: SPARQL Query not valid (update query)");
            }
        }
        catch (Exception exception) {
            throw new RuntimeException(format("Validation: SPARQL Query not valid (%s)",
                    exception.getMessage()));
        }
    }
}

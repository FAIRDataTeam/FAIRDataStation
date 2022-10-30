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
package org.fairdatatrain.fairdatastation.service.interaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.fairdatatrain.fairdatastation.data.model.enums.JobStatus;
import org.fairdatatrain.fairdatastation.data.model.event.Job;
import org.fairdatatrain.fairdatastation.service.event.job.JobService;
import org.fairdatatrain.fairdatastation.service.event.job.event.JobEventService;
import org.fairdatatrain.fairdatastation.service.interaction.entity.TrainType;
import org.fairdatatrain.fairdatastation.service.interaction.fetch.TrainFetcher;
import org.fairdatatrain.fairdatastation.service.interaction.train.ITrainInteraction;
import org.fairdatatrain.fairdatastation.service.interaction.train.TrainInteractionFactory;
import org.fairdatatrain.fairdatastation.service.validation.TrainValidationService;
import org.springframework.stereotype.Service;

import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class GenericTrainInteraction {

    private final TrainInteractionFactory trainInteractionFactory;

    private final TrainFetcher trainFetcher;

    private final TrainValidationService trainValidationService;

    private final JobEventService jobEventService;

    private final JobService jobService;

    public void interact(Job job) {
        sendInfo(job, "Retrieved job from queue", JobStatus.RUNNING);
        try {
            sendInfo(job, format("Fetch: Fetching details for train: %s",
                    job.getTrainUri()));
            final Model trainMetadata = fetchTrainMetadata(job);
            sendInfo(job, format("Fetch: Details fetched successfully for train: %s",
                    job.getTrainUri()));

            sendInfo(job, "Validation: Validating train metadata and checking type");
            final Resource train = extractValidTrain(trainMetadata);
            sendInfo(job, "Validation: Train metadata validated");
            final TrainType trainType = determineTrainType(trainMetadata);

            final ITrainInteraction trainInteraction =
                    trainInteractionFactory.getTrainInteractionService(trainType);
            trainInteraction.interact(job, trainMetadata, train);
        }
        catch (Exception exception) {
            handleInteractionFailed(job, exception.getMessage());
        }
    }

    private void handleInteractionFailed(Job job, String message) {
        jobEventService.createEvent(job, message, JobStatus.FAILED);
        jobService.updateStatus(job, JobStatus.FAILED);
    }

    private void sendInfo(Job job, String message) {
        jobEventService.createEvent(job, message);
    }

    private void sendInfo(Job job, String message, JobStatus status) {
        jobEventService.createEvent(job, message, status);
    }

    private Model fetchTrainMetadata(Job job) {
        try {
            return trainFetcher.fetchTrainMetadata(job.getTrainUri());
        }
        catch (Exception exception) {
            throw new RuntimeException("Preparation: Failed to fetch train metadata");
        }
    }

    private Resource extractValidTrain(Model trainMetadata) {
        try {
            return trainValidationService.validate(trainMetadata);
        }
        catch (Exception exception) {
            throw new RuntimeException(format("Validation: Invalid train (%s)",
                    exception.getMessage()));
        }
    }

    private TrainType determineTrainType(Model trainMetadata) {
        // TODO: throw if type not defined or multiple types (?)
        return TrainType.SPARQL_TRAIN;
    }
}

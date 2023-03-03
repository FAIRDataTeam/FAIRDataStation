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
package org.fairdatatrain.fairdatastation.service.validation;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.dtls.fairdatapoint.vocabulary.FDT;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.fairdatatrain.fairdatastation.service.interaction.entity.TrainType;
import org.fairdatatrain.fairdatastation.service.interaction.train.fhir.FHIRClient;
import org.fairdatatrain.fairdatastation.service.storage.TripleStoreStorage;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.String.format;

@Slf4j
@Service
public class TrainValidationService {

    private final Map<Resource, TrainType> trainTypes = new HashMap<>();

    public TrainValidationService(TripleStoreStorage tripleStoreStorage, FHIRClient fhirClient) {
        // TODO: refactor check for supported trains based on configuration/properties
        if (tripleStoreStorage.isReady()) {
            log.info("Supported train: SPARQL Train");
            trainTypes.put(FDT.SPARQLTRAIN, TrainType.SPARQL_TRAIN);
        }
        if (fhirClient.isReady()) {
            log.info("Supported train: FHIR Train");
            trainTypes.put(FDT.FHIRTRAIN, TrainType.FHIR_TRAIN);
        }
    }

    @SneakyThrows
    public Resource validate(Model trainModel) {
        List<Resource> trainsFound = new ArrayList<>();
        for (Resource trainType : trainTypes.keySet()) {
            trainsFound.addAll(trainModel.filter(null, RDF.TYPE, trainType).subjects());
        }
        for (Resource trainType : trainTypes.keySet()) {
            // Why DCTERMS?
            trainsFound.addAll(trainModel.filter(null, DCTERMS.TYPE, trainType).subjects());
        }
        trainsFound.addAll(trainModel.filter(null, RDF.TYPE, FDT.TRAIN).subjects());
        trainsFound = trainsFound.stream().distinct().toList();
        if (trainsFound.isEmpty()) {
            throw new RuntimeException("Validation: No train specification found in metadata");
        }
        if (trainsFound.size() > 1) {
            throw new RuntimeException(
                    format("Validation: More than one train found in metadata: %s", trainsFound));
        }
        final Resource train = trainsFound.get(0);
        // TODO: more validations (train metadata)
        return train;
    }

    public TrainType determineTrainType(Model trainModel, Resource train) {
        final List<Value> trainTypeResources = new ArrayList<>();
        trainTypeResources.addAll(trainModel.filter(train, RDF.TYPE, null).objects());
        trainTypeResources.addAll(trainModel.filter(train, DCTERMS.TYPE, null).objects());
        final List<TrainType> presentTrainTypes = trainTypeResources
                .stream()
                .distinct()
                .filter(Value::isResource)
                .map(value -> (Resource) value)
                .map(trainTypes::get)
                .filter(Objects::nonNull)
                .toList();
        if (presentTrainTypes.isEmpty()) {
            throw new RuntimeException("Validation: No supported train type found in metadata");
        }
        if (presentTrainTypes.size() > 1) {
            throw new RuntimeException(
                    format("Validation: Multiple supported train types found in metadata: %s",
                    presentTrainTypes)
            );
        }
        return presentTrainTypes.get(0);
    }
}

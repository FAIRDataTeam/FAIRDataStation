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
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.DCTERMS;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TrainValidationService {

    private static final List<Resource> SUPPORTED_TRAINS = new ArrayList<>();

    static {
        final ValueFactory vf = SimpleValueFactory.getInstance();

        SUPPORTED_TRAINS.add(FDT.SPARQLTRAIN);
        SUPPORTED_TRAINS.add(vf.createIRI("https://w3id.org/fdp/fdt-o#SPARQLTrain"));
    }

    @SneakyThrows
    public Resource validate(Model trainModel) {
        List<Resource> trainsFound = new ArrayList<>();
        for (Resource trainType : SUPPORTED_TRAINS) {
            trainsFound.addAll(trainModel.filter(null, RDF.TYPE, trainType).subjects());
        }
        for (Resource trainType : SUPPORTED_TRAINS) {
            // Why DCTERMS?
            trainsFound.addAll(trainModel.filter(null, DCTERMS.TYPE, trainType).subjects());
        }
        trainsFound.addAll(trainModel.filter(null, RDF.TYPE, FDT.TRAIN).subjects());
        trainsFound = trainsFound.stream().distinct().toList();
        if (trainsFound.isEmpty()) {
            // TODO: raise no train found
        }
        if (trainsFound.size() > 1) {
            // TODO: raise more trains found
        }
        final Resource train = trainsFound.get(0);
        // TODO: more validations (train metadata)
        return train;
    }
}

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
package org.fairdatatrain.fairdatastation.service.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.common.lang.FileFormat;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.resultio.*;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.Rio;
import org.fairdatatrain.fairdatastation.exception.StorageException;
import org.fairdatatrain.fairdatastation.service.interaction.entity.InteractionArtifact;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.StringUtils.sanitizeFilename;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripleStoreStorage {

    private final Repository repository;

    public List<InteractionArtifact> executeQuery(
            String sparqlQuery, String name, String accept
    ) throws StorageException {
        return executeQuery(sparqlQuery, name, Set.of(accept));
    }

    public List<InteractionArtifact> executeQuery(
            String sparqlQuery, String name, Set<String> accept
    ) throws StorageException {
        if (accept.isEmpty()) {
            return List.of();
        }
        try (RepositoryConnection connection = repository.getConnection()) {
            final Query query = connection.prepareQuery(QueryLanguage.SPARQL, sparqlQuery);

            // SELECT
            if (query instanceof final TupleQuery selectQuery) {
                return evaluateQuery(selectQuery, name, accept);
            }
            // ASK
            else if (query instanceof final BooleanQuery askQuery) {
                return evaluateQuery(askQuery, name, accept);
            }
            // DESCRIBE / CONSTRUCT
            else if (query instanceof final GraphQuery graphQuery) {
                return evaluateQuery(graphQuery, name, accept);
            }
            // Other (e.g. UPDATE)
            else {
                throw new StorageException(format("Unsupported query type: %s",
                        query.getClass().getName()));
            }
        }
        catch (RepositoryException exception) {
            throw new StorageException(exception.getMessage());
        }
    }

    private List<InteractionArtifact> evaluateQuery(
            TupleQuery query, String name, Set<String> accept
    ) {
        final Set<TupleQueryResultFormat> formats = accept
                .parallelStream()
                .map(QueryResultIO::getWriterFormatForMIMEType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(format -> format instanceof TupleQueryResultFormat)
                .map(format -> (TupleQueryResultFormat) format)
                .collect(Collectors.toSet());
        if (accept.contains(MimeTypeUtils.ALL_VALUE)) {
            formats.add(TupleQueryResultFormat.JSON);
        }
        return formats
                .parallelStream()
                .map(format -> getQueryResult(query, name, format))
                .toList();
    }

    private List<InteractionArtifact> evaluateQuery(
            BooleanQuery query, String name, Set<String> accept
    ) {
        final Set<BooleanQueryResultFormat> formats = accept
                .parallelStream()
                .map(QueryResultIO::getBooleanParserFormatForMIMEType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(format -> format instanceof BooleanQueryResultFormat)
                .map(format -> (BooleanQueryResultFormat) format)
                .collect(Collectors.toSet());
        if (accept.contains(MimeTypeUtils.ALL_VALUE)) {
            formats.add(BooleanQueryResultFormat.TEXT);
        }
        return formats
                .parallelStream()
                .map(format -> getQueryResult(query, name, format))
                .toList();
    }

    private List<InteractionArtifact> evaluateQuery(
            GraphQuery query, String name, Set<String> accept
    ) {
        final Set<RDFFormat> formats = accept
                .parallelStream()
                .map(Rio::getParserFormatForMIMEType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        if (accept.contains(MimeTypeUtils.ALL_VALUE)) {
            formats.add(RDFFormat.TURTLE);
        }
        return formats
                .parallelStream()
                .map(format -> getQueryResult(query, name, format))
                .toList();
    }

    private InteractionArtifact getQueryResult(
            TupleQuery query, String name, TupleQueryResultFormat format
    ) {
        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
        final TupleQueryResultWriter writer = QueryResultIO.createTupleWriter(format, bao);
        query.evaluate(writer);
        return createArtifact(name, format, bao.toString().getBytes(StandardCharsets.UTF_8));
    }

    private InteractionArtifact getQueryResult(
            BooleanQuery query, String name, BooleanQueryResultFormat format
    ) {
        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
        final BooleanQueryResultWriter writer = QueryResultIO.createBooleanWriter(format, bao);
        writer.handleBoolean(query.evaluate());
        return createArtifact(name, format, bao.toString().getBytes(StandardCharsets.UTF_8));
    }

    private InteractionArtifact getQueryResult(GraphQuery query, String name, RDFFormat format) {
        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
        final RDFHandler writer = Rio.createWriter(format, bao);
        query.evaluate(writer);
        return createArtifact(name, format, bao.toString().getBytes(StandardCharsets.UTF_8));
    }

    private InteractionArtifact createArtifact(String name, FileFormat format, byte[] data) {
        return InteractionArtifact.builder()
                .name(format("%s (%s)", name, format.getName()))
                .filename(format("%s.%s", sanitizeFilename(name), format.getDefaultFileExtension()))
                .contentType(format.getDefaultMIMEType())
                .data(data)
                .build();
    }

}

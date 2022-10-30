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
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.parser.*;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.fairdatatrain.fairdatastation.exception.StorageException;
import org.fairdatatrain.fairdatastation.service.interaction.entity.InteractionArtifact;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static java.lang.String.format;
import static org.fairdatatrain.fairdatastation.utils.RdfUtils.write;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripleStoreStorage {

    private final Repository repository;

    public List<InteractionArtifact> executeQuery(String sparqlQuery) throws StorageException {
        final ParsedOperation operation =
                QueryParserUtil.parseOperation(QueryLanguage.SPARQL, sparqlQuery, null);
        if (operation instanceof ParsedBooleanQuery) {
            final boolean result = runBooleanQuery(sparqlQuery);
            final String content = result ? "TRUE" : "FALSE";
            return List.of(
                    InteractionArtifact.builder()
                            .name("Response")
                            .filename("response.txt")
                            .contentType("text/plain")
                            .data(content.getBytes(StandardCharsets.UTF_8))
                            .build()
            );
        }
        if (operation instanceof ParsedTupleQuery) {
            // TODO: add CSV? TSV? XML?
            final StringWriter stringWriter = new StringWriter();
            final SPARQLResultsJSONWriter writer = new SPARQLResultsJSONWriter(stringWriter);
            runTupleQuery(sparqlQuery, writer);
            return List.of(
                    InteractionArtifact.builder()
                            .name("Result (JSON)")
                            .filename("result.json")
                            .contentType(RDFFormat.TURTLE.getDefaultMIMEType())
                            .data(writer.toString().getBytes(StandardCharsets.UTF_8))
                            .build()
            );
        }
        if (operation instanceof ParsedGraphQuery) {
            final Model result = runGraphQuery(sparqlQuery);
            final String content = write(result, RDFFormat.TURTLE);
            return List.of(
                    InteractionArtifact.builder()
                            .name("Result graph (RDF)")
                            .filename("graph.rdf")
                            .contentType(RDFFormat.TURTLE.getDefaultMIMEType())
                            .data(content.getBytes(StandardCharsets.UTF_8))
                            .build()
            );
        }
        throw new StorageException(format("Unsupported query type: %s",
                operation.getClass().getName()));
    }

    // ASK
    private Boolean runBooleanQuery(String sparqlQuery) throws StorageException {
        try (RepositoryConnection conn = repository.getConnection()) {
            final BooleanQuery query =
                    conn.prepareBooleanQuery(QueryLanguage.SPARQL, sparqlQuery, null);
            return query.evaluate();
        }
        catch (RepositoryException exception) {
            throw new StorageException(exception.getMessage());
        }
    }

    // SELECT
    private void runTupleQuery(
            String sparqlQuery, TupleQueryResultWriter writer
    ) throws StorageException {
        try (RepositoryConnection conn = repository.getConnection()) {
            final TupleQuery query =
                    conn.prepareTupleQuery(QueryLanguage.SPARQL, sparqlQuery, null);
            query.evaluate(writer);
        }
        catch (RepositoryException exception) {
            throw new StorageException(exception.getMessage());
        }
    }

    // CONSTRUCT/DESCRIBE
    private Model runGraphQuery(String sparqlQuery) throws StorageException {
        // TODO: support different RDF formats?
        try (RepositoryConnection conn = repository.getConnection()) {
            final GraphQuery query =
                    conn.prepareGraphQuery(QueryLanguage.SPARQL, sparqlQuery, null);
            return QueryResults.asModel(query.evaluate());
        }
        catch (RepositoryException exception) {
            throw new StorageException(exception.getMessage());
        }
    }
}

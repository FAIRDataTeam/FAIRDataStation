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
package org.fairdatatrain.fairdatastation.utils;

import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.util.Models;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.*;
import org.eclipse.rdf4j.rio.*;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

public class RdfUtils {

    private static final String PREFIX_SEP = ":";

    private static final WriterConfig WRITER_CONFIG = new WriterConfig();

    static {
        WRITER_CONFIG.set(BasicWriterSettings.INLINE_BLANK_NODES, true);
    }

    public static IRI i(String iri) {
        if (iri == null) {
            return null;
        }
        return Values.iri(iri);
    }

    public static IRI i(String iri, Model model) {
        if (iri != null) {
            // URI: ':title'
            if (iri.startsWith(PREFIX_SEP)) {
                final Optional<Namespace> optionalNamespace = model.getNamespace("");
                final String prefix = optionalNamespace.get().getName();
                return i(prefix + iri.substring(1));
            }

            // URI: 'rda:title'
            final String[] splitted = iri.split(PREFIX_SEP);
            if (splitted.length == 2 && splitted[1].charAt(0) != '/') {
                final Optional<Namespace> optionalNamespace = model.getNamespace(splitted[0]);
                final String prefix = optionalNamespace.get().getName();
                return i(prefix + splitted[1]);
            }

            // URI: 'http://schema.org/person#title'
            if (iri.contains("://")) {
                return i(iri);
            }

        }
        return null;
    }

    public static List<Value> getObjectsBy(Model model, Resource subject, IRI predicate) {
        return Models.getProperties(model, subject, predicate).stream().toList();
    }

    public static List<Value> getObjectsBy(Model model, String subject, String predicate) {
        return getObjectsBy(model, i(subject, model), i(predicate, model));
    }

    public static Value getObjectBy(Model model, Resource subject, IRI predicate) {
        return Models.getProperty(model, subject, predicate).orElse(null);
    }

    public static String getStringObjectBy(Model model, Resource subject, IRI predicate) {
        return Models.getProperty(model, subject, predicate).map(Value::stringValue).orElse(null);
    }

    public static Model read(String content, String baseUri) {
        return read(content, baseUri, RDFFormat.TURTLE);
    }

    public static Model read(String content, String baseUri, RDFFormat format) {
        try (InputStream inputStream = new ByteArrayInputStream(content.getBytes())) {
            return Rio.parse(inputStream, baseUri, format);
        }
        catch (IOException exception) {
            throw new RuntimeException("Unable to read RDF (IO exception)");
        }
        catch (RDFParseException exception) {
            throw new RuntimeException("Unable to read RDF (parse exception)");
        }
        catch (RDFHandlerException exception) {
            throw new RuntimeException("Unable to read RDF (handler exception)");
        }
    }

    public static String write(Model model) {
        return write(model, RDFFormat.TURTLE);
    }

    public static String write(Model model, RDFFormat format) {
        model.setNamespace(DCTERMS.NS);
        model.setNamespace(DCAT.NS);
        model.setNamespace(FOAF.NS);
        model.setNamespace(XMLSchema.NS);
        model.setNamespace(LDP.NS);

        try (StringWriter out = new StringWriter()) {
            Rio.write(model, out, format, WRITER_CONFIG);
            return out.toString();
        }
        catch (IOException exception) {
            throw new RuntimeException("Unable to write RDF (IO exception)");
        }
    }

}

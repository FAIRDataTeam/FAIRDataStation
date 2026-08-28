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
package org.fairdatatrain.fairdatastation.service.policy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.algebra.Service;
import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor;
import org.eclipse.rdf4j.query.parser.ParsedBooleanQuery;
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery;
import org.eclipse.rdf4j.query.parser.ParsedOperation;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.ParsedUpdate;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.fairdatatrain.fairdatastation.config.properties.QueryPolicyProperties;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static java.lang.String.format;

/**
 * Authorizes the query a train wants to run before it touches the data store.
 *
 * <p>Checks (when enabled): reject over-long queries; reject any query matching a
 * configured denied pattern; reject write/update operations unless allowed;
 * reject query forms outside the allow-list; and reject SERVICE (federation)
 * clauses unless allowed, since those can exfiltrate this station's data to an
 * external endpoint. A violation throws {@link QueryPolicyException}, which the
 * caller turns into a FAILED job with the reason.
 */
@Slf4j
// RDF4J's Service type is imported above, so qualify the Spring stereotype.
@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class QueryPolicyService {

    private final QueryPolicyProperties properties;

    public void authorizeSparql(String query) {
        if (!properties.isEnabled()) {
            return;
        }
        final QueryPolicyProperties.Sparql cfg = properties.getSparql();

        if (cfg.getMaxQueryLength() > 0 && query.length() > cfg.getMaxQueryLength()) {
            throw deny(format("query exceeds max length (%d > %d)",
                    query.length(), cfg.getMaxQueryLength()));
        }

        for (final String pattern : cfg.getDeniedPatterns()) {
            if (matches(pattern, query)) {
                throw deny(format("query matches a denied pattern (%s)", pattern));
            }
        }

        final ParsedOperation operation = parse(query);

        if (operation instanceof ParsedUpdate) {
            if (!cfg.isAllowUpdates()) {
                throw deny("write/update operations are not permitted");
            }
            return;
        }

        if (operation instanceof ParsedQuery parsedQuery) {
            final String type = queryType(parsedQuery);
            if (!isTypeAllowed(cfg, type)) {
                throw deny(format("query form %s is not permitted", type));
            }
            if (!cfg.isAllowFederation() && hasServiceClause(parsedQuery)) {
                throw deny("federation via SERVICE is not permitted");
            }
        }
    }

    private ParsedOperation parse(String query) {
        try {
            return QueryParserUtil.parseOperation(QueryLanguage.SPARQL, query, null);
        }
        catch (Exception exception) {
            throw deny(format("query could not be parsed (%s)", exception.getMessage()));
        }
    }

    private String queryType(ParsedQuery parsedQuery) {
        if (parsedQuery instanceof ParsedTupleQuery) {
            return "SELECT";
        }
        if (parsedQuery instanceof ParsedBooleanQuery) {
            return "ASK";
        }
        if (parsedQuery instanceof ParsedGraphQuery) {
            // RDF4J parses both CONSTRUCT and DESCRIBE into a graph query.
            return "CONSTRUCT/DESCRIBE";
        }
        return "UNKNOWN";
    }

    private boolean isTypeAllowed(QueryPolicyProperties.Sparql cfg, String type) {
        if (cfg.getAllowedQueryTypes().isEmpty()) {
            return true;
        }
        final boolean has = cfg.getAllowedQueryTypes().stream()
                .anyMatch(allowed -> type.toUpperCase(Locale.ROOT)
                        .contains(allowed.toUpperCase(Locale.ROOT)));
        return has;
    }

    private boolean hasServiceClause(ParsedQuery parsedQuery) {
        final boolean[] found = {false};
        parsedQuery.getTupleExpr().visit(new AbstractQueryModelVisitor<RuntimeException>() {
            @Override
            public void meet(Service node) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private boolean matches(String pattern, String query) {
        try {
            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                    .matcher(query)
                    .find();
        }
        catch (PatternSyntaxException exception) {
            log.warn(format("Ignoring invalid denied pattern '%s': %s",
                    pattern, exception.getMessage()));
            return false;
        }
    }

    private QueryPolicyException deny(String reason) {
        return new QueryPolicyException("Policy: query denied (" + reason + ")");
    }
}

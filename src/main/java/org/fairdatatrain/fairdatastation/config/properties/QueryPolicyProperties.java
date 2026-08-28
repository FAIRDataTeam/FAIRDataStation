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
package org.fairdatatrain.fairdatastation.config.properties;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Station-side authorization policy for the queries a train may run. This is the
 * "is this specific query allowed on my data?" control that sits between an
 * authenticated dispatch and execution. When {@code enabled} is false the
 * station keeps its previous behaviour (parse + read-only check only).
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ConfigurationProperties(prefix = "data-station.policy")
public class QueryPolicyProperties {

    private boolean enabled = false;

    private Sparql sparql = new Sparql();

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class Sparql {

        // Permit write/update operations (INSERT/DELETE/DROP/LOAD/...). Off by default.
        private boolean allowUpdates = false;

        // Permit SERVICE clauses (federation). Off by default: a SERVICE call can
        // ship this station's data to an external endpoint, so it is denied.
        private boolean allowFederation = false;

        // Reject queries longer than this many characters (0 = no limit).
        private int maxQueryLength = 100000;

        // Permitted read query forms. Empty = allow any read form.
        private List<String> allowedQueryTypes =
                new ArrayList<>(List.of("SELECT", "ASK", "CONSTRUCT", "DESCRIBE"));

        // Case-insensitive regexes; a match denies the query.
        private List<String> deniedPatterns = new ArrayList<>();

    }
}

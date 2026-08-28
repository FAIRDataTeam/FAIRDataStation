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
package org.fairdatatrain.fairdatastation.service.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CallbackDestinationPolicyTest {

    private static final List<String> HOSTS =
            List.of("handler.example.org", "*.trusted.example.net", "dev.example.org:8080");

    @Test
    @DisplayName("an exact host on the default port is permitted")
    void permitsExactHost() {
        assertTrue(CallbackDestinationPolicy.allows(HOSTS,
                "https://handler.example.org/runs/1/events"));
    }

    @Test
    @DisplayName("host matching ignores case")
    void permitsDifferentCase() {
        assertTrue(CallbackDestinationPolicy.allows(HOSTS,
                "https://HANDLER.example.ORG/runs/1/events"));
    }

    @Test
    @DisplayName("a wildcard entry covers a subdomain but not the bare suffix")
    void wildcardCoversSubdomainOnly() {
        assertTrue(CallbackDestinationPolicy.allows(HOSTS,
                "https://a.trusted.example.net/cb"));
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://trusted.example.net/cb"));
    }

    @Test
    @DisplayName("a wildcard suffix does not match by string ending alone")
    void wildcardIsNotASuffixMatch() {
        // "nottrusted.example.net" ends with "trusted.example.net" as a string.
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://nottrusted.example.net/cb"));
    }

    @Test
    @DisplayName("a pinned port is required, and an unpinned entry means the default port")
    void enforcesPort() {
        assertTrue(CallbackDestinationPolicy.allows(HOSTS,
                "http://dev.example.org:8080/cb"));
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "http://dev.example.org/cb"));
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://handler.example.org:9999/cb"));
    }

    @Test
    @DisplayName("an allowed host appearing elsewhere in the URL does not pass")
    void doesNotMatchOnSubstring() {
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://evil.example.com/?to=https://handler.example.org/cb"));
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://handler.example.org.evil.example.com/cb"));
    }

    @Test
    @DisplayName("userinfo cannot be used to disguise the real host")
    void doesNotMatchOnUserInfo() {
        assertFalse(CallbackDestinationPolicy.allows(HOSTS,
                "https://handler.example.org@evil.example.com/cb"));
    }

    @ParameterizedTest
    @DisplayName("non-http schemes are refused")
    @ValueSource(strings = {
        "file:///etc/passwd",
        "gopher://handler.example.org/",
        "ftp://handler.example.org/"
    })
    void refusesOtherSchemes(String location) {
        assertFalse(CallbackDestinationPolicy.allows(HOSTS, location));
    }

    @Test
    @DisplayName("an empty allow-list permits nothing, so callers must handle that case")
    void emptyListPermitsNothing() {
        assertFalse(CallbackDestinationPolicy.allows(List.of(),
                "https://handler.example.org/cb"));
        assertFalse(CallbackDestinationPolicy.allows(null,
                "https://handler.example.org/cb"));
    }

    @ParameterizedTest
    @DisplayName("malformed and empty locations are refused")
    @ValueSource(strings = {"", "   ", "not a uri", "https://"})
    void refusesMalformed(String location) {
        assertFalse(CallbackDestinationPolicy.allows(HOSTS, location));
    }
}

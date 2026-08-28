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
package org.fairdatatrain.fairdatastation.service.interaction.fetch;

import org.fairdatatrain.fairdatastation.config.properties.FetchProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every case here uses an IP literal or "localhost" so the guard's address check
 * never depends on a DNS lookup succeeding in the test environment.
 */
class FetchGuardTest {

    private static FetchGuard guard(FetchProperties properties) {
        return new FetchGuard(properties);
    }

    private static FetchProperties defaults() {
        return new FetchProperties();
    }

    @ParameterizedTest
    @DisplayName("loopback and link-local are refused by default")
    @ValueSource(strings = {
        "http://127.0.0.1/train.ttl",
        "http://127.0.0.53:8080/train.ttl",
        "http://localhost/train.ttl",
        "http://169.254.169.254/latest/meta-data/",
        "http://[::1]/train.ttl"
    })
    void refusesLoopbackAndLinkLocalByDefault(String uri) {
        assertThrows(FetchNotPermittedException.class,
                () -> guard(defaults()).checkPermitted(uri));
    }

    @Test
    @DisplayName("private ranges are permitted by default, for the compose deployment")
    void permitsPrivateByDefault() {
        assertDoesNotThrow(() -> guard(defaults()).checkPermitted("http://10.1.2.3/train.ttl"));
        assertDoesNotThrow(() ->
                guard(defaults()).checkPermitted("http://192.168.1.10:8080/train.ttl"));
    }

    @Test
    @DisplayName("private ranges are refused once the deployment turns them off")
    void refusesPrivateWhenDisabled() {
        final FetchProperties properties = defaults();
        properties.setAllowPrivateAddresses(false);
        assertThrows(FetchNotPermittedException.class,
                () -> guard(properties).checkPermitted("http://10.1.2.3/train.ttl"));
    }

    @ParameterizedTest
    @DisplayName("non-http schemes are refused")
    @ValueSource(strings = {
        "file:///etc/passwd",
        "jar:file:///app.jar!/x",
        "ftp://10.1.2.3/train.ttl"
    })
    void refusesOtherSchemes(String uri) {
        assertThrows(FetchNotPermittedException.class,
                () -> guard(defaults()).checkPermitted(uri));
    }

    @Test
    @DisplayName("a host allow-list, once set, excludes everything else")
    void enforcesHostAllowList() {
        final FetchProperties properties = defaults();
        properties.setAllowedHosts(new ArrayList<>(List.of("10.1.2.3")));
        assertDoesNotThrow(() -> guard(properties).checkPermitted("http://10.1.2.3/train.ttl"));
        assertThrows(FetchNotPermittedException.class,
                () -> guard(properties).checkPermitted("http://10.9.9.9/train.ttl"));
    }

    @ParameterizedTest
    @DisplayName("malformed and hostless URIs are refused")
    @ValueSource(strings = {"", "   ", "not a uri", "http://", "/relative/path"})
    void refusesMalformed(String uri) {
        assertThrows(FetchNotPermittedException.class,
                () -> guard(defaults()).checkPermitted(uri));
    }

    @Test
    @DisplayName("a relative redirect is admitted; an absolute one is re-checked")
    void checksRedirectTargets() {
        final FetchGuard fetchGuard = guard(defaults());
        // Resolves against an origin that already passed, so it adds no reach.
        assertDoesNotThrow(() -> fetchGuard.checkRedirectPermitted("/payload.ttl"));
        assertDoesNotThrow(() -> fetchGuard.checkRedirectPermitted("payload.ttl"));
        // The bypass this exists to close: a permitted host redirecting inward.
        assertThrows(FetchNotPermittedException.class,
                () -> fetchGuard.checkRedirectPermitted("http://169.254.169.254/latest/"));
    }

    @Test
    @DisplayName("an over-sized body is refused, and the cap can be lifted")
    void enforcesSizeCap() {
        final FetchProperties properties = defaults();
        properties.setMaxBytes(100);
        assertDoesNotThrow(() -> guard(properties).checkSize("http://10.1.2.3/x", 100));
        assertThrows(FetchNotPermittedException.class,
                () -> guard(properties).checkSize("http://10.1.2.3/x", 101));

        properties.setMaxBytes(0);
        assertDoesNotThrow(() -> guard(properties).checkSize("http://10.1.2.3/x", 999999));
    }

    @Test
    @DisplayName("disabling the guard restores the previous unrestricted behaviour")
    void disabledGuardPermitsEverything() {
        final FetchProperties properties = defaults();
        properties.setEnabled(false);
        assertDoesNotThrow(() ->
                guard(properties).checkPermitted("file:///etc/passwd"));
        assertDoesNotThrow(() ->
                guard(properties).checkPermitted("http://169.254.169.254/latest/"));
    }
}

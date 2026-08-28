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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a handler-supplied callback URL names a destination that
 * handler is permitted to receive results at.
 *
 * <p>The callback locations arrive inside the signed dispatch, so they are
 * handler-attested rather than forgeable by a third party. That is not enough on
 * its own: a compromised or misconfigured handler can name any destination, and
 * the station will faithfully POST result artifacts to it. This check is what
 * turns "the handler asked for it" into "the handler was allowed to ask for it".
 *
 * <p>An entry matches on host, optionally pinning the port ({@code host:port})
 * or covering subdomains ({@code *.suffix}). Matching is deliberately structural
 * rather than a string prefix, so a destination like
 * {@code https://evil.example.org/?x=https://station.example.org} cannot pass by
 * containing an allowed value somewhere in the URL.
 */
public final class CallbackDestinationPolicy {

    private static final String SCHEME_HTTP = "http";

    private static final String SCHEME_HTTPS = "https";

    private static final String WILDCARD_PREFIX = "*.";

    private static final int PORT_HTTP = 80;

    private static final int PORT_HTTPS = 443;

    private CallbackDestinationPolicy() {
    }

    /**
     * True when {@code location} is a well-formed http(s) URL whose authority is
     * covered by one of {@code allowedHosts}.
     */
    public static boolean allows(List<String> allowedHosts, String location) {
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            return false;
        }
        final URI uri = parse(location);
        if (uri == null) {
            return false;
        }
        final String scheme = lower(uri.getScheme());
        if (!SCHEME_HTTP.equals(scheme) && !SCHEME_HTTPS.equals(scheme)) {
            return false;
        }
        final String host = lower(uri.getHost());
        if (host == null || host.isBlank()) {
            return false;
        }
        final int port = uri.getPort() == -1 ? defaultPort(scheme) : uri.getPort();
        return allowedHosts.stream()
                .anyMatch(entry -> matches(entry, host, port, scheme));
    }

    private static boolean matches(String entry, String host, int port, String scheme) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        final String candidate = lower(entry.trim());
        final int separator = candidate.lastIndexOf(':');
        String hostPattern = candidate;
        Integer requiredPort = null;
        // A trailing ":<digits>" pins the port; anything else is part of the host.
        if (separator > 0 && separator < candidate.length() - 1) {
            final String tail = candidate.substring(separator + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                hostPattern = candidate.substring(0, separator);
                requiredPort = Integer.valueOf(tail);
            }
        }
        if (requiredPort == null) {
            // No port pinned: accept only the scheme's default, so an allow-list
            // entry cannot be widened to an arbitrary port by the caller.
            if (port != defaultPort(scheme)) {
                return false;
            }
        }
        else if (requiredPort != port) {
            return false;
        }
        return hostMatches(hostPattern, host);
    }

    private static boolean hostMatches(String pattern, String host) {
        if (pattern.startsWith(WILDCARD_PREFIX)) {
            final String suffix = pattern.substring(WILDCARD_PREFIX.length());
            // "*.example.org" covers "a.example.org" but not "example.org"
            // itself, and not "notexample.org".
            return !suffix.isBlank() && host.endsWith("." + suffix);
        }
        return pattern.equals(host);
    }

    private static URI parse(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        try {
            return new URI(location.trim());
        }
        catch (URISyntaxException exception) {
            return null;
        }
    }

    private static int defaultPort(String scheme) {
        return SCHEME_HTTPS.equals(scheme) ? PORT_HTTPS : PORT_HTTP;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}

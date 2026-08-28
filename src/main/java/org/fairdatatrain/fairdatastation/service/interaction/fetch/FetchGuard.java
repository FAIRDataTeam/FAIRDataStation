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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.config.properties.FetchProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

import static java.lang.String.format;

/**
 * Admits or refuses a URI before the station fetches it.
 *
 * <p>Train and payload URIs are supplied by a dispatching handler, or read out of
 * metadata that was itself fetched from one, so an unchecked GET makes the
 * station a request-forwarding proxy for whatever it can route to. The guard
 * resolves the host and refuses address classes a train URI has no business
 * naming, which is the difference between "fetching a train" and "fetching
 * anything on the station's network on request".
 *
 * <p>This is a mitigation and not a complete defence: resolution here and
 * resolution by the HTTP client are two separate lookups, so a name that changes
 * answer between them (DNS rebinding) is not addressed. Closing that requires
 * pinning the resolved address into the connection, which the shared WebClient
 * does not currently expose.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FetchGuard {

    private static final String WILDCARD_PREFIX = "*.";

    private final FetchProperties properties;

    /**
     * @throws FetchNotPermittedException if the station may not fetch this URI
     */
    public void checkPermitted(String uri) {
        if (!properties.isEnabled()) {
            return;
        }
        final URI parsed = parse(uri);
        final String scheme = lower(parsed.getScheme());
        if (scheme == null || !containsIgnoreCase(properties.getAllowedSchemes(), scheme)) {
            throw refuse(uri, format("scheme '%s' is not permitted", scheme));
        }
        final String host = lower(parsed.getHost());
        if (host == null || host.isBlank()) {
            throw refuse(uri, "no host");
        }
        if (properties.hasHostAllowList() && !hostAllowed(host)) {
            throw refuse(uri, "host is not on the allow-list");
        }
        checkAddress(uri, host);
    }

    /**
     * The same test applied to a redirect target. A relative {@code Location} is
     * admitted without further checking: it resolves against an origin that has
     * already passed {@link #checkPermitted}, so it cannot move the request to a
     * host or address class that was not already allowed.
     *
     * @throws FetchNotPermittedException if the station may not follow this hop
     */
    public void checkRedirectPermitted(String location) {
        if (!properties.isEnabled()) {
            return;
        }
        final URI parsed = parse(location);
        if (parsed.getScheme() == null && parsed.getHost() == null) {
            return;
        }
        checkPermitted(location);
    }

    /**
     * Refuse a body larger than the configured cap. Applied to what was actually
     * read, since a remote server's Content-Length is not a promise.
     */
    public void checkSize(String uri, long bytes) {
        final long max = properties.getMaxBytes();
        if (properties.isEnabled() && max > 0 && bytes > max) {
            throw refuse(uri, format("response of %d bytes exceeds the %d byte cap",
                    bytes, max));
        }
    }

    public long maxBytes() {
        return properties.isEnabled() ? properties.getMaxBytes() : 0;
    }

    private void checkAddress(String uri, String host) {
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        }
        catch (UnknownHostException exception) {
            throw refuse(uri, format("host '%s' does not resolve", host));
        }
        // Every answer must be acceptable: a name resolving to both a public and
        // a loopback address must not be admitted on the strength of the former.
        for (final InetAddress address : addresses) {
            if (!properties.isAllowLoopbackAddresses()
                    && (address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress())) {
                throw refuse(uri, "resolves to a loopback or link-local address");
            }
            if (!properties.isAllowPrivateAddresses() && address.isSiteLocalAddress()) {
                throw refuse(uri, "resolves to a private address");
            }
        }
    }

    private boolean hostAllowed(String host) {
        return properties.getAllowedHosts().stream()
                .map(this::lower)
                .anyMatch(entry -> entryMatches(entry, host));
    }

    private boolean entryMatches(String entry, String host) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        final String pattern = entry.trim();
        if (pattern.startsWith(WILDCARD_PREFIX)) {
            final String suffix = pattern.substring(WILDCARD_PREFIX.length());
            return !suffix.isBlank() && host.endsWith("." + suffix);
        }
        return pattern.equals(host);
    }

    private URI parse(String uri) {
        if (uri == null || uri.isBlank()) {
            throw refuse(uri, "empty URI");
        }
        try {
            return new URI(uri.trim());
        }
        catch (URISyntaxException exception) {
            throw refuse(uri, "malformed URI");
        }
    }

    private boolean containsIgnoreCase(List<String> values, String needle) {
        return values.stream()
                .map(this::lower)
                .anyMatch(needle::equals);
    }

    private FetchNotPermittedException refuse(String uri, String reason) {
        log.warn(format("Refused to fetch '%s': %s", uri, reason));
        return new FetchNotPermittedException(
                format("Fetch of '%s' is not permitted (%s)", uri, reason));
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}

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
 * Bounds on the outbound requests the station makes while resolving a train.
 *
 * <p>Train and payload URIs arrive from a dispatching handler and from remotely
 * fetched metadata, so they are attacker-influenced: the station will otherwise
 * issue an unauthenticated GET to any URI it is handed, which gives a caller
 * request-forgery reach into whatever the station can route to, and will read a
 * response of any size. These properties are what make that reach and that size
 * a deliberate deployment decision.
 *
 * <p>The defaults are chosen so that a standard compose deployment keeps working:
 * loopback and link-local are refused, because no legitimate train URI names them
 * and one of them — 169.254.169.254 — is the cloud instance-metadata endpoint that
 * makes request forgery worth attempting in the first place; private ranges stay
 * reachable, because in the reference deployment the FAIR Data Point is a sibling
 * container on exactly such an address. A production station should additionally
 * set an explicit host allow-list and turn private addresses off.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ConfigurationProperties(prefix = "data-station.fetch")
public class FetchProperties {

    private static final long DEFAULT_MAX_BYTES = 16L * 1024 * 1024;

    // Apply these bounds. Off leaves the previous unrestricted behaviour.
    private boolean enabled = true;

    // Permitted URI schemes. "file" and "jar" are absent by design.
    private List<String> allowedSchemes =
            new ArrayList<>(List.of("http", "https"));

    // Hosts the station may fetch from, as "host" or "*.suffix".
    // Empty means any host, subject to allowPrivateAddresses.
    private List<String> allowedHosts = new ArrayList<>();

    // Permit fetching from loopback (127.0.0.0/8, ::1) and link-local
    // (169.254.0.0/16, fe80::/10) addresses. Off by default: no train URI has a
    // legitimate reason to name one, and 169.254.169.254 is the cloud
    // instance-metadata endpoint.
    private boolean allowLoopbackAddresses = false;

    // Permit fetching from private ranges (10/8, 172.16/12, 192.168/16, fc00::/7).
    // On by default because the reference compose deployment puts the FAIR Data
    // Point on a container network. Turn this off in production and pair it with
    // an explicit allowedHosts list.
    private boolean allowPrivateAddresses = true;

    // Refuse a response body larger than this many bytes (0 = no limit).
    private long maxBytes = DEFAULT_MAX_BYTES;

    public boolean hasHostAllowList() {
        return !allowedHosts.isEmpty();
    }
}

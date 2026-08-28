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
 * Controls authentication of incoming train dispatches. When {@code enabled} is
 * true, the station only accepts a dispatch that is signed by a handler listed
 * in {@code allowedHandlers}; otherwise the endpoint stays open (legacy
 * behaviour). The allow-list maps a handler identity to its Ed25519 public key.
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ConfigurationProperties(prefix = "data-station.dispatch")
public class DispatchAuthProperties {

    // Enforce signature verification on POST /trains.
    private boolean enabled = false;

    // Allowed difference between the signed timestamp and now, to limit replay.
    private long maxClockSkewSeconds = 300;

    // Trusted handlers permitted to dispatch trains to this station.
    private List<AllowedHandler> allowedHandlers = new ArrayList<>();

    public AllowedHandler findById(String id) {
        if (id == null) {
            return null;
        }
        return allowedHandlers.stream()
                .filter(handler -> id.equals(handler.getId()))
                .findFirst()
                .orElse(null);
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class AllowedHandler {

        // Handler identity, matched against the X-FDT-Handler-Id header.
        private String id;

        // Base64-encoded X.509 (SubjectPublicKeyInfo) Ed25519 public key.
        private String publicKey;

    }
}

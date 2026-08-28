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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.train.TrainDispatchPayloadDTO;
import org.fairdatatrain.fairdatastation.config.properties.DispatchAuthProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static java.lang.String.format;

/**
 * Authenticates an incoming train dispatch before it is queued.
 *
 * <p>Flow: reject if a required header is missing, the timestamp is stale, the
 * announced handler is not on the allow-list, or the Ed25519 signature does not
 * verify against that handler's public key. Every one of those is an
 * authentication failure and every one answers <strong>401</strong>: reporting a
 * distinguishable status for an unrecognised identity would tell an
 * unauthenticated caller which handlers are enrolled here.
 *
 * <p>Only once the signature has verified is the caller's own attested data
 * examined — the callback destinations it named must be ones this handler is
 * permitted to receive results at (403, an authorisation failure by a caller
 * whose identity is by then established).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchVerifier {

    private static final String ALGORITHM = "Ed25519";

    // One reason for every authentication outcome, so the response body cannot
    // distinguish an unknown identity from a bad signature either.
    private static final String MSG_AUTH_FAILED = "Dispatch authentication failed";

    private static final String MSG_BAD_DESTINATION = "Callback destination is not permitted";

    private static final long MILLIS_PER_SECOND = 1000L;

    private final DispatchAuthProperties properties;

    public void verify(
            String handlerId,
            String timestampHeader,
            String signature,
            TrainDispatchPayloadDTO payload
    ) {
        if (!properties.isEnabled()) {
            return;
        }
        if (isBlank(handlerId) || isBlank(timestampHeader) || isBlank(signature)) {
            throw unauthorized("Missing dispatch authentication headers");
        }

        final long timestamp = parseTimestamp(timestampHeader);
        final long skew = Math.abs(System.currentTimeMillis() - timestamp);
        if (skew > properties.getMaxClockSkewSeconds() * MILLIS_PER_SECOND) {
            throw unauthorized("Dispatch timestamp is outside the accepted window");
        }

        final DispatchAuthProperties.AllowedHandler handler = properties.findById(handlerId);
        if (handler == null) {
            log.warn(format("Rejected dispatch from unknown handler '%s'", handlerId));
            // Deliberately the same status and reason as a bad signature, so the
            // response cannot be used to enumerate the allow-list.
            throw unauthorized(MSG_AUTH_FAILED);
        }

        final String canonical = canonicalString(handlerId, timestamp, payload);
        if (!verifySignature(handler.getPublicKey(), canonical, signature)) {
            log.warn(format("Invalid dispatch signature from handler '%s'", handlerId));
            throw unauthorized(MSG_AUTH_FAILED);
        }

        verifyCallbackDestinations(handler, payload);
        log.info(format("Accepted signed dispatch from handler '%s'", handlerId));
    }

    /**
     * The callback locations are signed, so they are this handler's own claim
     * about where results should go. That makes them trustworthy as to origin and
     * says nothing about whether the destination is acceptable, so they are
     * checked against the handler's declared allow-list.
     */
    private void verifyCallbackDestinations(
            DispatchAuthProperties.AllowedHandler handler, TrainDispatchPayloadDTO payload
    ) {
        if (!handler.hasCallbackAllowList()) {
            if (properties.isRequireCallbackAllowList()) {
                log.warn(format(
                        "Rejected dispatch from handler '%s': no callback allow-list declared",
                        handler.getId()
                ));
                throw forbidden(MSG_BAD_DESTINATION);
            }
            log.warn(format(
                    "Handler '%s' has no callback allow-list; result destinations "
                            + "are unconstrained. Set allowed-callback-hosts to close this.",
                    handler.getId()
            ));
            return;
        }
        checkDestination(handler, payload.getCallbackEventLocation(), "event");
        checkDestination(handler, payload.getCallbackArtifactLocation(), "artifact");
    }

    private void checkDestination(
            DispatchAuthProperties.AllowedHandler handler, String location, String kind
    ) {
        if (!CallbackDestinationPolicy.allows(handler.getAllowedCallbackHosts(), location)) {
            log.warn(format(
                    "Rejected dispatch from handler '%s': %s callback destination '%s' "
                            + "is not on its allow-list",
                    handler.getId(), kind, location
            ));
            throw forbidden(MSG_BAD_DESTINATION);
        }
    }

    /**
     * MUST match the handler's canonical format exactly (field order + separator).
     */
    private String canonicalString(
            String handlerId, long timestamp, TrainDispatchPayloadDTO payload
    ) {
        return String.join("\n",
                handlerId,
                Long.toString(timestamp),
                payload.getJobUuid(),
                payload.getTrainUri(),
                payload.getCallbackEventLocation(),
                payload.getCallbackArtifactLocation(),
                payload.getSecret()
        );
    }

    private boolean verifySignature(String base64PublicKey, String canonical, String signature) {
        try {
            final byte[] der = Base64.getDecoder().decode(base64PublicKey.trim());
            final PublicKey publicKey = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(der));
            final Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(canonical.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature.trim()));
        }
        catch (Exception exception) {
            log.warn(format("Signature verification error: %s", exception.getMessage()));
            return false;
        }
    }

    private long parseTimestamp(String value) {
        try {
            return Long.parseLong(value.trim());
        }
        catch (NumberFormatException exception) {
            throw unauthorized("Malformed dispatch timestamp");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private ResponseStatusException unauthorized(String reason) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, reason);
    }

    /**
     * For a caller whose identity is established but whose request is not
     * permitted. Reachable only after the signature verifies, so it discloses
     * nothing to an unauthenticated caller.
     */
    private ResponseStatusException forbidden(String reason) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, reason);
    }
}

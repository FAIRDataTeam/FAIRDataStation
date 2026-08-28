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
 * <p>Flow: reject if a required header is missing or the timestamp is stale
 * (401); reject if the announced handler is not on the allow-list (403); reject
 * if the Ed25519 signature does not verify against that handler's public key
 * (401). Only then is the dispatch allowed to proceed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchVerifier {

    private static final String ALGORITHM = "Ed25519";

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
        if (skew > properties.getMaxClockSkewSeconds() * 1000L) {
            throw unauthorized("Dispatch timestamp is outside the accepted window");
        }

        final DispatchAuthProperties.AllowedHandler handler = properties.findById(handlerId);
        if (handler == null) {
            log.warn(format("Rejected dispatch from unknown handler '%s'", handlerId));
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Handler is not on the allow-list");
        }

        final String canonical = canonicalString(handlerId, timestamp, payload);
        if (!verifySignature(handler.getPublicKey(), canonical, signature)) {
            log.warn(format("Invalid dispatch signature from handler '%s'", handlerId));
            throw unauthorized("Invalid dispatch signature");
        }
        log.info(format("Accepted signed dispatch from handler '%s'", handlerId));
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
}

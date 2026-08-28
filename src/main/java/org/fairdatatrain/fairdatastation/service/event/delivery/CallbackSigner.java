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
package org.fairdatatrain.fairdatastation.service.event.delivery;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fairdatatrain.fairdatastation.api.dto.event.job.artifact.JobArtifactDispatchDTO;
import org.fairdatatrain.fairdatastation.api.dto.event.job.event.JobEventDispatchDTO;
import org.fairdatatrain.fairdatastation.config.properties.CallbackSigningProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

import static org.fairdatatrain.fairdatastation.utils.HashUtils.sha256Hex;

/**
 * Signs the job events and artifacts this station sends back to a train handler,
 * so the handler can tell a genuine result from a forged one. Mirrors the
 * handler-side {@code DispatchSigner}, in the opposite direction.
 *
 * <p>The per-job {@code secret} is deliberately NOT part of the signed material:
 * it is a bearer value that anyone on the dispatch path may have seen, so it
 * cannot establish authorship. It stays in the payload only for compatibility.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackSigner {

    public static final String HEADER_ID = "X-FDS-Station-Id";

    public static final String HEADER_TIMESTAMP = "X-FDS-Timestamp";

    public static final String HEADER_SIGNATURE = "X-FDS-Signature";

    private static final String ALGORITHM = "Ed25519";

    // Domain separation: an event signature must not be replayable as an artifact.
    private static final String KIND_EVENT = "job-event";

    private static final String KIND_ARTIFACT = "job-artifact";

    private static final String ABSENT = "-";

    private final CallbackSigningProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Builds the exact string both sides sign/verify for a job event. The handler
     * reconstructs this from the received DTO, the path variables and the headers,
     * so the field order and separator MUST stay identical on both sides.
     *
     * <p>{@code handlerJobUuid} is the handler's own job UUID, which the station
     * stored as {@code job.remoteId} when the train was dispatched. Including it
     * binds the callback to the job the handler actually dispatched here.
     */
    public static String canonicalEvent(
            String stationId,
            long timestamp,
            String handlerJobUuid,
            JobEventDispatchDTO payload
    ) {
        return String.join("\n",
                KIND_EVENT,
                stationId,
                Long.toString(timestamp),
                orAbsent(handlerJobUuid),
                orAbsent(payload.getRemoteId()),
                payload.getResultStatus() == null
                        ? ABSENT : payload.getResultStatus().name(),
                epochMillis(payload.getOccurredAt()),
                sha256Hex(payload.getMessage())
        );
    }

    /**
     * Canonical string for a job artifact. The artifact bytes are covered
     * transitively: {@code hash} is the SHA-256 of the payload data and the
     * handler already re-computes it from {@code base64data} before storing.
     */
    public static String canonicalArtifact(
            String stationId,
            long timestamp,
            String handlerJobUuid,
            JobArtifactDispatchDTO payload
    ) {
        return String.join("\n",
                KIND_ARTIFACT,
                stationId,
                Long.toString(timestamp),
                orAbsent(handlerJobUuid),
                orAbsent(payload.getRemoteId()),
                sha256Hex(payload.getDisplayName()),
                sha256Hex(payload.getFilename()),
                sha256Hex(payload.getContentType()),
                payload.getBytesize() == null
                        ? ABSENT : Long.toString(payload.getBytesize()),
                orAbsent(payload.getHash()),
                epochMillis(payload.getOccurredAt())
        );
    }

    public SignedHeaders signEvent(String handlerJobUuid, JobEventDispatchDTO payload) {
        final String identity = properties.getIdentity();
        final long timestamp = System.currentTimeMillis();
        return sign(
                identity,
                timestamp,
                canonicalEvent(identity, timestamp, handlerJobUuid, payload)
        );
    }

    public SignedHeaders signArtifact(String handlerJobUuid, JobArtifactDispatchDTO payload) {
        final String identity = properties.getIdentity();
        final long timestamp = System.currentTimeMillis();
        return sign(
                identity,
                timestamp,
                canonicalArtifact(identity, timestamp, handlerJobUuid, payload)
        );
    }

    private SignedHeaders sign(String identity, long timestamp, String canonical) {
        try {
            final Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(loadPrivateKey(properties.getPrivateKey()));
            signature.update(canonical.getBytes(StandardCharsets.UTF_8));
            final String encoded = Base64.getEncoder().encodeToString(signature.sign());
            return new SignedHeaders(identity, timestamp, encoded);
        }
        catch (Exception exception) {
            throw new RuntimeException(
                    "Failed to sign callback: " + exception.getMessage(), exception
            );
        }
    }

    private PrivateKey loadPrivateKey(String base64Pkcs8) throws Exception {
        final byte[] der = Base64.getDecoder().decode(base64Pkcs8.trim());
        return KeyFactory.getInstance(ALGORITHM)
                .generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String epochMillis(Instant instant) {
        return instant == null ? ABSENT : Long.toString(instant.toEpochMilli());
    }

    private static String orAbsent(String value) {
        return value == null || value.isBlank() ? ABSENT : value;
    }

    public record SignedHeaders(String identity, long timestamp, String signature) {
    }
}

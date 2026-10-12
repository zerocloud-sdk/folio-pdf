package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Initialization v2 keeps deadline authority in the caller process. */
public final class WorkerInitializationTimingTest {

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void relativeDeadlineCannotBecomeAChildWallClockBudget() throws Exception {
        byte[] encoded = WorkerMessages.encodeInitialization(
                WorkflowRequest.builder().saveMode(SaveMode.REWRITE)
                        .deadline(Instant.parse("2026-01-01T00:00:05Z")).build(),
                WorkflowResourcePolicy.safeDefaults(),
                Collections.<String, Path>emptyMap(), Collections.<String, Path>emptyMap(),
                Collections.<Path>emptyList(), 4096);
        try (WorkerMessages.DecodedInitialization decoded = WorkerMessages.decodeInitialization(
                encoded, temporary.getRoot().toPath())) {
            assertEquals(null, decoded.getRequest().getDeadline());
        }
        WorkerCodecIO.Input input = WorkerCodecIO.input(encoded);
        input.readInt(); // Initialization version.
        input.readInt(); // Message bound.
        input.readInt(); // Policy version.
        input.readLong(); input.readInt(); // Input bytes/pages.
        input.readLong(); input.readInt(); // Objects/nesting.
        for (int field = 0; field < 5; field++) { input.readLong(); }
        input.readInt(); input.readInt(); // Elapsed nanoseconds/concurrency.
        input.readString(); input.readNullableString(); // Save Mode/primary Source.
        int offset = encoded.length - input.available();
        assertFalse(input.readBoolean());
        // A formerly accepted relative five-second budget, with the complete
        // valid remainder preserved, must not acquire Instant.now() semantics.
        ByteBuffer altered = ByteBuffer.allocate(encoded.length + 12);
        altered.put(encoded, 0, offset).put((byte) 1).putLong(5L).putInt(0);
        altered.put(encoded, offset + 1, encoded.length - offset - 1);
        try (WorkerMessages.DecodedInitialization ignored = WorkerMessages.decodeInitialization(
                altered.array(), temporary.getRoot().toPath())) {
            fail("Expected parent-owned deadline enforcement");
        } catch (DocumentFailure failure) {
            assertEquals(DocumentFailureCode.WORKER_PROTOCOL_REJECTED, failure.getCode());
            assertEquals("The Worker deadline must remain parent-owned.", failure.getDiagnostic());
        }
    }
}

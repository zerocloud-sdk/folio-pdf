package net.zerocloud.pdf.acceptance;

import static org.junit.Assert.*;
import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Every symbol is generated and reopened through the public Workflow seam. */
public final class T31BarcodeEvidenceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void declaredModesHaveIndependentPayloadStructureAndGeometryEvidence() throws Exception {
        for (WorkflowExecutionProfile profile : WorkflowExecutionProfile.values()) {
            Path pdf = temporary.newFile().toPath();
            T31BarcodeProducts.create(pdf, profile);
            T31BarcodeAssertions.Observation observed = T31BarcodeAssertions.inspect(pdf);
            assertTrue(observed.findings, observed.passed);
            assertEquals(T31BarcodeProfile.fixtures().size(), observed.matrices.size());
            assertEquals(224, observed.matrices.size());
            for (String id : new String[] {"qr-numeric", "qr-alphanumeric", "qr-byte", "qr-kanji",
                    "dm-raw", "dm-reader", "dm-sequence-max", "pdf-numeric", "pdf-binary",
                    "pdf-raw", "pdf-macro-single", "pdf-ecc-8", "dm-size-144x144", "qr-version-40",
                    "dm-raw-edifact-auto", "dm-raw-edifact-fixed", "dm-raw-edifact-ascii", "dm-raw-edifact-tail",
                    "pdf-raw-eci-leading", "pdf-raw-eci-literal", "pdf-raw-eci-charset", "pdf-raw-eci-924-leading",
                    "pdf-raw-eci-924-between", "pdf-raw-eci-text-latch", "pdf-raw-eci-consecutive", "pdf-raw-eci-group"}) {
                assertTrue(id, observed.findings.contains(id + ": payload="));
            }
        }
    }
}

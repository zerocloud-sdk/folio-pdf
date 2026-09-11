package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Aggregates public XML and signed-Source safety evidence for T11. */
final class T11SafetyEvidence {
    private T11SafetyEvidence() {
    }

    static Properties record(
            Path output,
            WorkflowExecutionProfile nativeExecution) throws Exception {
        Properties result = T11XmlSafetyProbe.record(output, nativeExecution);
        Properties signed = T11SignedProtectionEvidence.record(
                output, nativeExecution);
        result.setProperty("signed", signed.getProperty("result"));
        if (!"pass".equals(signed.getProperty("result"))) {
            result.setProperty("result", "fail");
        }
        T11MetadataProducts.save(output.resolve("result.properties"), result);
        return result;
    }
}

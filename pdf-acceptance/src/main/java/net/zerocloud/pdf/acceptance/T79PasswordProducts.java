package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Original public products for the independently frozen clear-metadata profile. */
final class T79PasswordProducts {
    private T79PasswordProducts() { }
    static RetainedEvidence create(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        return PasswordProducts.create(root, output, execution, true);
    }
}

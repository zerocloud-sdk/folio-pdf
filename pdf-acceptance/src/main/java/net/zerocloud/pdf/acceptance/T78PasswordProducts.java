package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Original public products for the independently frozen baseline profile. */
final class T78PasswordProducts {
    private T78PasswordProducts() { }
    static RetainedEvidence create(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        return PasswordProducts.create(root, output, execution, false);
    }
}

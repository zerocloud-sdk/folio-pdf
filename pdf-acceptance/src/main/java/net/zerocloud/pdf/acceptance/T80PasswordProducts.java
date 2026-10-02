package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Public products remain separate from independent attachment-scope observers. */
final class T80PasswordProducts {
    private T80PasswordProducts() { }
    static RetainedEvidence create(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        return PasswordProducts.create(root, output, execution, "T80-embedded-files-only", true);
    }
}

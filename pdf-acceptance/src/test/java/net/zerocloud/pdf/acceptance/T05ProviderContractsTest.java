package net.zerocloud.pdf.acceptance;

import java.nio.file.Path;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class T05ProviderContractsTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void recordsAllActualProviderCases() throws Exception {
        Path output = temporary.newFolder("provider-observations").toPath();
        T05ProviderContracts.record(output, WorkflowExecutionProfile.valueOf(
                System.getProperty("folio.t05.executionProfile", "IN_PROCESS"))).verify();
    }
}

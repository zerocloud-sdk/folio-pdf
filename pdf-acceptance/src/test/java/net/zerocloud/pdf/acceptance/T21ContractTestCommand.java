package net.zerocloud.pdf.acceptance;

import java.io.PrintStream;
import java.util.Set;

/** Runs every frozen T21 case with zero skipped mandatory execution. */
public final class T21ContractTestCommand {
    private T21ContractTestCommand() { }

    public static void main(String[] arguments) throws Exception {
        RequiredCaseRunner.main("T21", "T21-hardened-worker-boundary", arguments);
    }

    static int run(PrintStream output, Set<String> required, Class<?>... classes) {
        return RequiredCaseRunner.run("T21", output, required, classes);
    }
}

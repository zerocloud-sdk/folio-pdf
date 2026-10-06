package net.zerocloud.pdf.acceptance;

import java.io.PrintStream;
import junit.runner.Version;
import org.junit.internal.TextListener;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/** Runs the required public/artifact suite and rejects ignored or assumption-skipped tests. */
public final class T20ContractTestCommand {

    private T20ContractTestCommand() {
    }

    public static void main(String[] args) throws ClassNotFoundException {
        int expected = Integer.parseInt(args[0]);
        Class<?>[] classes = new Class<?>[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            classes[i - 1] = Class.forName(args[i]);
        }
        System.exit(run(System.out, expected, classes));
    }

    static int run(PrintStream output, int expected, Class<?>... classes) {
        final int[] assumptions = {0};
        JUnitCore core = new JUnitCore();
        core.addListener(new TextListener(output));
        core.addListener(new RunListener() {
            @Override
            public void testAssumptionFailure(Failure failure) {
                assumptions[0]++;
            }
        });
        output.println("JUnit version " + Version.id());
        Result result = core.run(classes);
        output.println("T20 required execution: tests=" + result.getRunCount()
                + ", failures=" + result.getFailureCount()
                + ", ignored=" + result.getIgnoreCount()
                + ", assumptions=" + assumptions[0]);
        return result.wasSuccessful() && result.getRunCount() == expected
                && result.getIgnoreCount() == 0 && assumptions[0] == 0 ? 0 : 1;
    }
}

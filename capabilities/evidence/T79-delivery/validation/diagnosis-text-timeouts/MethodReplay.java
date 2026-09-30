import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Diagnostic runner for unchanged staged public tests; preserves their timeouts. */
public final class MethodReplay {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("net.zerocloud.pdf.consumer.TextStructureExtractionWorkflowTest");
        boolean passed = true;
        int repetitions = Integer.parseInt(args[0]);
        for (int iteration = 1; iteration <= repetitions; iteration++) {
            for (int index = 1; index < args.length; index++) {
                long wall = System.currentTimeMillis();
                long monotonic = System.nanoTime();
                Result result = new JUnitCore().run(Request.method(type, args[index]));
                System.out.println("REPLAY " + iteration + " " + args[index]
                        + " tests=" + result.getRunCount() + " failures=" + result.getFailureCount()
                        + " elapsedMillis=" + (System.currentTimeMillis() - wall)
                        + " monotonicMillis=" + ((System.nanoTime() - monotonic) / 1000000L));
                for (Failure failure : result.getFailures()) {
                    System.out.println(failure.getTrace());
                }
                passed &= result.wasSuccessful() && result.getRunCount() == 1;
            }
        }
        if (!passed) System.exit(1);
    }
}

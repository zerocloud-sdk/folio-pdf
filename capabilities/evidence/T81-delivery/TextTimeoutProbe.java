import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/** Diagnostic-only invocation of an existing public test; never acceptance evidence. */
public final class TextTimeoutProbe {
    public static void main(String[] args) throws Exception {
        Result result = new JUnitCore().run(Request.method(Class.forName(args[0]), args[1]));
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.getTrace());
        }
        System.out.println("run=" + result.getRunCount() + " failures="
                + result.getFailureCount() + " ignored=" + result.getIgnoreCount()
                + " elapsed-ms=" + result.getRunTime());
        if (result.getRunCount() != 1 || !result.wasSuccessful() || result.getIgnoreCount() != 0) {
            System.exit(1);
        }
    }
}

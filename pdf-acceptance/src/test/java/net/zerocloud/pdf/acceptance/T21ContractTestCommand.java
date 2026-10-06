package net.zerocloud.pdf.acceptance;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import junit.runner.Version;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/** Runs every frozen T21 case; counts alone cannot establish complete execution. */
public final class T21ContractTestCommand {
    private T21ContractTestCommand() { }

    public static void main(String[] arguments) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(arguments[0]), StandardCharsets.UTF_8);
        Set<String> required = new TreeSet<String>(lines);
        if (required.size() != lines.size() || required.contains("")) {
            throw new IllegalArgumentException("Duplicate or empty mandatory T21 case");
        }
        Class<?>[] classes = new Class<?>[arguments.length - 1];
        for (int i = 1; i < arguments.length; i++) { classes[i - 1] = Class.forName(arguments[i]); }
        int status = run(System.out, required, classes);
        String boundary = System.getProperty("folio.t21.observationOutput");
        if (boundary != null) {
            java.nio.file.Path directory = Paths.get(boundary);
            Files.createDirectories(directory);
            RetainedEvidence retained = new RetainedEvidence(directory);
            try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(directory)) {
                for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.filter(Files::isRegularFile)::iterator) {
                    retained.retain(path, EvidenceFiles.sha256(path));
                }
            }
            String manifest = retained.publishManifest(status == 0 ? "pass" : "fail");
            java.util.Properties result = new java.util.Properties();
            result.setProperty("profile", "T21-hardened-worker-boundary");
            result.setProperty("result", status == 0 ? "pass" : "fail");
            result.setProperty("required-case-count", Integer.toString(required.size()));
            result.setProperty("retained-files-sha256", manifest);
            retained.write(directory.resolve("result.properties"), result);
            retained.verify();
            System.out.println("T21 boundary retained-sha256=" + manifest);
        }
        System.exit(status);
    }

    static int run(PrintStream output, Set<String> required, Class<?>... classes) {
        output.println("JUnit version " + Version.id());
        Runner runner = Request.classes(classes).getRunner();
        Set<String> declared = new TreeSet<String>();
        boolean unique = inventory(runner.getDescription(), declared);
        if (!unique || !declared.equals(required)) {
            output.println("T21 mandatory case inventory differs: required=" + required + "; declared=" + declared);
            return 1;
        }
        final Set<String> started = new HashSet<String>();
        final Set<String> finished = new HashSet<String>();
        final Set<String> failed = new HashSet<String>();
        final int[] assumptions = {0}, duplicates = {0};
        JUnitCore core = new JUnitCore();
        core.addListener(new RunListener() {
            @Override public void testStarted(Description description) {
                if (!started.add(name(description))) { duplicates[0]++; }
            }
            @Override public void testFinished(Description description) {
                if (!finished.add(name(description))) { duplicates[0]++; }
            }
            @Override public void testFailure(Failure failure) {
                failed.add(name(failure.getDescription()));
                output.println(failure.getTrace());
            }
            @Override public void testAssumptionFailure(Failure failure) {
                assumptions[0]++;
                failed.add(name(failure.getDescription()));
            }
            @Override public void testIgnored(Description description) { failed.add(name(description)); }
        });
        Result result = core.run(runner);
        for (String test : required) {
            output.println("T21 CASE " + test + " = "
                    + (started.contains(test) && finished.contains(test) && !failed.contains(test) ? "PASS" : "FAIL"));
        }
        output.println("T21 required execution: tests=" + result.getRunCount()
                + ", failures=" + result.getFailureCount() + ", ignored=" + result.getIgnoreCount()
                + ", assumptions=" + assumptions[0] + ", duplicates=" + duplicates[0]);
        boolean pass = result.wasSuccessful() && result.getRunCount() == required.size()
                && result.getIgnoreCount() == 0 && assumptions[0] == 0 && duplicates[0] == 0
                && started.equals(required) && finished.equals(required) && failed.isEmpty();
        output.println(pass ? "OK (" + required.size() + " tests)" : "FAILURES!!!");
        return pass ? 0 : 1;
    }

    private static boolean inventory(Description description, Set<String> names) {
        if (description.isTest()) { return names.add(name(description)); }
        boolean unique = true;
        for (Description child : description.getChildren()) { unique &= inventory(child, names); }
        return unique;
    }

    private static String name(Description description) {
        return description.getClassName() + "#" + description.getMethodName();
    }
}

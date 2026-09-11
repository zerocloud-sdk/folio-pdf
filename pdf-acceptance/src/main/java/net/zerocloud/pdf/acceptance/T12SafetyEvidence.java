package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import net.zerocloud.pdf.WorkflowExecutionProfile;

/** Binds public Action probes to qualified cross-process Linux observations. */
final class T12SafetyEvidence {
    private T12SafetyEvidence() { }

    static Properties record(Path root, Path output, WorkflowExecutionProfile execution) throws Exception {
        Path script = root.resolve("scripts/t12-safety-observer.py");
        Path probe = Files.createDirectory(output.resolve("probe"));
        ProcessResult invocation = ExternalProcess.run(Paths.get("/usr/bin/python3.12"), root, 240000, 1024 * 1024,
                script.toString(), output.resolve("effects").toString(), Paths.get(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                T12ActionSafetyProbe.class.getName(), root.toString(), probe.toString(), execution.name());
        EvidenceFiles.write(output.resolve("observer-command.txt"), invocation.combinedOutput());
        PinProperties effects = PinProperties.load(output.resolve("effects/observation.properties"), "T12 Linux effect observer");
        if (invocation.exitCode != 0 || !"pass".equals(effects.required("result"))
                || !"0".equals(effects.required("child-exit")) || !EvidenceFiles.sha256(script).equals(effects.required("observer-sha256"))) {
            Path log = output.resolve("effects/child.log");
            String diagnostic = Files.isRegularFile(log) && Files.size(log) <= 65536
                    ? new String(Files.readAllBytes(log), StandardCharsets.UTF_8) : "Missing or oversized child log";
            throw new IOException("T12 Action effects or child probe did not pass; retained " + output + ": " + diagnostic);
        }
        for (String effect : new String[] {"read", "write", "script", "process", "network"}) {
            if (!"pass".equals(effects.required("qualification." + effect)) || !"0".equals(effects.required("effects." + effect))) {
                throw new IOException("T12 Action observer qualification/effect failed: " + effect);
            }
        }
        Properties result = new Properties();
        try (InputStream input = Files.newInputStream(probe.resolve("result.properties"))) { result.load(input); }
        if (!"pass".equals(result.getProperty("result")) || !execution.name().equals(result.getProperty("native-execution-profile"))
                || !WorkflowExecutionProfile.IN_PROCESS.name().equals(result.getProperty("facade-execution-profile"))) {
            throw new IOException("T12 public safety identity or result mismatch");
        }
        result.setProperty("effect-observer-sha256", EvidenceFiles.sha256(script));
        result.setProperty("effect-observation-sha256", EvidenceFiles.sha256(output.resolve("effects/observation.json")));
        T12AnnotationProducts.save(output.resolve("result.properties"), result);
        return result;
    }
}

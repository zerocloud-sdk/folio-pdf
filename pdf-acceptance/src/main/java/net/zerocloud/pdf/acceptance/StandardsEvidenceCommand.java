package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Stream;

/** Repository-only independent standards recording; never a product validator. */
public final class StandardsEvidenceCommand {
    private StandardsEvidenceCommand() {
    }

    /**
     * Records a standards observation against one exact, unmodified PDF.
     *
     * @param arguments fresh output directory, tool pin, rule profile, input PDF
     * @throws Exception if input or evidence storage is unavailable
     */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: StandardsEvidenceCommand <output> <tool-pin> <profile> <pdf>");
        }
        Path output = Paths.get(arguments[0]).toAbsolutePath();
        Path pinPath = Paths.get(arguments[1]).toAbsolutePath();
        Path profilePath = Paths.get(arguments[2]).toAbsolutePath();
        Path input = Paths.get(arguments[3]).toAbsolutePath();
        String inputHash = EvidenceFiles.sha256(input);
        String pinHash = EvidenceFiles.sha256(pinPath);
        String profileHash = EvidenceFiles.sha256(profilePath);
        PinProperties pin = PinProperties.load(pinPath, "standards tool");
        PinProperties profile = PinProperties.load(profilePath, "standards profile");
        Files.createDirectory(output);
        EvidenceResult result = EvidenceResult.INDETERMINATE;
        StringBuilder findings = new StringBuilder();
        String executableHash = "unavailable";
        String coveredRules = "";
        String modelHash = "none";
        String patchHash = "none";
        try {
            Path executable = pin.requiredExecutable("executable");
            executableHash = EvidenceFiles.sha256(executable);
            if (pin.optional("patch") != null || pin.optional("patch-sha256") != null) {
                patchHash = EvidenceFiles.sha256(pin.requiredExecutable("patch"));
                if (!patchHash.equals(pin.requiredSha256("patch-sha256"))) {
                    findings.append("Checker source patch identity mismatch.\n");
                    throw new IOException("Standards source patch identity mismatch");
                }
            }
            boolean arlington = "arlington".equals(pin.required("tool"));
            if (arlington) {
                modelHash = modelHash(pin.requiredExecutable("model"));
                if (!modelHash.equals(pin.requiredSha256("model-sha256"))) {
                    throw new IOException("Standards model identity mismatch");
                }
            }
            if (!executableHash.equals(pin.requiredSha256("sha256"))) {
                findings.append("The checker identity does not match its pin.\n");
            } else if (!arlington && !"pdfcpu".equals(pin.required("tool"))) {
                findings.append("The checker has no qualified adapter.\n");
            } else {
                ProcessResult version = ExternalProcess.run(
                        executable, output, Long.parseLong(pin.required("timeout-ms")),
                        Integer.parseInt(pin.required("max-output-bytes")),
                        arlington ? new String[] {"--help", "--no-color"}
                                : new String[] {"version", "--conf", "disable", "--offline"});
                findings.append("Version exit: ").append(version.exitCode).append('\n')
                        .append(version.combinedOutput());
                String expectedVersion = arlington
                        ? "Arlington PDF Model C++ P.o.C. version v" + pin.required("version") + " "
                        : "version: " + pin.required("version") + "\n";
                if (version.exitCode != 0 || !version.standardOutput.contains(expectedVersion)) {
                    findings.append("The checker version does not match its pin.\n");
                } else {
                    ProcessResult check = check(pin, profile, output, input, arlington);
                    findings.append("Validation exit: ").append(check.exitCode).append('\n')
                            .append(check.combinedOutput());
                    if (invalid(check, arlington)) {
                        result = EvidenceResult.FAIL;
                    } else if (valid(check, arlington)) {
                        Set<String> required = rules(profile.required("required-rules"));
                        Set<String> covered = rules(profile.required("covered-rules"));
                        if (!covered.containsAll(required)) {
                            findings.append("Required rule coverage is incomplete.\n");
                        } else {
                            boolean qualified = true;
                            for (String rule : required) {
                                String prefix = "negative." + rule;
                                Path control = profile.requiredExecutable(prefix + ".path");
                                String controlHash = EvidenceFiles.sha256(control);
                                if (!controlHash.equals(profile.requiredSha256(prefix + ".sha256"))) {
                                    findings.append("Negative control identity mismatch: ").append(rule).append('\n');
                                    qualified = false;
                                    continue;
                                }
                                Path retained = output.resolve("control-" + rule + ".pdf");
                                Files.copy(control, retained);
                                if (!controlHash.equals(EvidenceFiles.sha256(retained))) {
                                    throw new IOException("Negative control changed while staging");
                                }
                                ProcessResult negative = check(pin, profile, output, retained, arlington);
                                EvidenceFiles.write(output.resolve("negative-" + rule + ".txt"),
                                        "exit=" + negative.exitCode + "\n" + negative.combinedOutput());
                                boolean detected = invalid(negative, arlington) && negative.combinedOutput().contains(
                                        profile.required(prefix + ".finding"));
                                findings.append("Negative control ").append(rule).append(" sha256=")
                                        .append(controlHash).append(" detected=").append(detected).append('\n');
                                qualified &= detected;
                            }
                            if (qualified) {
                                result = EvidenceResult.PASS;
                                coveredRules = String.join(",", required);
                            }
                        }
                    } else {
                        findings.append("Rule coverage is unavailable.\n");
                    }
                }
            }
            if (!inputHash.equals(EvidenceFiles.sha256(input))) {
                result = EvidenceResult.INDETERMINATE;
                coveredRules = "";
                findings.append("Input changed during validation.\n");
            }
            if (!executableHash.equals(EvidenceFiles.sha256(executable))
                    || !pinHash.equals(EvidenceFiles.sha256(pinPath))
                    || !profileHash.equals(EvidenceFiles.sha256(profilePath))
                    || (!"none".equals(patchHash) && !patchHash.equals(EvidenceFiles.sha256(pin.requiredExecutable("patch"))))
                    || (arlington && !modelHash.equals(modelHash(pin.requiredExecutable("model"))))) {
                result = EvidenceResult.INDETERMINATE;
                coveredRules = "";
                findings.append("Checker or configuration identity changed during validation.\n");
            }
        } catch (ExternalProcess.LimitExceededException limit) {
            result = EvidenceResult.INDETERMINATE;
            coveredRules = "";
            findings.append(limit.retainedOutput()).append('\n');
            findings.append(limit.getMessage()).append('\n');
        } catch (IOException unavailable) {
            result = EvidenceResult.INDETERMINATE;
            coveredRules = "";
            findings.append("The pinned checker is unavailable.\n");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = EvidenceResult.INDETERMINATE;
            coveredRules = "";
            findings.append("The checker was interrupted.\n");
        }
        EvidenceFiles.write(output.resolve("findings.txt"), findings.toString());
        EvidenceFiles.write(output.resolve("standards.properties"),
                "result=" + result.recordValue() + "\n"
                        + "profile=" + profile.required("profile") + "\n"
                        + "covered-rules=" + coveredRules + "\n"
                        + "executable-sha256=" + executableHash + "\n"
                        + "model-sha256=" + modelHash + "\n"
                        + "patch-sha256=" + patchHash + "\n"
                        + "input-sha256=" + inputHash + "\n"
                        + "pin-sha256=" + pinHash + "\n"
                        + "profile-sha256=" + profileHash + "\n");
    }

    private static Set<String> rules(String value) throws IOException {
        Set<String> result = new LinkedHashSet<String>(Arrays.asList(value.split(",", -1)));
        if (result.size() > 128) {
            throw new IOException("Too many standards rules");
        }
        for (String rule : result) {
            if (!rule.matches("[a-z0-9][a-z0-9.-]{0,95}")) {
                throw new IOException("Invalid standards rule identifier");
            }
        }
        return result;
    }

    private static boolean invalid(ProcessResult result, boolean arlington) {
        if (arlington) {
            return result.exitCode == 0 && result.combinedOutput().matches("(?s).*\\nError: .*" );
        }
        return result.exitCode == 1
                && (result.standardError.contains("validation error:")
                    || result.standardError.contains("validation error (obj#:"));
    }

    private static boolean valid(ProcessResult result, boolean arlington) {
        if (result.exitCode != 0) {
            return false;
        }
        if (arlington) {
            return result.standardError.trim().isEmpty()
                    && result.standardOutput.contains(" BEGIN - TestGrammar ")
                    && result.standardOutput.contains("\nEND\n")
                    && result.standardOutput.endsWith("DONE - 1 files processed\n")
                    && !result.standardOutput.contains("Error:")
                    && !result.standardOutput.contains("Warning:");
        }
        return result.combinedOutput().trim().matches(
                "validating\\(mode=strict\\) [^\\r\\n]+ \\.\\.\\.\\r?\\nvalidation ok");
    }

    private static ProcessResult check(PinProperties pin, PinProperties profile,
            Path output, Path input, boolean arlington) throws IOException, InterruptedException {
        String[] arguments = arlington
                ? new String[] {"--tsvdir", pin.requiredExecutable("model").toString(),
                    "--pdf", input.toString(), "--force", profile.required("pdf-version"), "--no-color"}
                : new String[] {"validate", "--mode", "strict", "--conf", "disable", "--offline",
                    input.toString()};
        return ExternalProcess.run(pin.requiredExecutable("executable"), output,
                Long.parseLong(pin.required("timeout-ms")),
                Integer.parseInt(pin.required("max-output-bytes")), arguments);
    }

    private static String modelHash(Path model) throws IOException {
        List<Path> files = new ArrayList<Path>();
        try (Stream<Path> entries = Files.list(model)) {
            entries.filter(path -> path.toString().endsWith(".tsv")).forEach(files::add);
        }
        if (files.isEmpty() || files.size() > 2048) {
            throw new IOException("Invalid standards model size");
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        StringBuilder identity = new StringBuilder();
        for (Path file : files) {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) {
                throw new IOException("Invalid standards model file");
            }
            identity.append(file.getFileName()).append(' ')
                    .append(EvidenceFiles.sha256(file)).append('\n');
        }
        return EvidenceFiles.sha256(identity.toString());
    }
}

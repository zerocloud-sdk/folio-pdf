package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Qualifies the independent program predicates using every original negative control. */
final class T13ProgramStandards {
    private static final String PROFILE_SHA256 = "0ea2838ccc0ce9b7920f822db6459fed8beba33a6709bbcc04c0be5e6715f9ce";
    private static final String OBSERVER_SHA256 = "653633a52a8ff973914af14495fc2a9fd9dc989ccec9da94064fcd8a2e430766";
    private static final String PYTHON_SHA256 = "1643dacd9feaedc58f3cc581e4d22577dfe25c09b10282936186ccf0f2e61118";
    private static final String FONTTOOLS_SHA256 = "8bd0f759020e87bb5d323e6283914d9bf4ae35a7307dafb2cbd1e379e720ad37";
    private static final Path PYTHON = Paths.get("/usr/bin/python3.12");

    private T13ProgramStandards() { }

    static Properties record(Path root, Path input, Path output) throws Exception {
        String inputHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        RetainedEvidence retained = new RetainedEvidence(output);
        retained.retain(observed, inputHash);
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("standards-scope", "programs-only");
        result.setProperty("input-sha256", inputHash);
        result.setProperty("qualification-profile-sha256", PROFILE_SHA256);
        result.setProperty("observer-sha256", OBSERVER_SHA256);
        String verdict = "indeterminate";
        Set<String> qualified = new TreeSet<String>();
        int controls = 0;
        StringBuilder findings = new StringBuilder("Input exact SHA-256: " + inputHash + "\n");
        try {
            Path profilePath = root.resolve("capabilities/profiles/T13-standards/program-qualification.properties");
            if (!PROFILE_SHA256.equals(EvidenceFiles.sha256(profilePath))) {
                throw new IOException("T13 program qualification profile identity mismatch");
            }
            PinProperties profile = PinProperties.load(profilePath, "T13 program qualification");
            if (!T13Corpus.PROFILE.equals(profile.required("profile"))) { throw new IOException("T13 program profile mismatch"); }
            Set<String> scopes = identifiers(profile.required("scopes"), 8);
            Set<String> required = identifiers(profile.required("required-rules"), 42);
            Set<String> negatives = identifiers(profile.required("controls"), 165);
            Properties before = identities(root, output, profile, negatives);
            retained.write(output.resolve("identities-before.properties"), before);
            result.putAll(before);
            Files.createDirectory(output.resolve("input"));
            Files.createDirectory(output.resolve("qualifications"));
            verdict = "pass";
            for (String scope : scopes) {
                Properties observation = observe(root, observed, inputHash, scope, output,
                        output.resolve("input/" + scope), retained);
                verdict = EvidenceResult.combine(verdict, required(observation, "standards"));
                findings.append(scope).append(": ").append(required(observation, "standards"))
                        .append(" ").append(required(observation, "finding")).append('\n');
            }
            for (String control : negatives) {
                String prefix = "negative." + control;
                String scope = profile.required(prefix + ".scope");
                String rule = profile.required(prefix + ".rule");
                if (!scopes.contains(scope) || !required.contains(scope + "." + rule)) {
                    throw new IOException("T13 program control has no required rule: " + control);
                }
                Path source = profile.requiredExecutable(prefix + ".path");
                String hash = profile.requiredSha256(prefix + ".sha256");
                if (!hash.equals(EvidenceFiles.sha256(source))) { throw new IOException("T13 program control identity mismatch: " + control); }
                Path copy = output.resolve("control-" + control + ".pdf");
                Files.copy(source, copy);
                retained.retain(copy, hash);
                if (!hash.equals(EvidenceFiles.sha256(copy))) { throw new IOException("T13 program control changed while staging"); }
                Properties observation = observe(root, copy, hash, scope, output,
                        output.resolve("qualifications/" + control), retained);
                if (!"fail".equals(required(observation, "standards")) || !rule.equals(required(observation, "rule"))) {
                    throw new IOException("T13 program negative control was not detected: " + control);
                }
                qualified.add(scope + "." + rule);
                controls++;
                findings.append("Negative control ").append(control).append(" sha256=").append(hash)
                        .append(" detected=true rule=").append(rule).append('\n');
            }
            if (!qualified.equals(required)) { throw new IOException("The exact T13 program rule union was not qualified"); }
            Properties after = identities(root, output, profile, negatives);
            retained.write(output.resolve("identities-after.properties"), after);
            if (!before.equals(after) || !inputHash.equals(EvidenceFiles.sha256(input))) {
                throw new IOException("T13 program input or authorities changed during observation");
            }
            retained.verify();
        } catch (ExternalProcess.LimitExceededException limit) {
            verdict = "indeterminate";
            qualified.clear();
            findings.append(limit.retainedOutput()).append('\n').append(limit.getMessage()).append('\n');
        } catch (IOException unavailable) {
            verdict = "indeterminate";
            qualified.clear();
            findings.append(unavailable.getMessage()).append('\n');
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            verdict = "indeterminate";
            qualified.clear();
            findings.append("The independent program observer was interrupted.\n");
        }
        result.setProperty("program-standards", verdict);
        result.setProperty("qualified-rule-count", Integer.toString(qualified.size()));
        result.setProperty("negative-control-count", Integer.toString(controls));
        result.setProperty("covered-rules", String.join(",", qualified));
        retained.write(output.resolve("programs.txt"), findings.toString());
        result.setProperty("retained-files-sha256", retained.publishManifest(verdict));
        return result;
    }

    private static Properties observe(Path root, Path input, String hash, String scope, Path workingDirectory,
            Path output, RetainedEvidence retained) throws IOException, InterruptedException {
        ProcessResult process = ExternalProcess.run(PYTHON, workingDirectory, 120000, 1024 * 1024,
                "-I", root.resolve("scripts/t13-program-standards.py").toString(), root.toString(), input.toString(), scope, output.toString());
        if (process.exitCode != 0 || !process.standardError.trim().isEmpty()
                || !Files.isDirectory(output, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            retained.write(workingDirectory.resolve("failed-program-process.txt"),
                    "exit=" + process.exitCode + "\n" + process.combinedOutput());
            throw new IOException("T13 program observer did not complete cleanly: " + scope);
        }
        RetainedEvidence observationFiles = new RetainedEvidence(output);
        observationFiles.write(output.resolve("process.txt"), "exit=" + process.exitCode + "\n" + process.combinedOutput());
        Properties receipt = new Properties();
        for (String line : process.standardOutput.split("\n")) {
            if (!line.matches("[0-9a-f]{64}  (qpdf-version.txt(\\.stderr)?|qpdf.json(\\.stderr)?|result.properties)")) {
                throw new IOException("Malformed T13 program producer receipt");
            }
            String file = line.substring(66);
            if (receipt.setProperty(file, line.substring(0, 64)) != null) { throw new IOException("Duplicate T13 program artifact"); }
            observationFiles.retain(output.resolve(file), line.substring(0, 64));
        }
        Path record = output.resolve("result.properties");
        byte[] bytes = Files.readAllBytes(record);
        if (!required(receipt, "result.properties").equals(EvidenceFiles.sha256(bytes))) {
            throw new IOException("T13 program report disagrees with its producer receipt");
        }
        Properties result = new Properties();
        result.load(new ByteArrayInputStream(bytes));
        if (!T13Corpus.PROFILE.equals(required(result, "profile")) || !scope.equals(required(result, "scope"))
                || !hash.equals(required(result, "input-sha256")) || !OBSERVER_SHA256.equals(required(result, "observer-sha256"))
                || !PYTHON_SHA256.equals(required(result, "python-executable-sha256"))
                || !"3.12.3".equals(required(result, "python-version")) || !"0".equals(required(result, "python-optimization-level"))) {
            throw new IOException("T13 program observation identity mismatch");
        }
        String verdict = required(result, "standards");
        if ("pass".equals(verdict) || "fail".equals(verdict)) {
            if (receipt.size() != 5 || !required(result, "qpdf-json-sha256").equals(required(receipt, "qpdf.json"))) {
                throw new IOException("T13 program producer receipt is incomplete");
            }
        }
        observationFiles.verify();
        retained.include(observationFiles);
        return result;
    }

    private static Properties identities(Path root, Path workingDirectory, PinProperties profile, Set<String> negatives) throws IOException {
        Properties result = T13IndependentEvidence.syntaxToolIdentity(root, workingDirectory);
        String observer = EvidenceFiles.sha256(root.resolve("scripts/t13-program-standards.py"));
        String python = EvidenceFiles.sha256(PYTHON);
        String qualification = EvidenceFiles.sha256(root.resolve("capabilities/profiles/T13-standards/program-qualification.properties"));
        String fonttools = EvidenceFiles.sha256(root.resolve(".build-cache/t75-fonttools-4.59.2/fonttools-4.59.2-py3-none-any.whl"));
        if (!OBSERVER_SHA256.equals(observer) || !PYTHON_SHA256.equals(python) || !PROFILE_SHA256.equals(qualification)
                || !FONTTOOLS_SHA256.equals(fonttools)) { throw new IOException("T13 program checker or profile identity mismatch"); }
        result.setProperty("observer-sha256", observer);
        result.setProperty("python-executable-sha256", python);
        result.setProperty("fonttools-wheel-sha256", fonttools);
        result.setProperty("qualification-profile-sha256", qualification);
        for (String control : negatives) {
            String hash = EvidenceFiles.sha256(profile.requiredExecutable("negative." + control + ".path"));
            if (!profile.requiredSha256("negative." + control + ".sha256").equals(hash)) {
                throw new IOException("T13 program control identity mismatch: " + control);
            }
            result.setProperty("negative." + control + ".sha256", hash);
        }
        return result;
    }

    private static Set<String> identifiers(String value, int count) throws IOException {
        String[] values = value.split(",", -1);
        Set<String> result = new TreeSet<String>(Arrays.asList(values));
        if (result.size() != count || values.length != count) { throw new IOException("T13 program catalog count mismatch"); }
        for (String identifier : result) {
            if (!identifier.matches("[a-z0-9][a-z0-9.-]{0,95}")) { throw new IOException("Invalid T13 program identifier"); }
        }
        return result;
    }

    private static String required(Properties values, String key) throws IOException {
        String value = values.getProperty(key);
        if (value == null || value.trim().isEmpty()) { throw new IOException("Missing T13 program observation property: " + key); }
        return value.trim();
    }
}

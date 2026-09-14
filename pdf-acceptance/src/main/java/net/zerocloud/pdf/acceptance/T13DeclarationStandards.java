package net.zerocloud.pdf.acceptance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Aggregates the qualified declaration rules, separately from program predicates. */
final class T13DeclarationStandards {
    private static final String PDFCPU_PIN_SHA256 = "21c58822196a4123561cac927e1f8ba487e6581ba8f488d5b7f40ba949429fc8";
    private static final String ARLINGTON_PIN_SHA256 = "e00f7b42bd5712f45b346bba68061fc89ac64888bc35c6b4a32f043784900c8b";
    private static final String REQUIRED_RULES_SHA256 = "98e35f2d6c7201bd0fe1c86452ec5f9497f19e4ca58b9d19b7b8b1ca3bece8bd";
    private static final String[] GROUPS = {"pdfcpu", "arlington-core", "arlington-fonts", "arlington-text",
        "arlington-cid", "arlington-descriptors", "arlington-cmaps"};
    private static final String[] PROFILE_HASHES = {
        "dcf92c9e8ab6a48fea9d581c99cfed86dc993d74e3a8de5cb6e9ae1da919f707",
        "178df803628eb17a0e6a87e9c9faac441373edbc60db115cba8e71dbb0d6d586",
        "7c4889c2f294cc4094bacc9dfd8af59642e183b3153de6c1b31f30429f253852",
        "41e5d9fd856574038ac59ce7e26c4a96f6a28ab78599b7d5f6c9afca723555bf",
        "d358219f7da9c26bb85e0a211c88c6f7ae9e00eac2423eda763f6321418181dd",
        "92e09969447f8384d0a0c9b693f9fc8f17261e02582b93b6bccd361503224a95",
        "3382bf1a8b36dd513873a17b7d9c460384c1a9e34250af3b8465245a19b5a369"
    };

    private T13DeclarationStandards() { }

    static Properties record(Path root, Path input, Path output) throws Exception {
        String inputHash = EvidenceFiles.sha256(input);
        Path observed = output.resolve("extraction.pdf");
        if (!input.toAbsolutePath().normalize().equals(observed.toAbsolutePath().normalize())) {
            Files.copy(input, observed, StandardCopyOption.COPY_ATTRIBUTES);
        }
        Properties result = new Properties();
        result.setProperty("profile", T13Corpus.PROFILE);
        result.setProperty("standards-scope", "declarations-only");
        result.setProperty("input-sha256", inputHash);
        String verdict = "indeterminate";
        Set<String> covered = new TreeSet<String>();
        StringBuilder findings = new StringBuilder("Input exact SHA-256: " + inputHash + "\n");
        RetainedEvidence retained = new RetainedEvidence(output);
        retained.retain(observed, inputHash);
        try {
            Set<String> required = requiredRules(root);
            Properties before = identities(root);
            retained.write(output.resolve("identities-before.properties"), before);
            result.setProperty("identities-before-sha256", EvidenceFiles.sha256(output.resolve("identities-before.properties")));
            verdict = "pass";
            for (int index = 0; index < GROUPS.length; index++) {
                String group = GROUPS[index];
                Path profile = root.resolve("capabilities/profiles/T13-standards/" + group + ".properties");
                if (!PROFILE_HASHES[index].equals(EvidenceFiles.sha256(profile))) {
                    throw new IOException("T13 " + group + " declaration profile identity mismatch");
                }
                Path pin = root.resolve("scripts/" + (group.startsWith("arlington") ? "t13-arlington" : group) + "-pin.properties");
                String pinHash = group.startsWith("arlington") ? ARLINGTON_PIN_SHA256 : PDFCPU_PIN_SHA256;
                if (!pinHash.equals(EvidenceFiles.sha256(pin))) {
                    throw new IOException("T13 " + group + " whole tool pin identity mismatch");
                }
                Path directory = output.resolve(group);
                retained.include(StandardsEvidenceCommand.observe(
                        new String[] {directory.toString(), pin.toString(), profile.toString(), observed.toString()}));
                PinProperties record = PinProperties.load(directory.resolve("standards.properties"), "T13 " + group);
                String raw = record.required("result");
                verdict = EvidenceResult.combine(verdict, raw);
                if (!T13Corpus.PROFILE.equals(record.required("profile")) || !inputHash.equals(record.required("input-sha256"))
                        || !pinHash.equals(record.required("pin-sha256"))
                        || !PROFILE_HASHES[index].equals(record.required("profile-sha256"))) {
                    throw new IOException("T13 declaration observation identity mismatch");
                }
                if ("pass".equals(raw)) {
                    for (String rule : record.required("covered-rules").split(",")) {
                        if (!covered.add(rule)) { throw new IOException("Duplicate T13 declaration rule: " + rule); }
                    }
                }
                findings.append(group).append('\n')
                        .append(new String(Files.readAllBytes(directory.resolve("standards.properties")), StandardCharsets.UTF_8))
                        .append(new String(Files.readAllBytes(directory.resolve("findings.txt")), StandardCharsets.UTF_8));
            }
            if (!covered.equals(required)) {
                findings.append("The exact required declaration rule union was not qualified.\n");
                if (!"fail".equals(verdict)) { verdict = "indeterminate"; }
            }
            if (!inputHash.equals(EvidenceFiles.sha256(input)) || !inputHash.equals(EvidenceFiles.sha256(observed))) {
                throw new IOException("T13 declaration input changed during observation");
            }
            Properties after = identities(root);
            retained.write(output.resolve("identities-after.properties"), after);
            result.setProperty("identities-after-sha256", EvidenceFiles.sha256(output.resolve("identities-after.properties")));
            if (!before.equals(after)) { throw new IOException("T13 declaration authorities changed during observation"); }
            retained.verify();
        } catch (IOException unavailable) {
            verdict = "indeterminate";
            covered.clear();
            findings.append(unavailable.getMessage()).append('\n');
        }
        result.setProperty("declarations", verdict);
        result.setProperty("qualified-rule-count", Integer.toString(covered.size()));
        result.setProperty("covered-rules", String.join(",", covered));
        result.setProperty("required-rules-sha256", REQUIRED_RULES_SHA256);
        retained.write(output.resolve("declarations.txt"), findings.toString());
        result.setProperty("retained-files-sha256", retained.publishManifest(verdict));
        return result;
    }

    private static Properties identities(Path root) throws IOException {
        Properties identities = new Properties();
        identities.setProperty("required-rules-sha256", EvidenceFiles.sha256(
                root.resolve("capabilities/profiles/T13-standards/required-rules.txt")));
        for (String group : GROUPS) {
            identities.setProperty("profile." + group + ".sha256", EvidenceFiles.sha256(
                    root.resolve("capabilities/profiles/T13-standards/" + group + ".properties")));
        }
        for (String tool : new String[] {"pdfcpu", "t13-arlington"}) {
            Path path = root.resolve("scripts/" + tool + "-pin.properties");
            identities.setProperty(tool + ".pin-sha256", EvidenceFiles.sha256(path));
            PinProperties pin = PinProperties.load(path, "T13 " + tool);
            identities.setProperty(tool + ".executable-sha256", EvidenceFiles.sha256(pin.requiredExecutable("executable")));
            if ("t13-arlington".equals(tool)) {
                identities.setProperty(tool + ".model-sha256", StandardsEvidenceCommand.modelHash(pin.requiredExecutable("model")));
                identities.setProperty(tool + ".patch-sha256", EvidenceFiles.sha256(pin.requiredExecutable("patch")));
            }
        }
        return identities;
    }

    private static Set<String> requiredRules(Path root) throws IOException {
        Path authority = root.resolve("capabilities/profiles/T13-standards/required-rules.txt");
        if (!REQUIRED_RULES_SHA256.equals(EvidenceFiles.sha256(authority))) {
            throw new IOException("T13 declaration rule catalog identity mismatch");
        }
        Set<String> rules = new TreeSet<String>();
        for (String rule : Files.readAllLines(authority, StandardCharsets.US_ASCII)) {
            if (!rule.matches("[a-z0-9-]+") || !rules.add(rule)) { throw new IOException("Invalid T13 declaration rule catalog"); }
        }
        if (rules.size() != 334) { throw new IOException("T13 requires exactly 334 declaration rules"); }
        return rules;
    }
}

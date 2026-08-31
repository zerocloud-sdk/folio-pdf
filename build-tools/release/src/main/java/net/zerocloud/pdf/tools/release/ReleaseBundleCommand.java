package net.zerocloud.pdf.tools.release;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class ReleaseBundleCommand {

    private ReleaseBundleCommand() {
    }

    public static void main(String[] arguments) {
        if (arguments.length < 3) {
            throw new IllegalArgumentException(
                    "Usage: ReleaseBundleCommand <validate|compare> DIRECTORY REPOSITORY_ROOT; "
                    + "ReleaseBundleCommand bundle BUILD_TREE OUTPUT_ZIP REPOSITORY_ROOT "
                    + "RELEASE_VERSION");
        }
        if (!("validate".equals(arguments[0]) || "compare".equals(arguments[0])
                || "bundle".equals(arguments[0]))) {
            throw new IllegalArgumentException(
                    "Usage: ReleaseBundleCommand <validate|compare> DIRECTORY REPOSITORY_ROOT; "
                    + "ReleaseBundleCommand bundle BUILD_TREE OUTPUT_ZIP REPOSITORY_ROOT "
                    + "RELEASE_VERSION");
        }
        if ("bundle".equals(arguments[0])) {
            if (arguments.length != 5) {
                throw new IllegalArgumentException(
                        "Usage: ReleaseBundleCommand bundle BUILD_TREE OUTPUT_ZIP "
                        + "REPOSITORY_ROOT RELEASE_VERSION");
            }
            Path buildTree = Paths.get(arguments[1]).toAbsolutePath().normalize();
            Path bundle = Paths.get(arguments[2]).toAbsolutePath().normalize();
            Path repositoryRoot = Paths.get(arguments[3]).toAbsolutePath().normalize();
            ReleaseBundleAssembler.Assembly assembly = new ReleaseBundleAssembler()
                    .assemble(buildTree, bundle, repositoryRoot, arguments[4]);
            if (!assembly.isValid()) {
                for (String error : assembly.errors) {
                    System.err.println("ERROR: " + error);
                }
                fail("release bundle assembly failed with "
                        + assembly.errors.size() + " error(s)");
            }
            System.out.println("Central bundle assembled: " + bundle
                    + " (entries=" + assembly.entries + ")");
            return;
        }
        Path output = Paths.get(arguments[1]).toAbsolutePath().normalize();
        if ("compare".equals(arguments[0])) {
            ReleaseReproducibility.Comparison comparison =
                    new ReleaseReproducibility().compare(output);
            if (!comparison.isReproducible()) {
                for (String error : comparison.errors) {
                    System.err.println("ERROR: " + error);
                }
                fail("release artifacts are not reproducible");
            }
            System.out.println("Release artifact reproducibility passed: "
                    + comparison.comparedEntries + " entries assessed");
            return;
        }
        int repositoryRootIndex = arguments.length == 5 ? 3 : 2;
        Path repositoryRoot = Paths.get(arguments[repositoryRootIndex]).toAbsolutePath()
                .normalize();
        ReleaseBundleValidator.Validation validation =
                new ReleaseBundleValidator().validate(output, repositoryRoot);
        if (!validation.isValid()) {
            for (String error : validation.errors) {
                System.err.println("ERROR: " + error);
            }
            fail("release rehearsal validation failed with "
                    + validation.errors.size() + " error(s)");
        }
        System.out.println("Release rehearsal bundle validation passed: " + output
                + " (verified-signatures=" + validation.verifiedSignatures
                + ", validated-checksums=" + validation.validatedChecksums + ")");
    }

    private static void fail(String message) {
        System.err.println("ERROR: " + message);
        throw new ReleaseBundleFailure(message);
    }

    private static final class ReleaseBundleFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private ReleaseBundleFailure(String message) {
            super(message);
        }
    }
}

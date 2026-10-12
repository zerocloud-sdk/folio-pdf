package net.zerocloud.pdf.acceptance;

/** Runs recovery, protocol, scale, public Facade and artifact cases without skips. */
public final class T22ContractTestCommand {
    private T22ContractTestCommand() { }

    public static void main(String[] arguments) throws Exception {
        if (!"all".equals(System.getProperty("folio.pdf.t22.scale"))) {
            throw new IllegalArgumentException("T22 certification requires every actual scale profile");
        }
        java.nio.file.Files.createDirectory(java.nio.file.Paths.get(System.getProperty("folio.pdf.t22.output")));
        RequiredCaseRunner.main("T22", "T22-recovery-scale-boundary", arguments);
    }
}

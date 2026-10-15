package net.zerocloud.pdf.acceptance;

/** Executes the frozen Provider inventory with no missing, duplicate or skipped case. */
public final class T05ContractTestCommand {
    private T05ContractTestCommand() { }
    public static void main(String[] arguments) throws Exception {
        RequiredCaseRunner.main("T05", "T05-capability-provider-contract", arguments);
    }
}

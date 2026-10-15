package net.zerocloud.pdf.acceptance;

import java.nio.file.Files;
import java.nio.file.Paths;

/** Runs existing public Facade operations in a JVM containing only the Preview edition. */
public final class T05PreviewFacadeCommand {
    private T05PreviewFacadeCommand() { }
    public static void main(String[] arguments) throws Exception {
        T20FacadeContracts.record(Files.createDirectory(Paths.get(arguments[0]))).verify();
        System.out.println("T05 Preview Facade public outcomes passed");
    }
}

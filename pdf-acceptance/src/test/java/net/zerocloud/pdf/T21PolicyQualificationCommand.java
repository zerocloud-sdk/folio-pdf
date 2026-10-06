package net.zerocloud.pdf;

import java.io.Closeable;
import java.lang.reflect.InvocationTargetException;
import java.net.NetPermission;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** Acceptance-only policy qualification JVM, distinct from the closed production Worker. */
@SuppressWarnings("removal")
public final class T21PolicyQualificationCommand {
    private T21PolicyQualificationCommand() { }

    public static void main(String[] arguments) throws Exception {
        Path root = Paths.get(arguments[0]);
        Properties observed = new Properties();
        Path base = root.resolve("base"), hard = root.resolve("hard"), symbolic = root.resolve("symbolic");
        Files.write(base, new byte[] {1});
        Files.createLink(hard, base);
        Files.createSymbolicLink(symbolic, base);
        check(Files.readAllBytes(hard)[0] == 1 && Files.readAllBytes(symbolic)[0] == 1, "permitted link controls unavailable");
        Files.delete(hard); Files.delete(symbolic);
        observed.setProperty("permitted-hard-link", "pass");
        observed.setProperty("permitted-symbolic-link", "pass");
        // Linux AF_UNIX paths have a fixed length ceiling; retained output paths can be much longer.
        Path unixSocket = Paths.get(System.getProperty("java.io.tmpdir"), "t21-socket-" + java.util.UUID.randomUUID());
        boolean unix = unixOperation(unixSocket, false);
        observed.setProperty("unix-java-api", unix ? "available" : "unavailable");
        if (unix) { Files.delete(unixSocket); }
        System.setSecurityManager(new HardenedWorkerSecurityManager(root));
        observed.setProperty("installed-manager", System.getSecurityManager().getClass().getName());
        try { Files.createLink(hard, base); throw new AssertionError("Hard link was permitted"); }
        catch (SecurityException expected) { observed.setProperty("denied-hard-link", "pass"); }
        try { Files.createSymbolicLink(symbolic, base); throw new AssertionError("Symbolic link was permitted"); }
        catch (SecurityException expected) { observed.setProperty("denied-symbolic-link", "pass"); }
        try { System.getSecurityManager().checkPermission(new NetPermission("accessUnixDomainSocket"));
            throw new AssertionError("Unix permission was permitted"); }
        catch (SecurityException expected) { observed.setProperty("denied-unix-permission", "pass"); }
        if (unix) {
            check(unixOperation(unixSocket, true), "actual Unix listen was not denied by the policy");
            observed.setProperty("denied-unix-listen", "pass");
        } else { observed.setProperty("denied-unix-listen", "api-unavailable"); }
        try (java.io.OutputStream output = Files.newOutputStream(root.resolve("policy.properties"))) {
            observed.store(output, "Policy qualification; this JVM is not the production Worker");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean unixOperation(Path path, boolean expectDenial) throws Exception {
        Closeable channel = null;
        try {
            Class<?> addressType = Class.forName("java.net.UnixDomainSocketAddress");
            Object address = addressType.getMethod("of", Path.class).invoke(null, path);
            Class<?> familyType = Class.forName("java.net.ProtocolFamily");
            Class<? extends Enum> family = (Class<? extends Enum>) Class.forName("java.net.StandardProtocolFamily");
            Object unix = Enum.valueOf(family, "UNIX");
            Class<?> channelType = Class.forName("java.nio.channels.ServerSocketChannel");
            channel = (Closeable) channelType.getMethod("open", familyType).invoke(null, unix);
            channelType.getMethod("bind", java.net.SocketAddress.class).invoke(channel, address);
            check(!expectDenial, "Unix operation unexpectedly permitted");
            return true;
        } catch (ClassNotFoundException unavailable) {
            check(!expectDenial, "Previously available Unix API disappeared");
            return false;
        } catch (InvocationTargetException failure) {
            if (expectDenial && failure.getCause() instanceof SecurityException) { return true; }
            throw failure;
        } finally { if (channel != null) { channel.close(); } }
    }

    private static void check(boolean value, String message) { if (!value) { throw new AssertionError(message); } }
}

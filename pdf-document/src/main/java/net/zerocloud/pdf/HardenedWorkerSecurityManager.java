package net.zerocloud.pdf;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.net.NetPermission;
import java.net.SocketPermission;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.lang.reflect.ReflectPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Process-local deny-by-default I/O boundary for the dedicated Worker JVM. */
@SuppressWarnings("removal")
final class HardenedWorkerSecurityManager extends SecurityManager {

    private final Path transactionRoot;
    private final Path transactionParent;
    private final List<Path> readableRuntimePaths;

    HardenedWorkerSecurityManager(Path transactionRoot) {
        this.transactionRoot = transactionRoot.toAbsolutePath().normalize();
        this.transactionParent = this.transactionRoot.getParent();
        this.readableRuntimePaths = runtimePaths();
    }

    @Override
    public void checkPermission(Permission permission) {
        if (permission instanceof SocketPermission) {
            throw denied("Network access is disabled in the Hardened Worker.");
        }
        if (permission instanceof NetPermission
                && "accessUnixDomainSocket".equals(permission.getName())) {
            throw denied(
                    "Unix-domain sockets are disabled in the Hardened Worker.");
        }
        if (permission instanceof java.nio.file.LinkPermission) {
            throw denied("Filesystem links are disabled in the Hardened Worker.");
        }
        if (permission instanceof ReflectPermission
                && "suppressAccessChecks".equals(permission.getName())
                && !isBootstrapReflection()) {
            throw denied(
                    "Deep reflection is disabled in the Hardened Worker.");
        }
        if (permission instanceof FilePermission) {
            checkFilePermission((FilePermission) permission);
            return;
        }
        if (permission instanceof RuntimePermission) {
            String name = permission.getName();
            if ("setSecurityManager".equals(name)
                    || name.startsWith("exitVM")
                    || (name.startsWith("loadLibrary.")
                            && !isBootstrapNativeLoad())) {
                throw denied(
                        "Worker isolation controls cannot be changed.");
            }
        }
    }

    @Override
    public void checkPermission(Permission permission, Object context) {
        checkPermission(permission);
    }

    @Override
    public void checkRead(FileDescriptor descriptor) {
        // Protocol input and already-open runtime resources are permitted.
    }

    @Override
    public void checkWrite(FileDescriptor descriptor) {
        // Protocol output and diagnostic stderr are permitted.
    }

    @Override
    public void checkConnect(String host, int port) {
        throw denied("Outbound network access is disabled in the Hardened Worker.");
    }

    @Override
    public void checkConnect(String host, int port, Object context) {
        checkConnect(host, port);
    }

    @Override
    public void checkListen(int port) {
        throw denied("Listening sockets are disabled in the Hardened Worker.");
    }

    @Override
    public void checkAccept(String host, int port) {
        throw denied("Accepted sockets are disabled in the Hardened Worker.");
    }

    @Override
    public void checkMulticast(java.net.InetAddress address) {
        throw denied("Multicast is disabled in the Hardened Worker.");
    }

    @Override
    public void checkExec(String command) {
        throw denied("Descendant processes are disabled in the Hardened Worker.");
    }

    @Override
    public void checkDelete(String file) {
        requireInTransaction(file, "delete");
    }

    @Override
    public void checkWrite(String file) {
        requireInTransaction(file, "write");
    }

    @Override
    public void checkRead(String file) {
        Path path = normalized(file);
        if (isWithin(path, transactionRoot)) {
            return;
        }
        if (transactionParent != null && isWithin(path, transactionParent)) {
            throw denied(
                    "Other transaction files are not readable by this Worker.");
        }
        for (Path runtimePath : readableRuntimePaths) {
            if (isWithin(path, runtimePath)) {
                return;
            }
        }
        throw denied("Host filesystem reads are disabled in the Hardened Worker.");
    }

    private void checkFilePermission(FilePermission permission) {
        String actions = permission.getActions();
        if (containsAction(actions, "execute")) {
            throw denied("Descendant processes are disabled in the Hardened Worker.");
        }
        if (containsAction(actions, "write")
                || containsAction(actions, "delete")) {
            requireInTransaction(permission.getName(), "write");
        }
        if (containsAction(actions, "read")
                || containsAction(actions, "readlink")) {
            checkRead(permission.getName());
        }
    }

    private void requireInTransaction(String file, String action) {
        if (!isWithin(normalized(file), transactionRoot)) {
            throw denied("Host filesystem " + action
                    + " access is disabled in the Hardened Worker.");
        }
    }

    private static boolean containsAction(String actions, String action) {
        for (String value : actions.split(",")) {
            if (action.equals(value.trim())) {
                return true;
            }
        }
        return false;
    }

    private static Path normalized(String file) {
        if ("<<ALL FILES>>".equals(file)) {
            return Paths.get(File.separator).toAbsolutePath().normalize();
        }
        String plain = file;
        if (plain.endsWith(File.separator + "-")
                || plain.endsWith(File.separator + "*")) {
            plain = plain.substring(0, plain.length() - 2);
        }
        return Paths.get(plain).toAbsolutePath().normalize();
    }

    private static boolean isWithin(Path candidate, Path parent) {
        return candidate.equals(parent) || candidate.startsWith(parent);
    }

    private boolean isBootstrapReflection() {
        return firstApplicationCallerIsBootstrap(
                "java.lang.reflect.",
                "java.security.AccessController");
    }

    private boolean isBootstrapNativeLoad() {
        return firstApplicationCallerIsBootstrap(
                "java.lang.Runtime",
                "java.lang.System",
                "java.security.AccessController",
                "jdk.internal.loader.NativeLibraries");
    }

    private boolean firstApplicationCallerIsBootstrap(String... machinery) {
        for (Class<?> type : getClassContext()) {
            if (type == HardenedWorkerSecurityManager.class
                    || type == SecurityManager.class) {
                continue;
            }
            String name = type.getName();
            boolean infrastructure = false;
            for (String prefix : machinery) {
                if (name.startsWith(prefix)) {
                    infrastructure = true;
                    break;
                }
            }
            if (!infrastructure) {
                return type.getClassLoader() == null;
            }
        }
        return false;
    }

    private static List<Path> runtimePaths() {
        List<Path> paths = new ArrayList<Path>();
        addPath(paths, System.getProperty("java.home"));
        String classPath = System.getProperty("java.class.path", "");
        String[] entries = classPath.split(
                java.util.regex.Pattern.quote(File.pathSeparator));
        for (String entry : entries) {
            if (!entry.isEmpty()) {
                addPath(paths, entry);
            }
        }
        return Collections.unmodifiableList(paths);
    }

    private static void addPath(List<Path> paths, String value) {
        if (value != null && !value.isEmpty()) {
            String plain = value;
            if (plain.endsWith(File.separator + "*")
                    || plain.endsWith(File.separator + "-")) {
                plain = plain.substring(0, plain.length() - 2);
            }
            paths.add(Paths.get(plain).toAbsolutePath().normalize());
        }
    }

    private static SecurityException denied(String message) {
        return new SecurityException(message);
    }
}

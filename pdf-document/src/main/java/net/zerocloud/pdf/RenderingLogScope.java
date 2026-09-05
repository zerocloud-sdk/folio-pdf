package net.zerocloud.pdf;

import java.io.IOException;
import java.util.Enumeration;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Keeps the default JUL backend logs private on the active rendering thread. */
final class RenderingLogScope implements AutoCloseable {
    private static final Object LOCK = new Object();
    private static final ThreadLocal<RenderingLogScope> CURRENT = new ThreadLocal<RenderingLogScope>();
    private static final Map<Handler, ScopedFilter> FILTERS = new IdentityHashMap<Handler, ScopedFilter>();
    private static int scopes;
    private final RenderingLogScope previous;
    private final EnumSet<RenderDiagnostic> diagnostics;
    private boolean warned;
    private boolean closed;

    static RenderingLogScope open(EnumSet<RenderDiagnostic> diagnostics) {
        synchronized (LOCK) {
            RenderingLogScope scope = new RenderingLogScope(CURRENT.get(), diagnostics);
            LogManager manager = LogManager.getLogManager();
            Enumeration<String> names = manager.getLoggerNames();
            while (names.hasMoreElements()) {
                Logger logger = manager.getLogger(names.nextElement());
                if (logger == null) { continue; }
                for (Handler handler : logger.getHandlers()) {
                    if (!FILTERS.containsKey(handler)) {
                        ScopedFilter filter = new ScopedFilter(handler.getFilter());
                        handler.setFilter(filter);
                        FILTERS.put(handler, filter);
                    }
                }
            }
            scopes++;
            CURRENT.set(scope);
            return scope;
        }
    }

    private RenderingLogScope(RenderingLogScope previous, EnumSet<RenderDiagnostic> diagnostics) {
        this.previous = previous; this.diagnostics = diagnostics;
    }

    void requireClean() throws IOException {
        if (warned) { throw new IOException("The renderer reported an incomplete operation."); }
    }

    @Override public void close() {
        if (closed) { return; }
        closed = true;
        synchronized (LOCK) {
            if (previous == null) { CURRENT.remove(); } else { CURRENT.set(previous); }
            if (--scopes == 0) {
                for (Map.Entry<Handler, ScopedFilter> entry : FILTERS.entrySet()) {
                    if (entry.getKey().getFilter() == entry.getValue()) {
                        entry.getKey().setFilter(entry.getValue().previous);
                    }
                }
                FILTERS.clear();
            }
        }
    }

    private static final class ScopedFilter implements Filter {
        private final Filter previous;
        ScopedFilter(Filter previous) { this.previous = previous; }
        @Override public boolean isLoggable(LogRecord record) {
            RenderingLogScope scope = CURRENT.get();
            String name = record.getLoggerName();
            if (scope != null && name != null
                    && (name.startsWith("org.apache.pdfbox.") || name.startsWith("org.apache.fontbox."))) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    if (record.getMessage() != null && record.getMessage().startsWith("Using fallback font ")
                            && record.getLevel().intValue() < Level.SEVERE.intValue()) {
                        scope.diagnostics.add(RenderDiagnostic.FONT_SUBSTITUTED);
                    } else if ("org.apache.pdfbox.io.IOUtils".equals(name)
                            && record.getMessage() != null
                            && record.getMessage().startsWith("Unmapping is not supported")) {
                        // The Worker intentionally denies PDFBox's optional
                        // Unsafe cleaner probe. This does not affect rendering.
                    } else {
                        scope.warned = true;
                    }
                }
                return false;
            }
            return previous == null || previous.isLoggable(record);
        }
    }
}

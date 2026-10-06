package net.zerocloud.pdf.acceptance;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.zerocloud.pdf.CancellationToken;
import net.zerocloud.pdf.DocumentFailure;
import net.zerocloud.pdf.DocumentFailureCode;
import net.zerocloud.pdf.DocumentPatch;
import net.zerocloud.pdf.DocumentSession;
import net.zerocloud.pdf.DocumentSource;
import net.zerocloud.pdf.DocumentWork;
import net.zerocloud.pdf.DocumentWorkflow;
import net.zerocloud.pdf.ObjectReference;
import net.zerocloud.pdf.PageRange;
import net.zerocloud.pdf.PdfDictionary;
import net.zerocloud.pdf.PdfIndirectReference;
import net.zerocloud.pdf.PdfInspectionLimits;
import net.zerocloud.pdf.PdfName;
import net.zerocloud.pdf.PdfNumber;
import net.zerocloud.pdf.PdfStream;
import net.zerocloud.pdf.PublicationReceipt;
import net.zerocloud.pdf.PublicationStatus;
import net.zerocloud.pdf.PublicationTarget;
import net.zerocloud.pdf.SaveMode;
import net.zerocloud.pdf.WorkflowEnvironment;
import net.zerocloud.pdf.WorkflowExecutionProfile;
import net.zerocloud.pdf.WorkflowOutcome;
import net.zerocloud.pdf.WorkflowProgressPhase;
import net.zerocloud.pdf.WorkflowRequest;
import net.zerocloud.pdf.WorkflowResourcePolicy;
import net.zerocloud.pdf.WorkflowResourceUsage;
import net.zerocloud.pdf.command.AddBlankPage;
import net.zerocloud.pdf.command.MergeDocuments;
import net.zerocloud.pdf.command.SplitDocument;
import net.zerocloud.pdf.query.DocumentRootReference;
import net.zerocloud.pdf.query.InspectObject;
import net.zerocloud.pdf.query.PageCount;
import net.zerocloud.pdf.query.XmpMetadata;

/** Fixed, project-authored T20 experiments using only the public Workflow seam. */
final class T20ResourceContracts {
    private static final String CAPABILITY = "document.hostile-input-limits";
    private static final Instant EPOCH = Instant.parse("2026-01-01T00:00:00Z");
    private final Path corpus;
    private final Path output;
    private final RetainedEvidence retained;
    private final Properties observed = new Properties();
    private final Path storage;
    private final Set<Path> callerPaths = new HashSet<Path>();
    private final MutableClock clock = new MutableClock();
    private final WorkflowEnvironment environment;
    private final DocumentWorkflow workflow;
    private String current;

    private T20ResourceContracts(Path root, Path output) throws IOException {
        this.corpus = root.resolve("capabilities/profiles/T20-hostile-input");
        this.output = output;
        this.retained = new RetainedEvidence(output);
        this.storage = Files.createDirectory(output.resolve("owned-storage"));
        this.callerPaths.add(storage);
        this.environment = WorkflowEnvironment.builder().clock(clock).temporaryDirectory(storage).build();
        this.workflow = new DocumentWorkflow(environment);
    }

    static RetainedEvidence record(Path root, Path output) throws Exception {
        T20ResourceContracts contract = new T20ResourceContracts(root, output);
        contract.run();
        contract.retained.write(output.resolve("observations.properties"), contract.observed);
        return contract.retained;
    }

    private void run() throws Exception {
        observe("defaults-declarations", this::declarations);
        for (String kind : Arrays.asList("path", "stream", "channel", "bytes")) {
            observe("input-" + kind + "-exact", () -> sourceBoundary(kind, 354, false));
            observe("input-" + kind + "-excess", () -> sourceBoundary(kind, 353, true));
        }
        observe("input-aggregate-exact", () -> aggregateInput(708, false));
        observe("input-aggregate-excess", () -> aggregateInput(707, true));
        observe("objects-command-observed", () -> addedObject(2000000, false));
        observe("objects-command-excess", () -> addedObject(3, true));
        observe("objects-donor-aggregate", this::donorObjects);
        observe("objects-donor-first-excess", this::donorObjectExcess);
        for (String dimension : Arrays.asList("pages", "objects", "nesting", "decompression", "pixels", "memory", "temporary")) {
            observe(dimension + "-exact", () -> boundary(dimension, false));
            observe(dimension + "-excess", () -> boundary(dimension, true));
            observe(dimension + "-zero", () -> boundaryAt(dimension, 0, true));
        }
        observe("input-zero", () -> sourceBoundary("path", 0, true));
        for (String codec : Arrays.asList("ascii85", "flate", "lzw")) {
            observe(codec + "-exact", () -> codecBoundary(codec, false));
            observe(codec + "-excess", () -> codecBoundary(codec, true));
        }
        observe("returned-memory-exact", () -> returnedMemory(8, 1, false));
        observe("returned-memory-excess", () -> returnedMemory(7, 1, true));
        observe("retained-working-memory-exact", () -> returnedMemory(12, 2, false));
        observe("retained-working-memory-excess", () -> returnedMemory(11, 2, true));
        observe("zero-consumption", this::zeroConsumption);
        observe("request-precedence", this::precedence);
        observe("local-source-precedence", this::localSource);
        observe("local-decoding-precedence", this::localDecode);
        observe("repeated-decode-exact", () -> repeatedDecode(24, false));
        observe("repeated-decode-excess", () -> repeatedDecode(23, true));
        observe("filter-patch-exact", () -> filterPatch(16, false));
        observe("filter-patch-excess", () -> filterPatch(15, true));
        observe("pixels-no-refund", this::pixelsNoRefund);
        observe("elapsed-equality", () -> elapsed(Duration.ofSeconds(1), false));
        observe("elapsed-first-excess", () -> elapsed(Duration.ofSeconds(1).plusNanos(1), true));
        observe("backward-clock", this::backwardClock);
        observe("deadline-equality", this::deadline);
        observe("source-cancellation", this::cancelRead);
        observe("concurrency-incoming", () -> concurrency(2, 1));
        observe("concurrency-active", () -> concurrency(1, 2));
        observe("concurrency-zero", () -> failure(workflow, blank(policy("concurrency", 0)).build(),
                session -> { throw new AssertionError("Rejected admission ran caller work"); }, DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED));
        observe("permit-terminal-paths", this::permits);
        observe("caught-terminal-failure", this::terminal);
        observe("owning-format-failure", this::malformed);
        observe("unsupported-filter", this::unsupported);
        observe("validation-before-publication", this::validationStop);
        observe("publication-quota-preserves-paths", this::publicationQuota);
        observe("publication-high-water", this::publicationHighWater);
        observe("adjacent-cancellation-cleans", this::adjacentCancellation);
        observe("publication-ordered-partial", this::partialPublication);
        observe("split-aggregate-pages", this::splitPages);
        observe("split-aggregate-objects", this::splitObjects);
        observed.setProperty("execution-profile", "IN_PROCESS");
        observed.setProperty("case-count", Integer.toString(observed.stringPropertyNames().stream()
                .filter(name -> name.endsWith(".result")).toArray().length));
    }

    private void observe(String name, Experiment experiment) throws Exception {
        current = name;
        clock.now = EPOCH;
        try { experiment.run(); }
        finally { clock.checkpoint = null; }
        try (java.util.stream.Stream<Path> entries = Files.list(storage)) {
            check(entries.count() == 0, "workflow-owned storage remained after " + name);
        }
        try (java.util.stream.Stream<Path> entries = Files.walk(output)) {
            check(entries.allMatch(path -> path.equals(output) || callerPaths.contains(path)),
                    "workflow or target-adjacent staging remained after " + name);
        }
        observed.setProperty(name + ".cleanup", "pass");
        observed.setProperty(name + ".result", "pass");
    }

    private void declarations() {
        WorkflowResourcePolicy value = WorkflowResourcePolicy.safeDefaults();
        check(value.getMaximumInputBytes() == 1L << 30 && value.getMaximumPages() == 5000
                && value.getMaximumObjects() == 2000000 && value.getMaximumNestingDepth() == 16384
                && value.getMaximumDecompressedBytes() == 4L << 30 && value.getMaximumDecodedPixels() == 1000000000
                && value.getMaximumOwnedMemoryBytes() == 256L << 20 && value.getMaximumTemporaryStorageBytes() == 4L << 30
                && value.getMaximumElapsedTime().equals(Duration.ofMinutes(5)) && value.getMaximumConcurrentWorkflows() == 4,
                "finite defaults changed");
        for (String dimension : Arrays.asList("input", "pages", "objects", "nesting", "decompression", "pixels", "memory", "temporary", "elapsed", "concurrency")) {
            try { policy(dimension, -1); throw new AssertionError("negative declaration accepted: " + dimension); }
            catch (IllegalArgumentException expected) { /* Programming error precedes any workflow. */ }
        }
        try { WorkflowResourcePolicy.builder().build(); throw new AssertionError("incomplete policy accepted"); }
        catch (IllegalStateException expected) { }
        try { policy("nesting", 16385); throw new AssertionError("version-1 ceiling accepted"); }
        catch (IllegalArgumentException expected) { }
        try { builder().maximumElapsedTime(Duration.ofSeconds(Long.MAX_VALUE)).build(); throw new AssertionError("overflow accepted"); }
        catch (IllegalArgumentException expected) { }
        check(policy("nesting", 16384).getMaximumNestingDepth() == 16384, "ceiling equality rejected");
    }

    private void sourceBoundary(String kind, long limit, boolean excess) throws Exception {
        byte[] bytes = Files.readAllBytes(corpus.resolve("plain.pdf"));
        check(bytes.length == 354, "authored operand length changed");
        BorrowedInput input = new BorrowedInput(bytes);
        ReadableByteChannel channel = Channels.newChannel(input);
        DocumentSource source = "path".equals(kind) ? DocumentSource.path(corpus.resolve("plain.pdf"))
                : "stream".equals(kind) ? DocumentSource.stream(input, 354)
                : "channel".equals(kind) ? DocumentSource.channel(channel, 354) : DocumentSource.bytes(bytes, 354);
        WorkflowRequest request = input(source, policy("input", limit)).build();
        if (excess) { failure(workflow, request, T20ResourceContracts::pageCount, DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED); }
        else { WorkflowOutcome<Integer> result = workflow.execute(request, T20ResourceContracts::pageCount);
            check(result.getResult() == 1 && result.getResourceUsage().getAcceptedInputBytes() == 354, "actual-byte boundary"); }
        check(!input.closed && channel.isOpen(), "caller Source closed");
    }

    private void aggregateInput(long limit, boolean excess) throws Exception {
        WorkflowRequest request = input(DocumentSource.path(corpus.resolve("plain.pdf")), policy("input", limit))
                .source("additional", DocumentSource.bytes(Files.readAllBytes(corpus.resolve("plain.pdf")), 354)).build();
        if (excess) { failure(workflow, request, T20ResourceContracts::pageCount, DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, T20ResourceContracts::pageCount).getResourceUsage().getAcceptedInputBytes() == 708, "aggregate input reset"); }
    }

    private void boundary(String dimension, boolean excess) throws Exception {
        long exact = "pages".equals(dimension) ? 1 : "objects".equals(dimension) ? 3 : "nesting".equals(dimension) ? 10
                : "decompression".equals(dimension) ? 8 : "pixels".equals(dimension) ? 6 : 354;
        boundaryAt(dimension, exact - (excess ? 1 : 0), excess);
    }

    private void addedObject(long maximum, boolean excess) throws Exception {
        // The authored primary contains three indirect objects. A new Page needs
        // at least one additional indirect object; parent aliases may also be observed.
        WorkflowRequest request = working(policy("objects", maximum));
        DocumentWork<Integer> work = session -> { session.execute(AddBlankPage.INSTANCE); return pageCount(session); };
        if (excess) { failure(workflow, request, work, DocumentFailureCode.OBJECT_LIMIT_EXCEEDED); }
        else { WorkflowOutcome<Integer> outcome = workflow.execute(request, work);
            check(outcome.getResult() == 2 && outcome.getResourceUsage().getObservedObjects() >= 4, "Command object aggregate"); }
    }

    private WorkflowRequest donors(WorkflowResourcePolicy policy) {
        return input(DocumentSource.path(corpus.resolve("plain.pdf")), policy)
                .source("donor", DocumentSource.path(corpus.resolve("plain.pdf"))).build();
    }

    private void donorObjects() throws Exception {
        WorkflowOutcome<Integer> outcome = workflow.execute(donors(builder().build()), session -> {
            session.execute(MergeDocuments.version1("donor")); return pageCount(session); });
        // Two independent three-object inputs and at least one distinct imported Page.
        check(outcome.getResult() == 2 && outcome.getResourceUsage().getAcceptedInputBytes() == 708
                && outcome.getResourceUsage().getObservedObjects() >= 7
                && outcome.getResourceUsage().getObservedPages() >= 3, "named-source aggregate replaced primary usage");
    }

    private void donorObjectExcess() throws Exception {
        failure(workflow, donors(policy("objects", 5)), session -> {
            check(pageCount(session) == 1, "primary was not independently admitted");
            session.execute(MergeDocuments.version1("donor")); return null;
        }, DocumentFailureCode.OBJECT_LIMIT_EXCEEDED);
    }

    private void boundaryAt(String dimension, long maximum, boolean excess) throws Exception {
        String fixture = "nesting".equals(dimension) ? "nested.pdf" : "decompression".equals(dimension) ? "filters.pdf"
                : "pixels".equals(dimension) ? "pixels.pdf" : "plain.pdf";
        DocumentSource source = "memory".equals(dimension) ? DocumentSource.bytes(Files.readAllBytes(corpus.resolve(fixture)), 354)
                : DocumentSource.path(corpus.resolve(fixture));
        WorkflowRequest request = input(source, policy(dimension, maximum)).build();
        if (excess) { failure(workflow, request, session -> { throw new AssertionError("Preflight first excess exposed Session"); }, code(dimension)); }
        else { WorkflowOutcome<Integer> outcome = workflow.execute(request, T20ResourceContracts::pageCount);
            WorkflowResourceUsage usage = outcome.getResourceUsage();
            check(outcome.getResult() == 1 && outcome.getExecutionProfile() == WorkflowExecutionProfile.IN_PROCESS, "actual execution");
            check(!"objects".equals(dimension) || usage.getObservedObjects() == 3, "object count");
            check(!"decompression".equals(dimension) || usage.getDecompressedBytes() == 8, "filter stages");
            check(!"pixels".equals(dimension) || usage.getDecodedPixels() == 6, "decoded pixels");
            check(!"memory".equals(dimension) || usage.getPeakOwnedMemoryBytes() == 354, "retained Source memory");
            check(!"temporary".equals(dimension) || usage.getPeakTemporaryStorageBytes() == 354, "snapshot high-water"); }
    }

    private void zeroConsumption() throws Exception {
        // A valid read needs its Source, page graph and snapshot. Absent byte decoding,
        // images and owned arrays consume none of their zero-bound dimensions.
        WorkflowResourcePolicy empty = builder().maximumDecompressedBytes(0).maximumDecodedPixels(0)
                .maximumOwnedMemoryBytes(0).maximumElapsedTime(Duration.ZERO).build();
        WorkflowResourceUsage usage = workflow.execute(working(empty), session -> null).getResourceUsage();
        check(usage.getAcceptedInputBytes() == 354 && usage.getObservedPages() == 1 && usage.getObservedObjects() == 3
                && usage.getDecompressedBytes() == 0 && usage.getDecodedPixels() == 0 && usage.getPeakOwnedMemoryBytes() == 0
                && usage.getPeakTemporaryStorageBytes() == 354 && usage.getElapsedTime().equals(Duration.ZERO), "zero semantics");
    }

    private void codecBoundary(String codec, boolean excess) throws Exception {
        long expected = "ascii85".equals(codec) ? 3 : "flate".equals(codec) ? 14 : 9;
        WorkflowRequest request = input(DocumentSource.path(corpus.resolve(codec + ".pdf")), policy("decompression", expected - (excess ? 1 : 0))).build();
        if (excess) { failure(workflow, request, T20ResourceContracts::pageCount, DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, T20ResourceContracts::pageCount).getResourceUsage().getDecompressedBytes() == expected, "declared codec stages"); }
    }

    private void returnedMemory(long maximum, int reads, boolean excess) throws Exception {
        WorkflowRequest request = input(DocumentSource.path(corpus.resolve("xmp.pdf")), policy("memory", maximum)).build();
        DocumentWork<Void> work = session -> {
            for (int index = 0; index < reads; index++) {
                check(Arrays.equals(session.query(XmpMetadata.version1(4)), new byte[] {60, 120, 47, 62}), "authored detached bytes");
            }
            return null;
        };
        if (excess) { failure(workflow, request, work, DocumentFailureCode.MEMORY_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, work).getResourceUsage().getPeakOwnedMemoryBytes() == maximum, "retained/working memory lifetime"); }
    }

    private void precedence() throws Exception {
        DocumentWorkflow strict = new DocumentWorkflow(WorkflowEnvironment.builder().clock(clock).temporaryDirectory(storage)
                .defaultResourcePolicy(policy("pages", 0)).build());
        WorkflowRequest.Builder request = WorkflowRequest.builder().source("source", DocumentSource.path(corpus.resolve("plain.pdf")))
                .primarySource("source").saveMode(SaveMode.REWRITE);
        failure(strict, request.build(), T20ResourceContracts::pageCount, DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        check(strict.execute(request.resourcePolicy(policy("pages", 1)).build(), T20ResourceContracts::pageCount).getResult() == 1, "request override");
    }

    private void localSource() throws Exception {
        failure(workflow, input(DocumentSource.stream(new BorrowedInput(Files.readAllBytes(corpus.resolve("plain.pdf"))), 353),
                policy("input", 354)).build(), T20ResourceContracts::pageCount, DocumentFailureCode.SOURCE_LIMIT_EXCEEDED);
    }

    private void localDecode() throws Exception {
        failure(workflow, input(DocumentSource.path(corpus.resolve("filters.pdf")), policy("decompression", 100)).build(),
                session -> stream(session, 2).readBytes(), DocumentFailureCode.PDF_VALUE_LIMIT_EXCEEDED);
    }

    private void repeatedDecode(long maximum, boolean excess) throws Exception {
        WorkflowRequest request = input(DocumentSource.path(corpus.resolve("filters.pdf")), policy("decompression", maximum)).build();
        DocumentWork<Void> work = session -> { PdfStream value = stream(session, 8);
            check(Arrays.equals(value.readBytes(), new byte[] {65, 66, 67}), "first decoded bytes");
            check(Arrays.equals(value.readBytes(), new byte[] {65, 66, 67}), "repeated decoded bytes"); return null; };
        if (excess) { failure(workflow, request, work, DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, work).getResourceUsage().getDecompressedBytes() == 24, "decode budget reset"); }
    }

    private void filterPatch(long maximum, boolean excess) throws Exception {
        WorkflowRequest request = input(DocumentSource.path(corpus.resolve("filters.pdf")), policy("decompression", maximum)).build();
        DocumentWork<Void> work = session -> { session.execute(DocumentPatch.builder().setDictionaryEntry(
                payload(session), PdfName.of("T20Marker"), PdfName.of("modified")).build()); return null; };
        if (excess) { failure(workflow, request, work, DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, work).getResourceUsage().getDecompressedBytes() == 16, "changed stream dictionary was not rechecked"); }
    }

    private void pixelsNoRefund() throws Exception {
        WorkflowResourceUsage usage = workflow.execute(input(DocumentSource.path(corpus.resolve("pixels.pdf")), policy("pixels", 6)).build(),
                session -> { ObjectReference image = payload(session);
                    session.execute(DocumentPatch.builder().setDictionaryEntry(image, PdfName.of("Width"), PdfNumber.of(1)).build());
                    session.execute(DocumentPatch.builder().setDictionaryEntry(image, PdfName.of("Width"), PdfNumber.of(2)).build()); return null; }).getResourceUsage();
        check(usage.getDecodedPixels() == 6, "pixel counter refunded or duplicated");
    }

    private void elapsed(Duration advance, boolean excess) throws Exception {
        WorkflowRequest request = input(DocumentSource.stream(new ControlledInput(Files.readAllBytes(corpus.resolve("plain.pdf")),
                () -> clock.now = EPOCH.plus(advance)), 354), policy("elapsed", 1000000000)).build();
        if (excess) { failure(workflow, request, T20ResourceContracts::pageCount, DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED); }
        else { check(workflow.execute(request, T20ResourceContracts::pageCount).getResourceUsage().getElapsedTime().equals(advance), "elapsed equality"); }
    }

    private void backwardClock() throws Exception {
        WorkflowOutcome<Integer> outcome = workflow.execute(working(policy("elapsed", 0)), session -> {
            clock.now = EPOCH.minusSeconds(1); session.execute(AddBlankPage.INSTANCE);
            clock.now = EPOCH; return pageCount(session); });
        check(outcome.getResult() == 2 && outcome.getResourceUsage().getElapsedTime().isZero(), "backward clock added elapsed time");
    }

    private void deadline() throws Exception {
        WorkflowRequest request = input(DocumentSource.stream(new ControlledInput(Files.readAllBytes(corpus.resolve("plain.pdf")),
                () -> clock.now = EPOCH.plusSeconds(1)), 354), policy("elapsed", 2000000000))
                .deadline(EPOCH.plusSeconds(1)).build();
        failure(workflow, request, T20ResourceContracts::pageCount, DocumentFailureCode.DEADLINE_EXCEEDED);
    }

    private void cancelRead() throws Exception {
        CancellationToken token = CancellationToken.create();
        ControlledInput input = new ControlledInput(Files.readAllBytes(corpus.resolve("plain.pdf")), token::cancel);
        failure(workflow, input(DocumentSource.stream(input, 354), policy("input", 354)).cancellationToken(token).build(),
                T20ResourceContracts::pageCount, DocumentFailureCode.WORKFLOW_CANCELLED);
        check(!input.closed, "cancelled caller stream closed");
    }

    private void concurrency(int active, int incoming) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread owner = new Thread(() -> {
            try { workflow.execute(working(policy("concurrency", active)), session -> {
                entered.countDown(); await(release); return null; }); }
            catch (Throwable failure) { error.set(failure); }
        }, "t20-controlled-workflow");
        owner.start();
        try { check(entered.await(10, TimeUnit.SECONDS), "admission latch did not enter");
            failure(new DocumentWorkflow(environment), working(policy("concurrency", incoming)), session -> {
                throw new AssertionError("Rejected admission entered caller work"); }, DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED); }
        finally { release.countDown(); owner.join(10000); }
        check(!owner.isAlive() && error.get() == null, "active workflow did not complete");
        new DocumentWorkflow(environment).execute(working(policy("concurrency", 1)), session -> null);
    }

    private void permits() throws Exception {
        WorkflowRequest request = working(policy("concurrency", 1));
        workflow.execute(request, session -> null);
        failure(workflow, blank(builder().maximumPages(0).maximumConcurrentWorkflows(1).build())
                .target("discard", PublicationTarget.stream(new ByteArrayOutputStream())).build(),
                session -> { session.execute(AddBlankPage.INSTANCE); return null; }, DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        RuntimeException caller = new IllegalStateException("caller");
        try { workflow.execute(request, session -> { throw caller; }); throw new AssertionError("caller exception suppressed"); }
        catch (RuntimeException expected) { check(expected == caller, "primary caller exception changed"); }
        workflow.execute(request, session -> null);
    }

    private void terminal() throws Exception {
        Path target = sentinel("terminal");
        AtomicReference<DocumentSession> expired = new AtomicReference<DocumentSession>();
        failure(workflow, blank(policy("pages", 1)).target("private-target", PublicationTarget.path(target)).build(), session -> {
            expired.set(session); session.execute(AddBlankPage.INSTANCE);
            try { session.execute(AddBlankPage.INSTANCE); throw new AssertionError("page limit not exhausted"); }
            catch (DocumentFailure expected) { check(expected.getCode() == DocumentFailureCode.PAGE_LIMIT_EXCEEDED, "wrong first cause"); }
            return null;
        }, DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        unchanged(target);
        try { expired.get().query(PageCount.INSTANCE); throw new AssertionError("Session did not expire"); }
        catch (IllegalStateException expected) { }
    }

    private void malformed() throws Exception {
        failure(workflow, input(DocumentSource.path(corpus.resolve("malformed.pdf")), policy("input", 10)).build(),
                T20ResourceContracts::pageCount, DocumentFailureCode.SOURCE_READ_FAILED);
    }

    private void unsupported() throws Exception {
        check(workflow.execute(input(DocumentSource.path(corpus.resolve("unsupported.pdf")), policy("decompression", 0)).build(),
                T20ResourceContracts::pageCount).getResult() == 1, "unsupported filter was relabeled as resource exhaustion");
    }

    private void validationStop() throws Exception {
        Path target = sentinel("validation");
        CancellationToken token = CancellationToken.create();
        failure(workflow, blank(builder().build()).target("private-target", PublicationTarget.path(target)).cancellationToken(token)
                .progressListener(phase -> { if (phase == WorkflowProgressPhase.VALIDATED) { token.cancel(); } }).build(),
                session -> { session.execute(AddBlankPage.INSTANCE); return null; }, DocumentFailureCode.WORKFLOW_CANCELLED);
        unchanged(target);
    }

    private void publicationQuota() throws Exception {
        Path first = sentinel("quota-first"), second = sentinel("quota-second");
        failure(workflow, blank(policy("temporary", 800)).target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second)).build(), session -> {
                    session.execute(AddBlankPage.INSTANCE); return null; }, DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED);
        unchanged(first); unchanged(second);
    }

    private void publicationHighWater() throws Exception {
        // An independently authored uncompressed Source and an incremental, unchanged
        // blank object model need no stream cache. The retained snapshot and live file
        // bytes therefore discriminate adjacent charges without searching a quota.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        WorkflowOutcome<Void> baseline = workflow.execute(incremental().target("stream", PublicationTarget.stream(stream)).build(),
                T20ResourceContracts::markBlank);
        Path baselinePdf = product("native-stream-high-water");
        Files.write(baselinePdf, stream.toByteArray());
        check(baseline.getResourceUsage().getPeakTemporaryStorageBytes() == 354 + Files.size(baselinePdf),
                "snapshot and staged stream-product lifetime model");
        retained.retain(baselinePdf, EvidenceFiles.sha256(baselinePdf));
        Path first = product("native-first"), second = product("native-second");
        WorkflowOutcome<Void> outcome = workflow.execute(incremental().target("first", PublicationTarget.path(first))
                .target("second", PublicationTarget.path(second)).build(), T20ResourceContracts::markBlank);
        long length = Files.size(first);
        check(Arrays.equals(Files.readAllBytes(first), Files.readAllBytes(second)), "same product differed across Targets");
        check(outcome.getResourceUsage().getPeakTemporaryStorageBytes() == 354 + 2 * length
                && outcome.getResourceUsage().isWithin(policy("temporary", 1048576)), "publication file lifetime model");
        observed.setProperty(current + ".stream-peak-temporary", Long.toString(baseline.getResourceUsage().getPeakTemporaryStorageBytes()));
        observed.setProperty(current + ".peak-temporary", Long.toString(outcome.getResourceUsage().getPeakTemporaryStorageBytes()));
        observed.setProperty(current + ".published-bytes", Long.toString(length));
        receipts(outcome.getPublicationReceipts(), PublicationStatus.COMMITTED, PublicationStatus.COMMITTED);
        check(T03BlankSemantics.inspect(first, WorkflowExecutionProfile.IN_PROCESS) == EvidenceResult.PASS, "published outcome");
        retained.retain(first, EvidenceFiles.sha256(first)); retained.retain(second, EvidenceFiles.sha256(second));
    }

    private WorkflowRequest.Builder incremental() {
        return input(DocumentSource.path(corpus.resolve("plain.pdf")), policy("temporary", 1048576)).saveMode(SaveMode.INCREMENTAL);
    }

    private static Void markBlank(DocumentSession session) throws DocumentFailure {
        session.execute(DocumentPatch.builder().setDictionaryEntry(session.query(DocumentRootReference.INSTANCE),
                PdfName.of("Type"), PdfName.of("Catalog")).build());
        return null;
    }

    private void adjacentCancellation() throws Exception {
        Path first = sentinel("adjacent-first"), later = sentinel("adjacent-later");
        Set<Path> before = new HashSet<Path>();
        try (java.util.stream.Stream<Path> paths = Files.list(first.getParent())) { paths.forEach(before::add); }
        CancellationToken token = CancellationToken.create();
        clock.checkpoint = () -> {
            try (java.util.stream.Stream<Path> paths = Files.list(first.getParent())) {
                boolean written = paths.filter(Files::isRegularFile).filter(path -> !before.contains(path))
                        .anyMatch(path -> { try { return Files.size(path) > 0; } catch (IOException failure) { throw new AssertionError(failure); } });
                if (written) { token.cancel(); }
            } catch (IOException failure) { throw new AssertionError(failure); }
        };
        DocumentFailure stopped = failure(workflow, incremental().target("first", PublicationTarget.path(first)).target("later", PublicationTarget.path(later))
                .cancellationToken(token).build(), T20ResourceContracts::markBlank, DocumentFailureCode.WORKFLOW_CANCELLED);
        receipts(stopped.getPublicationReceipts(), PublicationStatus.NOT_ATTEMPTED, PublicationStatus.NOT_ATTEMPTED);
        check(stopped.getPublicationReceipts().stream().noneMatch(PublicationReceipt::isPartialOutputPossible), "uncommitted Paths claimed partial output");
        check(token.isCancellationRequested(), "adjacent file was never observed before commitment");
        unchanged(first); unchanged(later);
        observed.setProperty(current + ".adjacent-observed", "true");
    }

    private void partialPublication() throws Exception {
        Path first = product("native-before-failure"), later = sentinel("later");
        BrokenOutput stream = new BrokenOutput();
        DocumentFailure failure = failure(workflow, blank(builder().build()).target("first", PublicationTarget.path(first))
                .target("current", PublicationTarget.stream(stream)).target("later", PublicationTarget.path(later)).build(),
                session -> { session.execute(AddBlankPage.INSTANCE); return null; }, DocumentFailureCode.PUBLICATION_FAILED);
        receipts(failure.getPublicationReceipts(), PublicationStatus.COMMITTED, PublicationStatus.FAILED, PublicationStatus.NOT_ATTEMPTED);
        check(failure.getPublicationReceipts().get(1).isPartialOutputPossible() && stream.count == 1 && !stream.closed, "partial caller output");
        unchanged(later);
        check(T03BlankSemantics.inspect(first, WorkflowExecutionProfile.IN_PROCESS) == EvidenceResult.PASS, "earlier commit lost");
        retained.retain(first, EvidenceFiles.sha256(first));
    }

    private void splitPages() throws Exception {
        Path first = sentinel("split-first"), second = sentinel("split-second");
        failure(workflow, input(DocumentSource.path(corpus.resolve("plain.pdf")), policy("pages", 2))
                .target("first", PublicationTarget.path(first)).target("second", PublicationTarget.path(second)).build(),
                session -> { session.execute(SplitDocument.version1().target("first", PageRange.of(1, 1))
                        .target("second", PageRange.of(1, 1)).build()); return null; }, DocumentFailureCode.PAGE_LIMIT_EXCEEDED);
        unchanged(first); unchanged(second);
    }

    private void splitObjects() throws Exception {
        Path first = sentinel("split-objects-first"), second = sentinel("split-objects-second");
        boolean[] staged = {false};
        // Primary: three indirect objects. Each distinct product needs at least an
        // indirect Pages parent and Page: 3 + 2 + 2 exceeds six, without per-product resets.
        failure(workflow, input(DocumentSource.path(corpus.resolve("plain.pdf")), policy("objects", 6))
                .target("first", PublicationTarget.path(first)).target("second", PublicationTarget.path(second))
                .progressListener(phase -> { if (phase == WorkflowProgressPhase.STAGED) { staged[0] = true; } }).build(),
                T20ResourceContracts::split, DocumentFailureCode.OBJECT_LIMIT_EXCEEDED);
        check(!staged[0], "product aggregate was replaced by a later per-file validation check");
        observed.setProperty(current + ".before-validation", "true");
        unchanged(first); unchanged(second);
    }

    private static Void split(DocumentSession session) throws DocumentFailure {
        session.execute(SplitDocument.version1().target("first", PageRange.of(1, 1)).target("second", PageRange.of(1, 1)).build());
        return null;
    }

    private <R> DocumentFailure failure(DocumentWorkflow executor, WorkflowRequest request, DocumentWork<R> work,
            DocumentFailureCode code) throws DocumentFailure {
        try { executor.execute(request, work); throw new AssertionError("Expected " + code + " in " + current); }
        catch (DocumentFailure failure) {
            check(failure.getCode() == code, "wrong failure in " + current + ": " + failure.getCode());
            if (isResource(code)) {
                check(CAPABILITY.equals(failure.getCapabilityId()) && failure.getCause() == null, "unsafe resource failure");
                check(diagnostic(code).equals(failure.getMessage()), "content-free diagnostic changed in " + current + ": " + failure.getMessage());
            }
            observed.setProperty(current + ".code", failure.getCode().name());
            observed.setProperty(current + ".capability", failure.getCapabilityId());
            observed.setProperty(current + ".diagnostic", failure.getMessage());
            StringBuilder statuses = new StringBuilder();
            for (PublicationReceipt receipt : failure.getPublicationReceipts()) {
                if (statuses.length() > 0) { statuses.append(','); }
                statuses.append(receipt.getStatus()).append(':').append(receipt.isPartialOutputPossible());
            }
            observed.setProperty(current + ".receipts", statuses.toString());
            return failure;
        }
    }

    private static boolean isResource(DocumentFailureCode code) {
        return Arrays.asList(DocumentFailureCode.WORKFLOW_INPUT_LIMIT_EXCEEDED, DocumentFailureCode.PAGE_LIMIT_EXCEEDED,
                DocumentFailureCode.OBJECT_LIMIT_EXCEEDED, DocumentFailureCode.NESTING_LIMIT_EXCEEDED,
                DocumentFailureCode.DECOMPRESSION_LIMIT_EXCEEDED, DocumentFailureCode.PIXEL_LIMIT_EXCEEDED,
                DocumentFailureCode.MEMORY_LIMIT_EXCEEDED, DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                DocumentFailureCode.ELAPSED_TIME_LIMIT_EXCEEDED, DocumentFailureCode.CONCURRENCY_LIMIT_EXCEEDED).contains(code);
    }

    private static String diagnostic(DocumentFailureCode code) {
        switch (code) {
            case WORKFLOW_INPUT_LIMIT_EXCEEDED: return "The workflow input-byte limit was exceeded.";
            case PAGE_LIMIT_EXCEEDED: return "The workflow page-count limit was exceeded.";
            case OBJECT_LIMIT_EXCEEDED: return "The workflow PDF-object limit was exceeded.";
            case NESTING_LIMIT_EXCEEDED: return "The workflow nesting-depth limit was exceeded.";
            case DECOMPRESSION_LIMIT_EXCEEDED: return "The workflow decompression limit was exceeded.";
            case PIXEL_LIMIT_EXCEEDED: return "The workflow decoded-pixel limit was exceeded.";
            case MEMORY_LIMIT_EXCEEDED: return "The workflow owned-memory limit was exceeded.";
            case TEMPORARY_STORAGE_LIMIT_EXCEEDED: return "The workflow temporary-storage limit was exceeded.";
            case ELAPSED_TIME_LIMIT_EXCEEDED: return "The workflow elapsed-time limit was exceeded.";
            case CONCURRENCY_LIMIT_EXCEEDED: return "The workflow concurrency limit was exceeded.";
            default: throw new AssertionError(code);
        }
    }

    private static DocumentFailureCode code(String dimension) {
        return DocumentFailureCode.valueOf("pages".equals(dimension) ? "PAGE_LIMIT_EXCEEDED" : "objects".equals(dimension) ? "OBJECT_LIMIT_EXCEEDED"
                : "nesting".equals(dimension) ? "NESTING_LIMIT_EXCEEDED" : "decompression".equals(dimension) ? "DECOMPRESSION_LIMIT_EXCEEDED"
                : "pixels".equals(dimension) ? "PIXEL_LIMIT_EXCEEDED" : "memory".equals(dimension) ? "MEMORY_LIMIT_EXCEEDED" : "TEMPORARY_STORAGE_LIMIT_EXCEEDED");
    }

    private static int pageCount(DocumentSession session) throws DocumentFailure { return session.query(PageCount.INSTANCE); }
    private static ObjectReference payload(DocumentSession session) throws DocumentFailure {
        PdfDictionary root = (PdfDictionary) session.query(InspectObject.version1(session.query(DocumentRootReference.INSTANCE), PdfInspectionLimits.of(32, 32)));
        return ((PdfIndirectReference) root.get(PdfName.of("Payload"))).getReference();
    }
    private static PdfStream stream(DocumentSession session, long limit) throws DocumentFailure {
        return (PdfStream) session.query(InspectObject.version1(payload(session), PdfInspectionLimits.of(32, limit)));
    }
    private static WorkflowRequest.Builder blank(WorkflowResourcePolicy policy) { return WorkflowRequest.builder().saveMode(SaveMode.REWRITE).resourcePolicy(policy); }
    private static WorkflowRequest.Builder input(DocumentSource source, WorkflowResourcePolicy policy) { return blank(policy).source("source", source).primarySource("source"); }
    private WorkflowRequest working(WorkflowResourcePolicy policy) { return input(DocumentSource.path(corpus.resolve("plain.pdf")), policy).build(); }
    private static WorkflowResourcePolicy.Builder builder() {
        return WorkflowResourcePolicy.builder().maximumInputBytes(1L << 30).maximumPages(5000).maximumObjects(2000000)
                .maximumNestingDepth(16384).maximumDecompressedBytes(4L << 30).maximumDecodedPixels(1000000000)
                .maximumOwnedMemoryBytes(256L << 20).maximumTemporaryStorageBytes(4L << 30)
                .maximumElapsedTime(Duration.ofMinutes(5)).maximumConcurrentWorkflows(4);
    }
    private static WorkflowResourcePolicy policy(String dimension, long limit) {
        WorkflowResourcePolicy.Builder result = builder();
        switch (dimension) {
            case "input": result.maximumInputBytes(limit); break;
            case "pages": result.maximumPages((int) limit); break;
            case "objects": result.maximumObjects(limit); break;
            case "nesting": result.maximumNestingDepth((int) limit); break;
            case "decompression": result.maximumDecompressedBytes(limit); break;
            case "pixels": result.maximumDecodedPixels(limit); break;
            case "memory": result.maximumOwnedMemoryBytes(limit); break;
            case "temporary": result.maximumTemporaryStorageBytes(limit); break;
            case "elapsed": result.maximumElapsedTime(Duration.ofNanos(limit)); break;
            case "concurrency": result.maximumConcurrentWorkflows((int) limit); break;
            default: throw new AssertionError(dimension);
        }
        return result.build();
    }
    private Path product(String name) throws IOException {
        Path directory = Files.createDirectory(output.resolve(name)), path = directory.resolve("blank.pdf");
        callerPaths.add(directory); callerPaths.add(path); return path;
    }
    private Path sentinel(String name) throws IOException {
        Path path = output.resolve(name + ".sentinel"); callerPaths.add(path); Files.write(path, new byte[] {1, 2, 3}); return path;
    }
    private static void unchanged(Path path) throws IOException { check(Arrays.equals(new byte[] {1, 2, 3}, Files.readAllBytes(path)), "Path Target changed before commitment"); }
    private static void receipts(List<PublicationReceipt> receipts, PublicationStatus... statuses) {
        check(receipts.size() == statuses.length, "receipt cardinality");
        for (int index = 0; index < statuses.length; index++) { check(receipts.get(index).getStatus() == statuses[index], "receipt ordering"); }
    }
    private static void check(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
    private static void await(CountDownLatch latch) {
        try { check(latch.await(10, TimeUnit.SECONDS), "latch expired"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }
    private interface Experiment { void run() throws Exception; }
    private static final class MutableClock extends Clock {
        private volatile Instant now = EPOCH;
        private Runnable checkpoint;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { if (!getZone().equals(zone)) { throw new IllegalArgumentException("UTC fixture"); } return this; }
        @Override public Instant instant() { if (checkpoint != null) { checkpoint.run(); } return now; }
    }
    private static class BorrowedInput extends ByteArrayInputStream {
        boolean closed;
        BorrowedInput(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }
    private static final class ControlledInput extends BorrowedInput {
        private final Runnable afterRead;
        ControlledInput(byte[] bytes, Runnable afterRead) { super(bytes); this.afterRead = afterRead; }
        @Override public synchronized int read(byte[] bytes, int offset, int length) {
            int count = super.read(bytes, offset, Math.min(length, 1)); if (count >= 0) { afterRead.run(); } return count;
        }
    }
    private static final class BrokenOutput extends java.io.OutputStream {
        private int count;
        private boolean closed;
        @Override public void write(int value) throws IOException { if (count == 1) { throw new IOException("private-output-detail"); } count++; }
        @Override public void close() { closed = true; }
    }
}

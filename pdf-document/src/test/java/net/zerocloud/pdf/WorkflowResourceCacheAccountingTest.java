package net.zerocloud.pdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.apache.pdfbox.io.RandomAccess;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class WorkflowResourceCacheAccountingTest {

    private static final long CACHE_PAGE_BYTES = 4096L;

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void closedBuffersReuseAlreadyChargedCachePages()
            throws Exception {
        WorkflowResourceContext resources = open(CACHE_PAGE_BYTES);
        try {
            RandomAccessStreamCache cache = resources
                    .streamCacheFactory()
                    .create();
            writeAndClose(cache);
            writeAndClose(cache);
        } finally {
            resources.close();
        }
    }

    @Test
    public void firstCachePageHonorsExactBoundaryAndFirstExcess()
            throws Exception {
        WorkflowResourceContext exact = open(CACHE_PAGE_BYTES);
        try {
            RandomAccess buffer = exact.streamCacheFactory()
                    .create()
                    .createBuffer();
            buffer.close();
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = open(CACHE_PAGE_BYTES - 1L);
        try {
            try {
                RandomAccess buffer = excess.streamCacheFactory()
                        .create()
                        .createBuffer();
                fail("Expected cache-page accounting to exhaust storage");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void clearedBufferRetainsItsInitialCachePage()
            throws Exception {
        WorkflowResourceContext resources = open(CACHE_PAGE_BYTES);
        try {
            RandomAccessStreamCache cache = resources
                    .streamCacheFactory()
                    .create();
            RandomAccess retained = cache.createBuffer();
            retained.clear();
            try {
                cache.createBuffer();
                fail("Expected the retained cache page to remain charged");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.TEMPORARY_STORAGE_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            } finally {
                retained.close();
            }
        } finally {
            resources.close();
        }
    }

    @Test
    public void accumulatorAccountsGrowthAndFinalCopyAtNonPowerBoundary()
            throws Exception {
        WorkflowResourceContext exact = openWithMemory(13L);
        try {
            WorkflowResourceContext.OwnedByteAccumulator accumulator =
                    exact.ownedByteAccumulator();
            try {
                byte[] payload = "12345".getBytes(StandardCharsets.US_ASCII);
                for (byte value : payload) {
                    accumulator.write(value);
                }
                WorkflowResourceContext.OwnedBytes result =
                        accumulator.finishWorking();
                result.close();
            } finally {
                accumulator.close();
            }
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(12L);
        try {
            WorkflowResourceContext.OwnedByteAccumulator accumulator =
                    excess.ownedByteAccumulator();
            try {
                byte[] payload = "12345".getBytes(StandardCharsets.US_ASCII);
                for (byte value : payload) {
                    accumulator.write(value);
                }
                try {
                    accumulator.finishWorking();
                    fail("Expected final-copy memory peak to exhaust policy");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                            failure.getCode());
                }
            } finally {
                accumulator.close();
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void textAccumulatorAccountsBuilderAndFinalStringPeak()
            throws Exception {
        WorkflowResourceContext exact = openWithMemory(20L);
        try {
            try (WorkflowResourceContext.OwnedMemoryScope ownership =
                            exact.ownedMemoryScope();
                    WorkflowResourceContext.OwnedTextAccumulator text =
                            exact.ownedTextAccumulator()) {
                text.append("12345");
                assertEquals("12345", ownership.hold(text.finishWorking()));
                ownership.transfer();
            }
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(19L);
        try {
            try (WorkflowResourceContext.OwnedTextAccumulator text =
                    excess.ownedTextAccumulator()) {
                text.append("12345");
                try {
                    text.finishWorking();
                    fail("Expected final-String memory peak to exhaust policy");
                } catch (DocumentFailure failure) {
                    assertEquals(
                            DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                            failure.getCode());
                }
            }
        } finally {
            excess.close();
        }
    }

    @Test
    public void cmapNumberStringIsReservedBeforeMaterialization()
            throws Exception {
        byte[] cmap = "12345 ".getBytes(StandardCharsets.US_ASCII);
        WorkflowResourceContext exact = openWithMemory(10L);
        try {
            assertEquals(
                    0,
                    PdfBoxCMapPreflight.countMappings(
                            cmap, Integer.MAX_VALUE, exact));
        } finally {
            exact.close();
        }

        WorkflowResourceContext excess = openWithMemory(9L);
        try {
            try {
                PdfBoxCMapPreflight.countMappings(
                        cmap, Integer.MAX_VALUE, excess);
                fail("Expected CMap token memory to exhaust policy");
            } catch (IOException failure) {
                DocumentFailure resourceFailure =
                        WorkflowResourceContext.findResourceFailure(failure);
                assertEquals(
                        DocumentFailureCode.MEMORY_LIMIT_EXCEEDED,
                        resourceFailure.getCode());
            }
        } finally {
            excess.close();
        }
    }

    private WorkflowResourceContext open(long maximumTemporaryStorage)
            throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        return WorkflowResourceContext.open(
                policy(maximumTemporaryStorage),
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                root);
    }

    private WorkflowResourceContext openWithMemory(long maximumMemory)
            throws Exception {
        Path root = temporaryFolder.newFolder().toPath();
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        WorkflowResourcePolicy policy = WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(maximumMemory)
                .maximumTemporaryStorageBytes(
                        defaults.getMaximumTemporaryStorageBytes())
                .maximumElapsedTime(Duration.ofMinutes(5L))
                .maximumConcurrentWorkflows(1)
                .build();
        return WorkflowResourceContext.open(
                policy,
                Clock.systemUTC(),
                CancellationToken.none(),
                null,
                root);
    }

    private static void writeAndClose(RandomAccessStreamCache cache)
            throws Exception {
        RandomAccess buffer = cache.createBuffer();
        buffer.write(1);
        buffer.close();
    }

    private static WorkflowResourcePolicy policy(long temporaryStorage) {
        WorkflowResourcePolicy defaults = WorkflowResourcePolicy.safeDefaults();
        return WorkflowResourcePolicy.builder()
                .maximumInputBytes(defaults.getMaximumInputBytes())
                .maximumPages(defaults.getMaximumPages())
                .maximumObjects(defaults.getMaximumObjects())
                .maximumNestingDepth(defaults.getMaximumNestingDepth())
                .maximumDecompressedBytes(
                        defaults.getMaximumDecompressedBytes())
                .maximumDecodedPixels(defaults.getMaximumDecodedPixels())
                .maximumOwnedMemoryBytes(
                        defaults.getMaximumOwnedMemoryBytes())
                .maximumTemporaryStorageBytes(temporaryStorage)
                .maximumElapsedTime(Duration.ofMinutes(5L))
                .maximumConcurrentWorkflows(1)
                .build();
    }
}

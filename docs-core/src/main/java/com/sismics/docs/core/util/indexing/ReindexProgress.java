package com.sismics.docs.core.util.indexing;

import com.sismics.docs.core.event.RebuildIndexAsyncEvent;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe singleton tracker for asynchronous search index rebuild & OCR reprocessing progress.
 */
public final class ReindexProgress {
    public enum Phase {
        IDLE,
        QUEUED,
        CLEARING,
        OCR_REPROCESSING,
        INDEXING_DOCUMENTS,
        INDEXING_FILES,
        DONE,
        ERROR
    }

    public static final class ErrorEntry {
        private final String fileId;
        private final String fileName;
        private final String message;

        public ErrorEntry(String fileId, String fileName, String message) {
            this.fileId = fileId == null ? "" : fileId;
            this.fileName = fileName == null ? "" : fileName;
            this.message = message == null ? "" : message;
        }
    }

    private static final int MAX_ERRORS = 50;
    private static final Object LOCK = new Object();

    private static boolean running = false;
    private static Phase phase = Phase.IDLE;
    private static RebuildIndexAsyncEvent.OcrMode ocrMode = RebuildIndexAsyncEvent.OcrMode.NONE;
    private static Long startedAt = null;
    private static Long finishedAt = null;
    private static long totalDocuments = 0;
    private static long processedDocuments = 0;
    private static long totalFiles = 0;
    private static long processedFiles = 0;
    private static long ocrTotal = 0;
    private static long ocrProcessed = 0;
    private static long ocrSucceeded = 0;
    private static long ocrSkipped = 0;
    private static long ocrFailed = 0;
    private static String currentItem = "";
    private static String errorMessage = "";
    private static final List<ErrorEntry> errors = new ArrayList<>();

    private ReindexProgress() {
    }

    public static boolean isRunning() {
        synchronized (LOCK) {
            return running;
        }
    }

    /**
     * Atomically marks a rebuild job as queued if none is currently running.
     *
     * @param mode OCR reprocessing mode
     * @return true if transitioned to running, false if already running
     */
    public static boolean tryQueue(RebuildIndexAsyncEvent.OcrMode mode) {
        synchronized (LOCK) {
            if (running) {
                return false;
            }
            resetLocked(mode, Phase.QUEUED);
            return true;
        }
    }

    /**
     * Called by the listener when execution actually begins (covers internal triggers too).
     */
    public static void beginExecution(RebuildIndexAsyncEvent.OcrMode mode) {
        synchronized (LOCK) {
            if (!running || phase != Phase.QUEUED) {
                resetLocked(mode, Phase.CLEARING);
            } else {
                phase = Phase.CLEARING;
                ocrMode = mode == null ? RebuildIndexAsyncEvent.OcrMode.NONE : mode;
            }
        }
    }

    private static void resetLocked(RebuildIndexAsyncEvent.OcrMode mode, Phase initialPhase) {
        running = true;
        phase = initialPhase;
        ocrMode = mode == null ? RebuildIndexAsyncEvent.OcrMode.NONE : mode;
        startedAt = System.currentTimeMillis();
        finishedAt = null;
        totalDocuments = 0;
        processedDocuments = 0;
        totalFiles = 0;
        processedFiles = 0;
        ocrTotal = 0;
        ocrProcessed = 0;
        ocrSucceeded = 0;
        ocrSkipped = 0;
        ocrFailed = 0;
        currentItem = "";
        errorMessage = "";
        errors.clear();
    }

    public static void setTotals(long docs, long files) {
        synchronized (LOCK) {
            totalDocuments = Math.max(0, docs);
            totalFiles = Math.max(0, files);
        }
    }

    public static void setPhase(Phase newPhase) {
        synchronized (LOCK) {
            phase = newPhase;
            currentItem = "";
        }
    }

    public static void setOcrTotal(long total) {
        synchronized (LOCK) {
            ocrTotal = Math.max(0, total);
        }
    }

    public static void setCurrentItem(String item) {
        synchronized (LOCK) {
            currentItem = item == null ? "" : item;
        }
    }

    public static void incrementOcrSucceeded() {
        synchronized (LOCK) {
            ocrProcessed++;
            ocrSucceeded++;
        }
    }

    public static void incrementOcrSkipped() {
        synchronized (LOCK) {
            ocrProcessed++;
            ocrSkipped++;
        }
    }

    public static void addOcrFailure(String fileId, String fileName, String message) {
        synchronized (LOCK) {
            ocrProcessed++;
            ocrFailed++;
            if (errors.size() < MAX_ERRORS) {
                errors.add(new ErrorEntry(fileId, fileName, message));
            }
        }
    }

    public static void addProcessedDocuments(long delta, String lastTitle) {
        synchronized (LOCK) {
            processedDocuments += delta;
            if (processedDocuments > totalDocuments && totalDocuments > 0) {
                totalDocuments = processedDocuments;
            }
            if (lastTitle != null) {
                currentItem = lastTitle;
            }
        }
    }

    public static void addProcessedFiles(long delta, String lastName) {
        synchronized (LOCK) {
            processedFiles += delta;
            if (processedFiles > totalFiles && totalFiles > 0) {
                totalFiles = processedFiles;
            }
            if (lastName != null) {
                currentItem = lastName;
            }
        }
    }

    public static void complete() {
        synchronized (LOCK) {
            running = false;
            phase = Phase.DONE;
            finishedAt = System.currentTimeMillis();
            currentItem = "";
        }
    }

    public static void fail(String message) {
        synchronized (LOCK) {
            running = false;
            phase = Phase.ERROR;
            finishedAt = System.currentTimeMillis();
            errorMessage = message == null ? "Unknown error" : message;
        }
    }

    public static JsonObject toJson() {
        synchronized (LOCK) {
            long totalSteps = totalDocuments + totalFiles + (ocrMode != RebuildIndexAsyncEvent.OcrMode.NONE ? ocrTotal : 0);
            long doneSteps = processedDocuments + processedFiles + (ocrMode != RebuildIndexAsyncEvent.OcrMode.NONE ? ocrProcessed : 0);
            int percent;
            if (phase == Phase.DONE) {
                percent = 100;
            } else if (phase == Phase.IDLE || phase == Phase.QUEUED) {
                percent = 0;
            } else if (totalSteps <= 0) {
                percent = running ? 5 : 100;
            } else {
                percent = (int) Math.min(99, Math.round((doneSteps * 100.0) / totalSteps));
            }

            JsonArrayBuilder errArr = Json.createArrayBuilder();
            for (ErrorEntry e : errors) {
                errArr.add(Json.createObjectBuilder()
                        .add("file_id", e.fileId)
                        .add("file_name", e.fileName)
                        .add("message", e.message));
            }

            JsonObjectBuilder b = Json.createObjectBuilder()
                    .add("running", running)
                    .add("phase", phase.name())
                    .add("ocr_mode", ocrMode.name())
                    .add("percent", percent)
                    .add("total_documents", totalDocuments)
                    .add("processed_documents", processedDocuments)
                    .add("total_files", totalFiles)
                    .add("processed_files", processedFiles)
                    .add("ocr_total", ocrTotal)
                    .add("ocr_processed", ocrProcessed)
                    .add("ocr_succeeded", ocrSucceeded)
                    .add("ocr_skipped", ocrSkipped)
                    .add("ocr_failed", ocrFailed)
                    .add("current_item", currentItem)
                    .add("error_message", errorMessage)
                    .add("errors", errArr);
            if (startedAt != null) {
                b.add("started_at", startedAt);
            }
            if (finishedAt != null) {
                b.add("finished_at", finishedAt);
            }
            return b.build();
        }
    }
}

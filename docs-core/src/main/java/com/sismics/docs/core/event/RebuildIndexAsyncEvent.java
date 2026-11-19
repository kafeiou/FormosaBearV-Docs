package com.sismics.docs.core.event;

import com.google.common.base.MoreObjects;

/**
 * Rebuild index event.
 *
 * @author bgamard
 */
public class RebuildIndexAsyncEvent {
    public enum OcrMode {
        NONE,
        MISSING,
        ALL;

        public static OcrMode fromString(String value) {
            if (value == null || value.trim().isEmpty()) {
                return NONE;
            }
            try {
                return OcrMode.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    private final OcrMode ocrMode;

    public RebuildIndexAsyncEvent() {
        this(OcrMode.NONE);
    }

    public RebuildIndexAsyncEvent(OcrMode ocrMode) {
        this.ocrMode = ocrMode == null ? OcrMode.NONE : ocrMode;
    }

    public OcrMode getOcrMode() {
        return ocrMode;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ocrMode", ocrMode)
                .toString();
    }
}

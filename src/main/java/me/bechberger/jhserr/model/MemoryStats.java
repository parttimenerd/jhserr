package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Memory usage statistics (used, committed, reserved in KB).
 * Used by Metaspace, heap regions, and other memory blocks.
 */
public record MemoryStats(
        @JsonProperty("usedKb") long usedKb,
        @JsonProperty("committedKb") long committedKb,
        @JsonProperty("reservedKb") long reservedKb
) {
    public @NotNull String toLine(@NotNull String prefix) {
        return String.format("%s used %dK, committed %dK, reserved %dK",
                prefix, usedKb, committedKb, reservedKb);
    }
}

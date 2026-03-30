package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed single code heap entry (e.g., "non-profiled nmethods", "profiled nmethods", "non-nmethods").
 *
 * Format:
 * CodeHeap 'non-profiled nmethods': size=120032Kb used=21Kb max_used=21Kb free=120010Kb
 *   bounds [0x000000015afc4000, 0x000000015b234000, 0x00000001624fc000]
 */
public record CodeHeapEntry(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("sizeKb") long sizeKb,
        @JsonProperty("usedKb") long usedKb,
        @JsonProperty("maxUsedKb") long maxUsedKb,
        @JsonProperty("freeKb") long freeKb,
        @JsonProperty("boundLow") @NotNull String boundLow,
        @JsonProperty("boundCurrent") @NotNull String boundCurrent,
        @JsonProperty("boundHigh") @NotNull String boundHigh,
        @JsonProperty("totalBlobs") @Nullable Integer totalBlobs,
        @JsonProperty("nmethods") @Nullable Integer nmethods,
        @JsonProperty("adapters") @Nullable Integer adapters,
        @JsonProperty("compilationEnabled") @Nullable Boolean compilationEnabled
) {
    public @NotNull String toHeaderLine() {
        return String.format(
                "CodeHeap '%s': size=%dKb used=%dKb max_used=%dKb free=%dKb",
                name, sizeKb, usedKb, maxUsedKb, freeKb
        );
    }

    public @NotNull String toBoundsLine() {
        return String.format(" bounds [%s, %s, %s]", boundLow, boundCurrent, boundHigh);
    }
}

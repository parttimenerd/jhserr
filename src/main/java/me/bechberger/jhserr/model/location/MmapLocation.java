package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Register points into mmap'd memory region.
 * Example: 0x00007fff94050000 in mmap'd memory region [0x00007fff94050000 - 0x00007fff94060000], tag mtInternal
 */
public record MmapLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("regionStart") @NotNull String regionStart,
        @JsonProperty("regionEnd") @NotNull String regionEnd,
        @JsonProperty("tag") @NotNull String tag
) implements RegisterLocation {
    @Override
    public String toString() {
        return address + " in mmap'd memory region [" + regionStart + " - " + regionEnd + "], tag " + tag;
    }
}

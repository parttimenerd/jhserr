package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Register points into a malloc'd block.
 * Example: 0x00007fff8c147ac0 into live malloced block starting at 0x00007fff8c147ac0, size 64, tag mtGC
 */
public record MallocLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("blockStart") @NotNull String blockStart,
        @JsonProperty("size") @NotNull String size,
        @JsonProperty("tag") @NotNull String tag
) implements RegisterLocation {
    @Override
    public String toString() {
        return address + " into live malloced block starting at " + blockStart + ", size " + size + ", tag " + tag;
    }
}

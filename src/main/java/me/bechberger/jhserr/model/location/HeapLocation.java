package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Register points into the heap (allocated memory).
 * Example: 0x00000000fd500000 is an unallocated location in the heap
 */
public record HeapLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("description") @NotNull String description  // "unallocated", "allocated", etc.
) implements RegisterLocation {
    @Override
    public String toString() {
        return address + " is " + description + " location in the heap";
    }
}

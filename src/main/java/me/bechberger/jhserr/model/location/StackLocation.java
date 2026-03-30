package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Register points into the stack for a specific thread.
 * Example: 0x00007fff60ffccf0 is pointing into the stack for thread: 0x00007fff8c481fc0
 */
public record StackLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("threadPtr") @NotNull String threadPtr
) implements RegisterLocation {
    @Override
    public String toString() {
        return address + " is pointing into the stack for thread: " + threadPtr;
    }
}

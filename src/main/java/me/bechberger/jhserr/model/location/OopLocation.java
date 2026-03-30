package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Register holds a reference to a Java object (OOP).
 * Example: 0x00000000fd580000 is an oop: gc.stress.gcold.TreeNode
 */
public record OopLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("className") @NotNull String className,
        @JsonProperty("details") @Nullable String details  // Optional additional details about the object
) implements RegisterLocation {
    @Override
    public String toString() {
        if (details != null) {
            return address + " is an oop: " + className + " " + details;
        }
        return address + " is an oop: " + className;
    }
}

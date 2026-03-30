package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Fallback for unknown register location types.
 */
public record UnknownRegisterLocation(
        @JsonProperty("text") @NotNull String text
) implements RegisterLocation {
    @Override
    public String toString() {
        return text;
    }
}

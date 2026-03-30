package me.bechberger.jhserr.model.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fallback register for unknown architectures or register types.
 */
public record UnknownRegister(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("value") @NotNull String value,
        @JsonProperty("description") @Nullable String description
) implements Register {}

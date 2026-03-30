package me.bechberger.jhserr.model.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PowerPC register (ppc64le) (r0-r31, etc.)
 */
public record PowerPCRegister(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("value") @NotNull String value,
        @JsonProperty("description") @Nullable String description
) implements Register {}

package me.bechberger.jhserr.model.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ARM64 register (x0-x30, fp, lr, sp, pc, etc.)
 */
public record ARMRegister(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("value") @NotNull String value,
        @JsonProperty("description") @Nullable String description
) implements Register {}

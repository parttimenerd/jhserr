package me.bechberger.jhserr.model.register;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * x86-64 register (rax, rbx, rcx, rdx, rsi, rdi, rbp, rsp, r8-r15, etc.)
 */
public record X86Register(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("value") @NotNull String value,
        @JsonProperty("description") @Nullable String description
) implements Register {}

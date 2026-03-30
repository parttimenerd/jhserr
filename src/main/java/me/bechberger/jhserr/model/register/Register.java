package me.bechberger.jhserr.model.register;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Sealed interface for CPU registers.
 * Supports multiple architectures with their specific register sets.
 */
public sealed interface Register permits
        X86Register, ARMRegister, PowerPCRegister, UnknownRegister {
    @NotNull String name();
    @NotNull String value();  // hex string like "0x0000..."
    @Nullable String description();
}

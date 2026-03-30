package me.bechberger.jhserr.model.location;

import org.jetbrains.annotations.NotNull;

/**
 * Sealed interface describing what a register value points to or represents.
 */
public sealed interface RegisterLocation permits
        LibraryLocation,
        StackLocation,
        OopLocation,
        HeapLocation,
        MallocLocation,
        MmapLocation,
        UnknownRegisterLocation {
    @NotNull String toString();
}

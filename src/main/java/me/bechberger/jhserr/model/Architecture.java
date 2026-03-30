package me.bechberger.jhserr.model;

import org.jetbrains.annotations.NotNull;

/**
 * CPU architecture enum with detection utility.
 */
public enum Architecture {
    X86_64("x86_64", "x86-64", "amd64"),
    ARM64("arm64", "aarch64", "aarch64"),
    PPC64LE("ppc64le", "ppc64"),
    RISCV("riscv64", "riscv"),
    UNKNOWN("unknown");

    private final String[] aliases;

    Architecture(String... aliases) {
        this.aliases = aliases;
    }

    /**
     * Detect architecture from uname output or VM line.
     *
     * @param text uname output or VM info line
     * @return detected Architecture, or UNKNOWN if not recognized
     */
    public static @NotNull Architecture detect(@NotNull String text) {
        String lower = text.toLowerCase();
        for (Architecture arch : values()) {
            if (arch == UNKNOWN) continue;
            for (String alias : arch.aliases) {
                if (lower.contains(alias)) {
                    return arch;
                }
            }
        }
        return UNKNOWN;
    }
}

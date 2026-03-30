package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed umask from a PROCESS section line.
 *
 * <p>Format: {@code umask: 0022 (----w--w-)}
 */
public record UmaskInfo(@NotNull String umask, @NotNull String perms) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "umask:\\s*(\\S+)\\s+\\(([^)]+)\\)");

    @JsonCreator
    public UmaskInfo(@JsonProperty("umask") @NotNull String umask,
                     @JsonProperty("perms") @NotNull String perms) {
        this.umask = umask;
        this.perms = perms;
    }

    @Override public void accept(HsErrVisitor v) { v.visitUmaskInfo(this); }

    @Override
    public String toString() {
        return "umask: %s (%s)\n".formatted(umask, perms);
    }

    public static @Nullable UmaskInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new UmaskInfo(m.group(1), m.group(2));
    }
}

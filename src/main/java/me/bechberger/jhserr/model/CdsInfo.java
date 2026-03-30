package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CDS archive mapping info from the PROCESS section.
 *
 * <p>Mapped format: {@code CDS archive(s) mapped at: [0x...-0x...-0x...), size N, SharedBaseAddress: 0x..., ArchiveRelocationMode: N.}
 * <p>Unmapped format: {@code CDS archive(s) not mapped}
 */
public record CdsInfo(
        @JsonProperty("mapped") boolean mapped,
        @JsonProperty("base") @Nullable String base,
        @JsonProperty("staticTop") @Nullable String staticTop,
        @JsonProperty("top") @Nullable String top,
        @JsonProperty("size") long size,
        @JsonProperty("sharedBaseAddress") @Nullable String sharedBaseAddress,
        @JsonProperty("archiveRelocationMode") int archiveRelocationMode
) implements ProcessSectionItem {

    private static final Pattern MAPPED_PAT = Pattern.compile(
            "^CDS archive\\(s\\) mapped at: \\[(0x[0-9a-fA-F]+)-(0x[0-9a-fA-F]+)-(0x[0-9a-fA-F]+)\\), " +
            "size (\\d+), SharedBaseAddress: (0x[0-9a-fA-F]+), ArchiveRelocationMode: (\\d+)\\.$");

    @JsonCreator
    public CdsInfo {}

    public static @NotNull CdsInfo fromLine(@NotNull String line) {
        if (line.startsWith("CDS archive(s) not mapped")) {
            return new CdsInfo(false, null, null, null, 0, null, 0);
        }
        Matcher m = MAPPED_PAT.matcher(line);
        if (!m.matches()) {
            throw new IllegalArgumentException("Cannot parse CDS info line: " + line);
        }
        return new CdsInfo(true, m.group(1), m.group(2), m.group(3),
                Long.parseLong(m.group(4)), m.group(5), Integer.parseInt(m.group(6)));
    }

    @JsonIgnore
    public @NotNull String line() {
        if (!mapped) {
            return "CDS archive(s) not mapped";
        }
        return "CDS archive(s) mapped at: [" + base + "-" + staticTop + "-" + top + "), " +
               "size " + size + ", SharedBaseAddress: " + sharedBaseAddress +
               ", ArchiveRelocationMode: " + archiveRelocationMode + ".";
    }

    @Override public void accept(HsErrVisitor v) { v.visitCdsInfo(this); }

    @Override
    public String toString() { return line() + "\n"; }
}

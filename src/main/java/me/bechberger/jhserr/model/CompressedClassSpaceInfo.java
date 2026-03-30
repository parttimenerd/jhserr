package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compressed class space mapping info from the PROCESS section.
 *
 * <p>Format: {@code Compressed class space mapped at: 0x...-0x..., reserved size: N}
 */
public record CompressedClassSpaceInfo(
        @JsonProperty("start") @NotNull String start,
        @JsonProperty("end") @NotNull String end,
        @JsonProperty("reservedSize") long reservedSize
) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "^Compressed class space mapped at: (0x[0-9a-fA-F]+)-(0x[0-9a-fA-F]+), reserved size: (\\d+)$");

    @JsonCreator
    public CompressedClassSpaceInfo(
            @JsonProperty("start") @NotNull String start,
            @JsonProperty("end") @NotNull String end,
            @JsonProperty("reservedSize") long reservedSize) {
        this.start = Objects.requireNonNull(start, "start");
        this.end = Objects.requireNonNull(end, "end");
        this.reservedSize = reservedSize;
    }

    public static @NotNull CompressedClassSpaceInfo fromLine(@NotNull String line) {
        Matcher m = PAT.matcher(line);
        if (!m.matches()) {
            throw new IllegalArgumentException("Cannot parse compressed class space line: " + line);
        }
        return new CompressedClassSpaceInfo(m.group(1), m.group(2), Long.parseLong(m.group(3)));
    }

    @JsonIgnore
    public @NotNull String line() {
        return "Compressed class space mapped at: " + start + "-" + end + ", reserved size: " + reservedSize;
    }

    @Override public void accept(HsErrVisitor v) { v.visitCompressedClassSpaceInfo(this); }

    @Override
    public String toString() { return line() + "\n"; }
}

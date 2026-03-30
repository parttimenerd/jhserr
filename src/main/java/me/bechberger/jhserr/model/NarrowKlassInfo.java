package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Narrow klass pointer info from the PROCESS section (one line per instance).
 *
 * <p>Pointer-bits variant: {@code Narrow klass pointer bits 22, Max shift 10}
 * <p>Base variant: {@code Narrow klass base: 0x..., Narrow klass shift: N[, Narrow klass range: 0x...]}
 */
public record NarrowKlassInfo(
        @JsonProperty("pointerBits") int pointerBits,
        @JsonProperty("maxShift") int maxShift,
        @JsonProperty("base") @Nullable String base,
        @JsonProperty("shift") int shift,
        @JsonProperty("range") @Nullable String range,
        @JsonProperty("isPointerBitsLine") boolean isPointerBitsLine
) implements ProcessSectionItem {

    private static final Pattern POINTER_BITS_PAT = Pattern.compile(
            "^Narrow klass pointer bits (\\d+), Max shift (\\d+)$");
    private static final Pattern BASE_PAT = Pattern.compile(
            "^Narrow klass base: (0x[0-9a-fA-F]+), Narrow klass shift: (\\d+)(?:, Narrow klass range: (0x[0-9a-fA-F]+))?$");

    @JsonCreator
    public NarrowKlassInfo {}

    public static @NotNull NarrowKlassInfo fromLine(@NotNull String line) {
        Matcher pbm = POINTER_BITS_PAT.matcher(line);
        if (pbm.matches()) {
            return new NarrowKlassInfo(
                    Integer.parseInt(pbm.group(1)), Integer.parseInt(pbm.group(2)),
                    null, 0, null, true);
        }
        Matcher bm = BASE_PAT.matcher(line);
        if (bm.matches()) {
            return new NarrowKlassInfo(
                    0, 0,
                    bm.group(1), Integer.parseInt(bm.group(2)), bm.group(3), false);
        }
        throw new IllegalArgumentException("Cannot parse narrow klass line: " + line);
    }

    @JsonIgnore
    public @NotNull String line() {
        if (isPointerBitsLine) {
            return "Narrow klass pointer bits " + pointerBits + ", Max shift " + maxShift;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Narrow klass base: ").append(base).append(", Narrow klass shift: ").append(shift);
        if (range != null) {
            sb.append(", Narrow klass range: ").append(range);
        }
        return sb.toString();
    }

    @Override public void accept(HsErrVisitor v) { v.visitNarrowKlassInfo(this); }

    @Override
    public String toString() { return line() + "\n"; }
}

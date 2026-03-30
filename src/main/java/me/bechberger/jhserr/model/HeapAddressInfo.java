package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed heap address information from the PROCESS section.
 *
 * <p>Format: {@code Heap address: 0x..., size: N MB, Compressed Oops mode: X, Oop shift amount: N}
 */
public record HeapAddressInfo(@NotNull String address, int sizeMB,
                              @NotNull String compressedOopsMode,
                              @Nullable Integer oopShiftAmount) implements ProcessSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "Heap address:\\s*(0x[0-9a-fA-F]+),\\s*size:\\s*(\\d+)\\s*MB,\\s*Compressed Oops mode:\\s*(.+?)(?:,\\s*Oop shift amount:\\s*(\\d+))?$");

    @JsonCreator
    public HeapAddressInfo(@JsonProperty("address") @NotNull String address,
                          @JsonProperty("sizeMB") int sizeMB,
                          @JsonProperty("compressedOopsMode") @NotNull String compressedOopsMode,
                          @JsonProperty("oopShiftAmount") @Nullable Integer oopShiftAmount) {
        this.address = address;
        this.sizeMB = sizeMB;
        this.compressedOopsMode = compressedOopsMode;
        this.oopShiftAmount = oopShiftAmount;
    }

    @Override public void accept(HsErrVisitor v) { v.visitHeapAddressInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("Heap address: ").append(address)
          .append(", size: ").append(sizeMB).append(" MB")
          .append(", Compressed Oops mode: ").append(compressedOopsMode);
        if (oopShiftAmount != null) sb.append(", Oop shift amount: ").append(oopShiftAmount);
        sb.append('\n');
        return sb.toString();
    }

    public static @Nullable HeapAddressInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new HeapAddressInfo(
                m.group(1), Integer.parseInt(m.group(2)), m.group(3).strip(),
                m.group(4) != null ? Integer.parseInt(m.group(4)) : null);
    }
}

package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * Parsed signal information from the THREAD section.
 *
 * <p>Format: {@code siginfo: si_signo: 11 (SIGSEGV), si_code: 2 (SEGV_ACCERR), si_addr: 0x0000000000000000}
 */
public record SignalInfo(
        int signalNumber,
        @Nullable String signalName,
        int signalCode,
        @Nullable String signalCodeName,
        @Nullable String address
) implements ThreadSectionItem {

    private static final Pattern PAT = Pattern.compile(
            "siginfo:\\s*si_signo:\\s*(\\d+)\\s*(?:\\(([^)]+)\\))?\\s*,\\s*si_code:\\s*(\\d+)\\s*(?:\\(([^)]+)\\))?(?:\\s*,\\s*si_addr:\\s*(0x[0-9a-fA-F]+))?");

    @JsonCreator
    public SignalInfo(
            @JsonProperty("signalNumber") int signalNumber,
            @JsonProperty("signalName") @Nullable String signalName,
            @JsonProperty("signalCode") int signalCode,
            @JsonProperty("signalCodeName") @Nullable String signalCodeName,
            @JsonProperty("address") @Nullable String address) {
        this.signalNumber = signalNumber;
        this.signalName = signalName;
        this.signalCode = signalCode;
        this.signalCodeName = signalCodeName;
        this.address = address;
    }

    @Override public void accept(HsErrVisitor v) { v.visitSignalInfo(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder("siginfo: si_signo: ").append(signalNumber);
        if (signalName != null) sb.append(" (").append(signalName).append(")");
        sb.append(", si_code: ").append(signalCode);
        if (signalCodeName != null) sb.append(" (").append(signalCodeName).append(")");
        if (address != null) sb.append(", si_addr: ").append(address);
        sb.append('\n');
        return sb.toString();
    }

    public static @Nullable SignalInfo parse(@NotNull String line) {
        var m = PAT.matcher(line);
        if (!m.find()) return null;
        return new SignalInfo(
                Integer.parseInt(m.group(1)),
                m.group(2),
                Integer.parseInt(m.group(3)),
                m.group(4),
                m.group(5));
    }
}

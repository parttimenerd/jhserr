package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Named register set from the "Registers:" sub-section.
 */
public final class Registers implements ThreadSectionItem {

    private final @NotNull List<String> registerLines;
    private LinkedHashMap<String, String> parsed;

    @JsonCreator
    public Registers(@JsonProperty("registerLines") @NotNull List<String> registerLines) {
        this.registerLines = List.copyOf(registerLines);
    }

    @JsonIgnore
    public @NotNull Map<String, String> values() {
        if (parsed == null) {
            parsed = new LinkedHashMap<>();
            for (String line : registerLines) {
                for (String part : line.strip().split("\\s+")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) parsed.put(part.substring(0, eq), part.substring(eq + 1));
                }
            }
        }
        return Collections.unmodifiableMap(parsed);
    }

    @JsonIgnore
    public @Nullable String get(String name) { return values().get(name); }
    @JsonProperty public @NotNull List<String> registerLines() { return registerLines; }

    public void accept(HsErrVisitor v) { v.visitRegisters(this); }

    @Override
    public String toString() {
        if (registerLines.isEmpty()) return "";
        var sb = new StringBuilder("Registers:\n");
        for (String line : registerLines) sb.append(line).append('\n');
        return sb.toString();
    }
}

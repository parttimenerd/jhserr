package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Environment variables from the PROCESS section.
 *
 * <p>Stores variables as an ordered map (key → value), parsed eagerly from body lines.
 */
public final class EnvironmentVariables implements ProcessSectionItem {

    private final @NotNull String header;
    private final @NotNull LinkedHashMap<String, String> vars;

    @JsonCreator
    public EnvironmentVariables(@JsonProperty("header") @NotNull String header,
                                @JsonProperty("vars") @NotNull Map<String, String> vars) {
        this.header = header;
        this.vars = new LinkedHashMap<>(vars);
    }

    public static @NotNull EnvironmentVariables fromLines(@NotNull String header, @NotNull List<String> lines) {
        var vars = new LinkedHashMap<String, String>();
        for (String l : lines) {
            int eq = l.indexOf('=');
            if (eq > 0) vars.put(l.substring(0, eq), l.substring(eq + 1));
        }
        return new EnvironmentVariables(header, vars);
    }

    @JsonProperty public @NotNull String header() { return header; }
    @JsonProperty public @NotNull Map<String, String> vars() { return Collections.unmodifiableMap(vars); }

    public void accept(HsErrVisitor v) { v.visitEnvironmentVariables(this); }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(header).append('\n');
        for (var e : vars.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return sb.toString();
    }
}

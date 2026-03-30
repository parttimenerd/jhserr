package me.bechberger.jhserr.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import me.bechberger.jhserr.HsErrVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VM Mutex/Monitor ownership info from the PROCESS section.
 */
public record VmMutexInfo(
        @JsonProperty("name") @NotNull String name,
        @JsonProperty("ownerState") @NotNull String ownerState,
        @JsonProperty("entries") @NotNull List<Entry> entries
) implements ProcessSectionItem {

    private static final Pattern HEADER = Pattern.compile("^(VM Mutex/Monitor currently owned by a thread:\\s*)(.*)$");
    private static final Pattern ENTRY = Pattern.compile("^(\\[[^]]+])\\s+(.+)$");

    @JsonCreator
    public VmMutexInfo(
            @JsonProperty("name") @NotNull String name,
            @JsonProperty("ownerState") @NotNull String ownerState,
            @JsonProperty("entries") @Nullable List<Entry> entries) {
        this.name = name;
        this.ownerState = ownerState;
        this.entries = entries != null ? entries : new ArrayList<>();
    }

    public static @NotNull VmMutexInfo fromLines(@NotNull String name, @NotNull List<String> lines) {
        Matcher m = HEADER.matcher(name);
        String headerPrefix = name;
        String ownerState = "";
        if (m.matches()) {
            headerPrefix = m.group(1);
            ownerState = m.group(2);
        }

        List<Entry> entries = new ArrayList<>();
        for (String line : lines) {
            Matcher em = ENTRY.matcher(line);
            if (em.matches()) {
                entries.add(new Entry(em.group(1), em.group(2), em.group(0).substring(em.group(1).length(), em.group(0).indexOf(em.group(2)))));
            } else {
                entries.add(new Entry("", line, ""));
            }
        }
        return new VmMutexInfo(headerPrefix, ownerState, entries);
    }

    @JsonIgnore
    public @NotNull List<String> lines() {
        List<String> out = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            if (entry.address().isEmpty()) {
                out.add(entry.description());
            } else {
                out.add(entry.address() + entry.spacesAfterAddress() + entry.description());
            }
        }
        return out;
    }

    @Override
    public @NotNull String toString() {
        var sb = new StringBuilder();
        sb.append(name).append(ownerState).append('\n');
        for (String line : lines()) sb.append(line).append('\n');
        return sb.toString();
    }

    @Override
    public void accept(HsErrVisitor v) {
        v.visitVmMutexInfo(this);
    }

    public record Entry(
            @JsonProperty("address") @NotNull String address,
            @JsonProperty("description") @NotNull String description,
            @JsonProperty("spacesAfterAddress") @NotNull String spacesAfterAddress
    ) {}
}

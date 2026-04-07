package me.bechberger.jhserr.model;

import me.bechberger.jhserr.HsErrVisitor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Polling page address from the PROCESS section.
 *
 * <p>Format: {@code Polling page: 0x00000001042c0000}
 */
public record PollingPageInfo(
        @JsonProperty("prefix") @NotNull String prefix,
        @JsonProperty("address") @NotNull String address
) implements ProcessSectionItem {

    private static final String POLLING_PAGE_PREFIX = "Polling page: ";

    @JsonCreator
    public PollingPageInfo(
            @JsonProperty("prefix") @NotNull String prefix,
            @JsonProperty("address") @NotNull String address) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        this.address = Objects.requireNonNull(address, "address");
    }

    public static @NotNull PollingPageInfo fromLine(@NotNull String line) {
        if (!line.startsWith(POLLING_PAGE_PREFIX)) {
            throw new IllegalArgumentException("Not a polling page line: " + line);
        }
        int colonPos = line.indexOf(':');
        String prefix = line.substring(0, colonPos + 1);
        String rest = line.substring(colonPos + 1);
        // Preserve whitespace between colon and address
        int addrStart = 0;
        while (addrStart < rest.length() && rest.charAt(addrStart) == ' ') addrStart++;
        prefix = prefix + rest.substring(0, addrStart);
        String address = rest.substring(addrStart);
        return new PollingPageInfo(prefix, address);
    }

    @JsonIgnore
    public @NotNull String line() {
        return prefix + address;
    }

    @Override public void accept(HsErrVisitor v) { v.visitPollingPageInfo(this); }

    @Override
    public String toString() { return line() + "\n"; }
}

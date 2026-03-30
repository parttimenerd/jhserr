package me.bechberger.jhserr.model.location;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Register points into a library/DSO at an absolute address.
 * Example: 0x00007fff92c1a4a0: <offset 0x0000000001c1a4a0> in /path/to/libjvm.so at 0x00007fff91000000
 */
public record LibraryLocation(
        @JsonProperty("address") @NotNull String address,
        @JsonProperty("offset") @NotNull String offset,
        @JsonProperty("libraryPath") @NotNull String libraryPath,
        @JsonProperty("baseAddress") @NotNull String baseAddress
) implements RegisterLocation {
    @Override
    public String toString() {
        return address + ": <offset " + offset + "> in " + libraryPath + " at " + baseAddress;
    }
}

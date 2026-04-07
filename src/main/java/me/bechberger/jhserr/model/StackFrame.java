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
 * A stack frame from native or Java frame listings.
 */
public final class StackFrame {

    public enum Type {
        VM('V'), STUB('v'), NATIVE('C'), COMPILED_JAVA('J'), INTERPRETED_JAVA('j'), OTHER('A');

        private final char code;
        Type(char code) { this.code = code; }
        public char code() { return code; }

        public static Type fromCode(char c) {
            for (Type t : values()) if (t.code == c) return t;
            return OTHER;
        }
    }

    /** Parsed body for V/C native frames: [lib+0xOFF]  function(args)+0xOFF */
    public record NativeBody(
        @NotNull String library,
        @NotNull String libraryOffset,
        @NotNull String spacingAfterLib,
        @NotNull String function,
        @Nullable String functionOffset
    ) {
        @Override public String toString() {
            var sb = new StringBuilder();
            sb.append('[').append(library).append('+').append(libraryOffset).append(']');
            sb.append(spacingAfterLib).append(function);
            if (functionOffset != null) sb.append('+').append(functionOffset);
            return sb.toString();
        }
    }

    /** Pattern to parse native frame body: [lib+0xOFF]<spacing>function+0xOFF */
    private static final Pattern NATIVE_BODY_PAT = Pattern.compile(
        "^\\[([^+\\]]+)\\+(0x[0-9a-fA-F]+)\\](\\s+)(.*?)(?:\\+(0x[0-9a-fA-F]+))?$");

    private final @NotNull String leadingWhitespace;
    private final char marker;
    private final @NotNull String spacingAfterMarker;
    private final @NotNull String body;
    private final @NotNull Type type;
    private final @Nullable NativeBody nativeBody;

    @JsonCreator
    public StackFrame(@JsonProperty("leadingWhitespace") @NotNull String leadingWhitespace,
                      @JsonProperty("marker") char marker,
                      @JsonProperty("spacingAfterMarker") @NotNull String spacingAfterMarker,
                      @JsonProperty("body") @NotNull String body,
                      @JsonProperty("type") @NotNull Type type,
                      @JsonProperty("nativeBody") @Nullable NativeBody nativeBody) {
        this.leadingWhitespace = leadingWhitespace;
        this.marker = marker;
        this.spacingAfterMarker = spacingAfterMarker;
        this.body = body;
        this.type = type;
        this.nativeBody = nativeBody;
    }

    public StackFrame(@NotNull String leadingWhitespace,
                      char marker,
                      @NotNull String spacingAfterMarker,
                      @NotNull String body,
                      @NotNull Type type) {
        this(leadingWhitespace, marker, spacingAfterMarker, body, type, parseNativeBody(body, type));
    }

    /**
     * Backward-compatible constructor from a full line.
     */
    public StackFrame(@NotNull String line, @NotNull Type type) {
        ParsedLine parsed = parseLine(line);
        this.leadingWhitespace = parsed.leadingWhitespace;
        this.marker = parsed.marker;
        this.spacingAfterMarker = parsed.spacingAfterMarker;
        this.body = parsed.body;
        this.type = type;
        this.nativeBody = parseNativeBody(this.body, type);
    }

    private static @Nullable NativeBody parseNativeBody(String body, Type type) {
        if (type != Type.VM && type != Type.NATIVE) return null;
        Matcher m = NATIVE_BODY_PAT.matcher(body);
        if (!m.matches()) return null;
        return new NativeBody(m.group(1), m.group(2), m.group(3), m.group(4), m.group(5));
    }

    @JsonProperty public @NotNull String leadingWhitespace() { return leadingWhitespace; }
    @JsonProperty public char marker() { return marker; }
    @JsonProperty public @NotNull String spacingAfterMarker() { return spacingAfterMarker; }
    @JsonProperty public @NotNull String body() { return body; }
    @JsonProperty public @NotNull Type type() { return type; }
    @JsonProperty public @Nullable NativeBody nativeBody() { return nativeBody; }

    @JsonIgnore
    public @NotNull String line() {
        return leadingWhitespace + marker + spacingAfterMarker + body;
    }

    public void accept(HsErrVisitor v) { v.visitFrame(this); }

    @Override
    public String toString() { return line(); }

    private record ParsedLine(String leadingWhitespace, char marker, String spacingAfterMarker, String body) {}

    private static ParsedLine parseLine(@NotNull String line) {
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) i++;
        String leading = line.substring(0, i);

        if (i >= line.length()) {
            return new ParsedLine(leading, ' ', "", "");
        }

        char marker = line.charAt(i);
        int j = i + 1;
        while (j < line.length() && Character.isWhitespace(line.charAt(j))) j++;
        String spacing = line.substring(i + 1, j);
        String body = line.substring(j);
        return new ParsedLine(leading, marker, spacing, body);
    }
}

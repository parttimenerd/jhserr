package me.bechberger.jhserr.transform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Configuration for {@link RedactionTransformer}.
 *
 * <p>Every redaction category can be individually enabled/disabled.
 * Supports JSON serialization/deserialization for config files.
 *
 * <p>Categories (all enabled by default):
 * <ul>
 *   <li>{@link #redactUsernames} — usernames discovered in paths (/Users/X/, /home/X/)</li>
 *   <li>{@link #redactHostnames} — hostname from Summary host line and uname</li>
 *   <li>{@link #redactPids} — PIDs and thread IDs from header</li>
 *   <li>{@link #redactEnvVars} — environment variable values (except safe list)</li>
 *   <li>{@link #redactPaths} — absolute paths (optionally keep filename)</li>
 *   <li>{@link #redactThreadNames} — thread names</li>
 *   <li>{@link #redactIpAddresses} — IP addresses found in text</li>
 *   <li>{@link #removeDynamicLibraries} — remove the entire dynamic libraries section</li>
 *   <li>{@link #removeEventLogs} — remove event logs</li>
 * </ul>
 *
 * <p>Configurable sensitive strings:
 * <ul>
 *   <li>{@link #additionalUsernames} — extra usernames to redact besides auto-discovered</li>
 *   <li>{@link #additionalHostnames} — extra hostnames to redact besides auto-discovered</li>
 *   <li>{@link #sensitivePathPrefixes} — folder paths to replace (longest match first)</li>
 *   <li>{@link #sensitiveStrings} — arbitrary strings to replace everywhere</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class RedactionConfig {

    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // ── redaction category toggles ───────────────────────────────────────

    @JsonProperty private boolean redactUsernames = true;
    @JsonProperty private boolean redactHostnames = true;
    @JsonProperty private boolean redactPids = true;
    @JsonProperty private boolean redactEnvVars = true;
    @JsonProperty private boolean redactPaths = true;
    @JsonProperty private boolean redactThreadNames = false;
    @JsonProperty private boolean redactIpAddresses = true;
    @JsonProperty private boolean removeDynamicLibraries = false;
    @JsonProperty private boolean removeEventLogs = false;

    // ── path redaction mode ──────────────────────────────────────────────

    public enum PathMode {
        /** Replace the directory part, keep filename: /Users/X/code/Main.java → &lt;path&gt;/Main.java */
        KEEP_FILENAME,
        /** Replace the entire path */
        REDACT_ALL,
        /** Don't touch paths at all (still redacts username folder) */
        KEEP_ALL
    }

    @JsonProperty private PathMode pathMode = PathMode.KEEP_FILENAME;

    // ── replacement text ─────────────────────────────────────────────────

    @JsonProperty private String redactionText = "***";
    @JsonProperty private String userPlaceholder = "REDACTED_USER";
    @JsonProperty private String hostPlaceholder = "REDACTED_HOST";
    @JsonProperty private String pathPlaceholder = "<redacted-path>";
    @JsonProperty private String pidPlaceholder = "0";
    @JsonProperty private String tidPlaceholder = "0";

    // ── configurable sensitive strings ───────────────────────────────────

    /** Extra usernames to redact (in addition to auto-discovered from paths). */
    @JsonProperty private final List<String> additionalUsernames = new ArrayList<>();

    /** Extra hostnames to redact (in addition to auto-discovered from Summary/uname). */
    @JsonProperty private final List<String> additionalHostnames = new ArrayList<>();

    /**
     * Absolute directory prefixes that should be replaced entirely.
     * If a path starts with one of these, the matching prefix is replaced.
     * Parent directories are also redacted automatically.
     */
    @JsonProperty private final List<String> sensitivePathPrefixes = new ArrayList<>();

    /** Arbitrary strings to replace everywhere with {@link #redactionText}. */
    @JsonProperty private final List<String> sensitiveStrings = new ArrayList<>();

    // ── safe env vars (kept unredacted) ──────────────────────────────────

    @JsonProperty private final Set<String> safeEnvVars = new LinkedHashSet<>(Set.of(
            "PATH", "SHELL", "LANG", "LC_ALL", "LC_CTYPE", "TERM", "DISPLAY",
            "JAVA_HOME", "JDK_HOME", "JAVA_TOOL_OPTIONS",
            "CLASSPATH", "_JAVA_OPTIONS"));

    // ── IP address redaction ─────────────────────────────────────────────

    static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b");

    // ── construction ─────────────────────────────────────────────────────

    public RedactionConfig() {}

    /** Copy constructor. */
    public RedactionConfig(RedactionConfig other) {
        this.redactUsernames = other.redactUsernames;
        this.redactHostnames = other.redactHostnames;
        this.redactPids = other.redactPids;
        this.redactEnvVars = other.redactEnvVars;
        this.redactPaths = other.redactPaths;
        this.redactThreadNames = other.redactThreadNames;
        this.redactIpAddresses = other.redactIpAddresses;
        this.removeDynamicLibraries = other.removeDynamicLibraries;
        this.removeEventLogs = other.removeEventLogs;
        this.pathMode = other.pathMode;
        this.redactionText = other.redactionText;
        this.userPlaceholder = other.userPlaceholder;
        this.hostPlaceholder = other.hostPlaceholder;
        this.pathPlaceholder = other.pathPlaceholder;
        this.pidPlaceholder = other.pidPlaceholder;
        this.tidPlaceholder = other.tidPlaceholder;
        this.additionalUsernames.addAll(other.additionalUsernames);
        this.additionalHostnames.addAll(other.additionalHostnames);
        this.sensitivePathPrefixes.addAll(other.sensitivePathPrefixes);
        this.sensitiveStrings.addAll(other.sensitiveStrings);
        this.safeEnvVars.clear();
        this.safeEnvVars.addAll(other.safeEnvVars);
    }

    // ── JSON serialization ───────────────────────────────────────────────

    /** Serialize this config to a JSON string. */
    public String toJson() throws JsonProcessingException {
        return CONFIG_MAPPER.writeValueAsString(this);
    }

    /** Write this config to a JSON file. */
    public void toJsonFile(Path path) throws IOException {
        CONFIG_MAPPER.writeValue(path.toFile(), this);
    }

    /** Deserialize a config from a JSON string. */
    public static RedactionConfig fromJson(String json) throws JsonProcessingException {
        return CONFIG_MAPPER.readValue(json, RedactionConfig.class);
    }

    /** Read a config from a JSON file. */
    public static RedactionConfig fromJsonFile(Path path) throws IOException {
        return CONFIG_MAPPER.readValue(path.toFile(), RedactionConfig.class);
    }

    // ── getters ──────────────────────────────────────────────────────────

    public boolean redactUsernames()         { return redactUsernames; }
    public boolean redactHostnames()         { return redactHostnames; }
    public boolean redactPids()              { return redactPids; }
    public boolean redactEnvVars()           { return redactEnvVars; }
    public boolean redactPaths()             { return redactPaths; }
    public boolean redactThreadNames()       { return redactThreadNames; }
    public boolean redactIpAddresses()       { return redactIpAddresses; }
    public boolean removeDynamicLibraries()  { return removeDynamicLibraries; }
    public boolean removeEventLogs()         { return removeEventLogs; }
    public PathMode pathMode()               { return pathMode; }
    public String redactionText()            { return redactionText; }
    public String userPlaceholder()          { return userPlaceholder; }
    public String hostPlaceholder()          { return hostPlaceholder; }
    public String pathPlaceholder()          { return pathPlaceholder; }
    public String pidPlaceholder()           { return pidPlaceholder; }
    public String tidPlaceholder()           { return tidPlaceholder; }
    public List<String> additionalUsernames()   { return Collections.unmodifiableList(additionalUsernames); }
    public List<String> additionalHostnames()   { return Collections.unmodifiableList(additionalHostnames); }
    public List<String> sensitivePathPrefixes() { return Collections.unmodifiableList(sensitivePathPrefixes); }
    public List<String> sensitiveStrings()      { return Collections.unmodifiableList(sensitiveStrings); }
    public Set<String> safeEnvVars()         { return Collections.unmodifiableSet(safeEnvVars); }

    // ── fluent setters ───────────────────────────────────────────────────

    public RedactionConfig setRedactUsernames(boolean v)        { this.redactUsernames = v; return this; }
    public RedactionConfig setRedactHostnames(boolean v)        { this.redactHostnames = v; return this; }
    public RedactionConfig setRedactPids(boolean v)             { this.redactPids = v; return this; }
    public RedactionConfig setRedactEnvVars(boolean v)          { this.redactEnvVars = v; return this; }
    public RedactionConfig setRedactPaths(boolean v)            { this.redactPaths = v; return this; }
    public RedactionConfig setRedactThreadNames(boolean v)      { this.redactThreadNames = v; return this; }
    public RedactionConfig setRedactIpAddresses(boolean v)      { this.redactIpAddresses = v; return this; }
    public RedactionConfig setRemoveDynamicLibraries(boolean v) { this.removeDynamicLibraries = v; return this; }
    public RedactionConfig setRemoveEventLogs(boolean v)        { this.removeEventLogs = v; return this; }
    public RedactionConfig setPathMode(PathMode v)              { this.pathMode = v; return this; }
    public RedactionConfig setRedactionText(String v)           { this.redactionText = v; return this; }
    public RedactionConfig setUserPlaceholder(String v)         { this.userPlaceholder = v; return this; }
    public RedactionConfig setHostPlaceholder(String v)         { this.hostPlaceholder = v; return this; }
    public RedactionConfig setPathPlaceholder(String v)         { this.pathPlaceholder = v; return this; }
    public RedactionConfig setPidPlaceholder(String v)          { this.pidPlaceholder = v; return this; }
    public RedactionConfig setTidPlaceholder(String v)          { this.tidPlaceholder = v; return this; }

    public RedactionConfig addAdditionalUsername(String username) {
        additionalUsernames.add(username);
        return this;
    }

    public RedactionConfig addAdditionalHostname(String hostname) {
        additionalHostnames.add(hostname);
        return this;
    }

    public RedactionConfig addSensitivePathPrefix(String prefix) {
        sensitivePathPrefixes.add(prefix);
        return this;
    }

    public RedactionConfig addSensitiveString(String s) {
        sensitiveStrings.add(s);
        return this;
    }

    public RedactionConfig addSafeEnvVar(String name) {
        safeEnvVars.add(name);
        return this;
    }

    public RedactionConfig removeSafeEnvVar(String name) {
        safeEnvVars.remove(name);
        return this;
    }

    /** Preset: only redact usernames and hostnames. */
    public static RedactionConfig minimal() {
        return new RedactionConfig()
                .setRedactPids(false)
                .setRedactEnvVars(false)
                .setRedactPaths(false)
                .setRedactIpAddresses(false);
    }

    /** Preset: aggressive — also remove libraries and events. */
    public static RedactionConfig aggressive() {
        return new RedactionConfig()
                .setRedactThreadNames(true)
                .setRemoveDynamicLibraries(true)
                .setRemoveEventLogs(true);
    }
}

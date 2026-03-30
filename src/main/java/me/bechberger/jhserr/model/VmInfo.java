package me.bechberger.jhserr.model;
import me.bechberger.jhserr.HsErrVisitor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The {@code vm_info:} line from the SYSTEM section.
 *
 * <p>Fully parsed VM build information. Example:
 * <pre>
 * vm_info: OpenJDK 64-Bit Server VM (22-internal-adhoc.testuser.jdk) for bsd-aarch64 JRE (...), built on ... by "testuser" with ...
 * </pre>
 */
public record VmInfo(
        @JsonProperty("prefix") @NotNull String prefix,
        @JsonProperty("productName") @NotNull String productName,
        @JsonProperty("vmBuild") @NotNull String vmBuild,
        @JsonProperty("targetPlatform") @NotNull String targetPlatform,
        @JsonProperty("jreBuild") @NotNull String jreBuild,
        @JsonProperty("builtOn") @NotNull String builtOn,
        @JsonProperty("builderName") @Nullable String builderName,
        @JsonProperty("toolchain") @NotNull String toolchain
) implements SystemSectionItem {

    private static final String VM_INFO_PREFIX = "vm_info:";
    private static final String SEP_AFTER_PRODUCT = " (";
    private static final String SEP_AFTER_VM_BUILD = ") for ";
    private static final String SEP_AFTER_PLATFORM = " JRE (";
    private static final String SEP_AFTER_JRE_BUILD = "), built on ";
    private static final String SEP_BUILT_ON_WITH = " with ";
    private static final String SEP_BUILT_ON_BY = " by \"";
    private static final String SEP_AFTER_BUILDER = "\" with ";

    @JsonCreator
    public VmInfo {
        prefix = Objects.requireNonNull(prefix, "prefix");
        productName = Objects.requireNonNull(productName, "productName");
        vmBuild = Objects.requireNonNull(vmBuild, "vmBuild");
        targetPlatform = Objects.requireNonNull(targetPlatform, "targetPlatform");
        jreBuild = Objects.requireNonNull(jreBuild, "jreBuild");
        builtOn = Objects.requireNonNull(builtOn, "builtOn");
        toolchain = Objects.requireNonNull(toolchain, "toolchain");
    }

    public static @NotNull VmInfo fromLine(@NotNull String line) {
        int prefixEnd = line.indexOf(VM_INFO_PREFIX);
        if (prefixEnd != 0) {
            throw new IllegalArgumentException("Invalid vm_info line: " + line);
        }
        int afterColon = VM_INFO_PREFIX.length();
        while (afterColon < line.length() && Character.isWhitespace(line.charAt(afterColon))) {
            afterColon++;
        }
        String prefix = line.substring(0, afterColon);
        String payload = line.substring(afterColon);

        int productSep = payload.indexOf(SEP_AFTER_PRODUCT);
        if (productSep < 0) {
            throw new IllegalArgumentException("Cannot parse vm_info product segment: " + line);
        }
        String productName = payload.substring(0, productSep);
        String rest = payload.substring(productSep + SEP_AFTER_PRODUCT.length());

        int vmBuildSep = rest.indexOf(SEP_AFTER_VM_BUILD);
        if (vmBuildSep < 0) {
            throw new IllegalArgumentException("Cannot parse vm_info build segment: " + line);
        }
        String vmBuild = rest.substring(0, vmBuildSep);
        rest = rest.substring(vmBuildSep + SEP_AFTER_VM_BUILD.length());

        int platformSep = rest.indexOf(SEP_AFTER_PLATFORM);
        if (platformSep < 0) {
            throw new IllegalArgumentException("Cannot parse vm_info platform segment: " + line);
        }
        String platform = rest.substring(0, platformSep);
        rest = rest.substring(platformSep + SEP_AFTER_PLATFORM.length());

        int jreSep = rest.indexOf(SEP_AFTER_JRE_BUILD);
        if (jreSep < 0) {
            throw new IllegalArgumentException("Cannot parse vm_info JRE segment: " + line);
        }
        String jreBuild = rest.substring(0, jreSep);
        rest = rest.substring(jreSep + SEP_AFTER_JRE_BUILD.length());

        int bySep = rest.indexOf(SEP_BUILT_ON_BY);
        int withSep = rest.indexOf(SEP_BUILT_ON_WITH);
        if (withSep < 0) {
            throw new IllegalArgumentException("Cannot parse vm_info toolchain segment: " + line);
        }

        String builtOn;
        String builderName = null;
        String toolchain;

        if (bySep >= 0 && bySep < withSep) {
            builtOn = rest.substring(0, bySep);

            String afterBy = rest.substring(bySep + SEP_BUILT_ON_BY.length());
            int builderSep = afterBy.indexOf(SEP_AFTER_BUILDER);
            if (builderSep < 0) {
                throw new IllegalArgumentException("Cannot parse vm_info builder segment: " + line);
            }
            builderName = afterBy.substring(0, builderSep);
            toolchain = afterBy.substring(builderSep + SEP_AFTER_BUILDER.length());
        } else {
            builtOn = rest.substring(0, withSep);
            toolchain = rest.substring(withSep + SEP_BUILT_ON_WITH.length());
        }

        return new VmInfo(
                prefix,
                productName,
                vmBuild,
                platform,
                jreBuild,
                builtOn,
                builderName,
                toolchain
        );
    }

    @Override public void accept(HsErrVisitor v) { v.visitVmInfo(this); }

    @JsonIgnore
    public @NotNull String line() {
        StringBuilder out = new StringBuilder();
        out.append(prefix)
                .append(productName)
                .append(SEP_AFTER_PRODUCT)
                .append(vmBuild)
                .append(SEP_AFTER_VM_BUILD)
                .append(targetPlatform)
                .append(SEP_AFTER_PLATFORM)
                .append(jreBuild)
                .append(SEP_AFTER_JRE_BUILD)
                .append(builtOn);
        if (builderName != null) {
            out.append(SEP_BUILT_ON_BY)
               .append(builderName)
               .append(SEP_AFTER_BUILDER);
        } else {
            out.append(SEP_BUILT_ON_WITH);
        }
        out.append(toolchain);
        return out.toString();
    }

    @JsonIgnore public @Nullable String vmVersion() {
        return line().startsWith(VM_INFO_PREFIX) ? line().substring(prefix.length()) : null;
    }

    @JsonIgnore public @Nullable String vmName() {
        return productName;
    }

    @JsonIgnore public @Nullable String builtBy() {
        return builderName;
    }

    @JsonIgnore public @Nullable String platform() {
        return targetPlatform;
    }

    @Override
    public String toString() { return line() + "\n"; }
}

package me.bechberger.jhserr.model;

/**
 * Marker for items that can appear in a {@link SystemSection}.
 */
public sealed interface SystemSectionItem extends SectionItem permits
        BlankLine,
        NamedSection,
        OsInfo,
        CpuInfo,
        MemoryInfo,
        VmInfo {
}

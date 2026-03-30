package me.bechberger.jhserr.model;

/**
 * Marker for items that can appear in a {@link ThreadSection}.
 */
public sealed interface ThreadSectionItem extends SectionItem permits
        BlankLine,
        NamedSection,
        ThreadInfo,
        FrameList,
        ErrorDuringError,
        Registers,
        SignalInfo,
        StackBoundsInfo,
        RegisterMemoryMapping,
        TopOfStack {
}

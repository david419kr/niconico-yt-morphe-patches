package io.github.david419kr.niconicoyt.extension.niconicocomments;

/** Immutable subset of a YouTube live-chat item needed by the overlay. */
final class NiconicoComment {
    final String id;
    final String text;
    final boolean membership;
    final int backgroundColor;
    final long videoOffsetMs;

    NiconicoComment(
            String id,
            String text,
            boolean membership,
            int backgroundColor,
            long videoOffsetMs
    ) {
        this.id = id;
        this.text = text;
        this.membership = membership;
        this.backgroundColor = backgroundColor;
        this.videoOffsetMs = videoOffsetMs;
    }
}

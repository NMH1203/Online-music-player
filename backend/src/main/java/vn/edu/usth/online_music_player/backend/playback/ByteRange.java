package vn.edu.usth.online_music_player.backend.playback;

/** A validated inclusive HTTP byte range. */
record ByteRange(long start, long end, boolean partial) {
    long length() {
        return end - start + 1;
    }

    static ByteRange full(long totalBytes) {
        return new ByteRange(0, totalBytes - 1, false);
    }

    static ByteRange parse(String header, long totalBytes) {
        if (totalBytes < 1) {
            throw new InvalidByteRangeException();
        }
        if (header == null || header.isBlank()) {
            return full(totalBytes);
        }

        int equalsIndex = header.indexOf('=');
        if (equalsIndex < 0 || !header.substring(0, equalsIndex).equalsIgnoreCase("bytes")) {
            throw new InvalidByteRangeException();
        }
        String specifier = header.substring(equalsIndex + 1);
        if (specifier.isBlank() || specifier.contains(",")) {
            throw new InvalidByteRangeException();
        }

        int dashIndex = specifier.indexOf('-');
        if (dashIndex < 0 || dashIndex != specifier.lastIndexOf('-')) {
            throw new InvalidByteRangeException();
        }
        String first = specifier.substring(0, dashIndex);
        String last = specifier.substring(dashIndex + 1);
        try {
            long start;
            long end;
            if (first.isEmpty()) {
                long suffixLength = Long.parseLong(last);
                if (suffixLength <= 0) {
                    throw new InvalidByteRangeException();
                }
                start = Math.max(totalBytes - suffixLength, 0);
                end = totalBytes - 1;
            } else {
                start = Long.parseLong(first);
                end = last.isEmpty() ? totalBytes - 1 : Long.parseLong(last);
                if (start < 0 || end < start || start >= totalBytes) {
                    throw new InvalidByteRangeException();
                }
                end = Math.min(end, totalBytes - 1);
            }
            return new ByteRange(start, end, true);
        } catch (NumberFormatException exception) {
            throw new InvalidByteRangeException();
        }
    }
}

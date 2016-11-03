package com.yalin.exoplayer.extractor;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public interface SeekMap {
    final class Unseekable implements SeekMap {
        private final long durationUs;

        public Unseekable(long durationUs) {
            this.durationUs = durationUs;
        }

        @Override
        public boolean isSeekable() {
            return false;
        }

        @Override
        public long getDurationUs() {
            return durationUs;
        }

        @Override
        public long getPosition(long timeUs) {
            return 0;
        }
    }

    boolean isSeekable();

    long getDurationUs();

    long getPosition(long timeUs);
}

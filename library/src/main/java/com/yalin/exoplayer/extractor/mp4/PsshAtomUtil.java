package com.yalin.exoplayer.extractor.mp4;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class PsshAtomUtil {
    private static final String TAG = "PsshAtomUtil";

    private PsshAtomUtil() {
    }

    public static byte[] buildPsshAtom(UUID uuid, byte[] data) {
        int psshBoxLength = Atom.FULL_HEADER_SIZE + 16 + 4 + data.length;
        ByteBuffer pssBox = ByteBuffer.allocate(psshBoxLength);
        pssBox.putInt(psshBoxLength);
        pssBox.putInt(Atom.TYPE_pssh);
        pssBox.putInt(0);
        pssBox.putLong(uuid.getMostSignificantBits());
        pssBox.putLong(uuid.getLeastSignificantBits());
        pssBox.putInt(data.length);
        pssBox.put(data);
        return pssBox.array();
    }
}

package com.yalin.exoplayer.extractor.mp4;

import android.util.Log;
import android.util.Pair;

import com.yalin.exoplayer.util.ParsableByteArray;

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

    public static byte[] parseSchemeSpecificData(byte[] atom, UUID uuid) {
        Pair<UUID, byte[]> parsedAtom = parsePsshAtom(atom);
        if (parsedAtom == null) {
            return null;
        }
        if (uuid != null && !uuid.equals(parsedAtom.first)) {
            Log.w(TAG, "UUID mismatch. Expected: " + uuid + ", got: " + parsedAtom.first + ".");
            return null;
        }
        return parsedAtom.second;
    }

    private static Pair<UUID, byte[]> parsePsshAtom(byte[] atom) {
        ParsableByteArray atomData = new ParsableByteArray(atom);
        if (atomData.limit() < Atom.FULL_HEADER_SIZE + 16 /* UUID */ + 4 /* DataSize */) {
            // Data too short.
            return null;
        }
        atomData.setPosition(0);
        int atomSize = atomData.readInt();
        if (atomSize != atomData.bytesLeft() + 4) {
            // Not an atom, or incorrect atom size.
            return null;
        }
        int atomType = atomData.readInt();
        if (atomType != Atom.TYPE_pssh) {
            // Not an atom, or incorrect atom type.
            return null;
        }
        int atomVersion = Atom.parseFullAtomVersion(atomData.readInt());
        if (atomVersion > 1) {
            Log.w(TAG, "Unsupported pssh version: " + atomVersion);
            return null;
        }
        UUID uuid = new UUID(atomData.readLong(), atomData.readLong());
        if (atomVersion == 1) {
            int keyIdCount = atomData.readUnsignedIntToInt();
            atomData.skipBytes(16 * keyIdCount);
        }
        int dataSize = atomData.readUnsignedIntToInt();
        if (dataSize != atomData.bytesLeft()) {
            // Incorrect dataSize.
            return null;
        }
        byte[] data = new byte[dataSize];
        atomData.readBytes(data, 0, dataSize);
        return Pair.create(uuid, data);
    }
}


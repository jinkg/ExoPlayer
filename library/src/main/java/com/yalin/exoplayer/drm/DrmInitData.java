package com.yalin.exoplayer.drm;

import android.os.Parcel;
import android.os.Parcelable;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.util.Assertions;

import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public final class DrmInitData implements Comparator<DrmInitData.SchemeData>, Parcelable {

    private final SchemeData[] schemeDatas;

    private int hashCode;

    public final int schemeDataCount;

    public DrmInitData(SchemeData... schemeDatas) {
        this(true, schemeDatas);
    }

    public DrmInitData(boolean cloneSchemeDatas, SchemeData... schemeDatas) {
        if (cloneSchemeDatas) {
            schemeDatas = schemeDatas.clone();
        }

        Arrays.sort(schemeDatas, this);

        for (int i = 1; i < schemeDatas.length; i++) {
            if (schemeDatas[i - 1].uuid.equals(schemeDatas[i].uuid)) {
                throw new IllegalArgumentException("Duplicate data for uuid: " + schemeDatas[i].uuid);
            }
        }
        this.schemeDatas = schemeDatas;
        schemeDataCount = schemeDatas.length;
    }

    DrmInitData(Parcel in) {
        schemeDatas = in.createTypedArray(SchemeData.CREATOR);
        schemeDataCount = in.readInt();
    }

    @Override
    public int compare(SchemeData o1, SchemeData o2) {
        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedArray(schemeDatas, flags);
        dest.writeInt(schemeDataCount);
    }

    public SchemeData get(int index) {
        return schemeDatas[index];
    }

    public SchemeData get(UUID uuid) {
        for (SchemeData schemeData : schemeDatas) {
            if (schemeData.matches(uuid)) {
                return schemeData;
            }
        }
        return null;
    }

    public static final Creator<DrmInitData> CREATOR = new Creator<DrmInitData>() {
        @Override
        public DrmInitData createFromParcel(Parcel source) {
            return new DrmInitData(source);
        }

        @Override
        public DrmInitData[] newArray(int size) {
            return new DrmInitData[size];
        }
    };

    public static final class SchemeData implements Parcelable {
        private int hashCode;

        private final UUID uuid;

        public final String mimeType;

        public final byte[] data;

        public final boolean requiresSecureDecryption;

        public SchemeData(UUID uuid, String mimeType, byte[] data) {
            this(uuid, mimeType, data, false);
        }

        public SchemeData(UUID uuid, String mimeType, byte[] data, boolean requiresSecureDecryption) {
            this.uuid = Assertions.checkNotNull(uuid);
            this.mimeType = Assertions.checkNotNull(mimeType);
            this.data = Assertions.checkNotNull(data);
            this.requiresSecureDecryption = requiresSecureDecryption;
        }

        public boolean matches(UUID schemeUuid) {
            return C.UUID_NIL.equals(uuid) || schemeUuid.equals(uuid);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.hashCode);
            dest.writeSerializable(this.uuid);
            dest.writeString(this.mimeType);
            dest.writeByteArray(this.data);
            dest.writeByte(this.requiresSecureDecryption ? (byte) 1 : (byte) 0);
        }

        protected SchemeData(Parcel in) {
            this.hashCode = in.readInt();
            this.uuid = (UUID) in.readSerializable();
            this.mimeType = in.readString();
            this.data = in.createByteArray();
            this.requiresSecureDecryption = in.readByte() != 0;
        }

        public static final Creator<SchemeData> CREATOR = new Creator<SchemeData>() {
            @Override
            public SchemeData createFromParcel(Parcel source) {
                return new SchemeData(source);
            }

            @Override
            public SchemeData[] newArray(int size) {
                return new SchemeData[size];
            }
        };
    }

}

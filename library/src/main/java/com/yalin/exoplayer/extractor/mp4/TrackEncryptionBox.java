package com.yalin.exoplayer.extractor.mp4;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public final class TrackEncryptionBox {


    /**
     * Indicates the encryption state of the samples in the sample group.
     */
    public final boolean isEncrypted;

    /**
     * The initialization vector size in bytes for the samples in the corresponding sample group.
     */
    public final int initializationVectorSize;

    /**
     * The key identifier for the samples in the corresponding sample group.
     */
    public final byte[] keyId;

    /**
     * @param isEncrypted              Indicates the encryption state of the samples in the sample group.
     * @param initializationVectorSize The initialization vector size in bytes for the samples in the
     *                                 corresponding sample group.
     * @param keyId                    The key identifier for the samples in the corresponding sample group.
     */
    public TrackEncryptionBox(boolean isEncrypted, int initializationVectorSize, byte[] keyId) {
        this.isEncrypted = isEncrypted;
        this.initializationVectorSize = initializationVectorSize;
        this.keyId = keyId;
    }
}

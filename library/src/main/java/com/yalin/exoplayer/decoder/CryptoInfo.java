package com.yalin.exoplayer.decoder;

import android.annotation.TargetApi;
import android.media.MediaCodec;

import com.yalin.exoplayer.C;
import com.yalin.exoplayer.util.Util;

/**
 * 作者：YaLin
 * 日期：2016/11/1.
 */

public final class CryptoInfo {
    public byte[] iv;

    public byte[] key;

    @C.CryptoMode
    public int mode;

    public int[] numBytesOfClearData;

    public int[] numBytesOfEncryptedData;

    public int numSubSamples;

    private final MediaCodec.CryptoInfo frameworkCryptoInfo;

    public CryptoInfo() {
        frameworkCryptoInfo = Util.SDK_INT >= 16 ? newFrameworkCryptoInfoV16() : null;
    }

    public void set(int numSubSamples, int[] numBytesOfClearData, int[] numBytesOfEncryptedData,
                    byte[] key, byte[] iv, @C.CryptoMode int mode) {
        this.numSubSamples = numSubSamples;
        this.numBytesOfClearData = numBytesOfClearData;
        this.numBytesOfEncryptedData = numBytesOfEncryptedData;
        this.key = key;
        this.iv = iv;
        this.mode = mode;
        if (Util.SDK_INT >= 16) {
            updateFrameworkCryptoInfoV16();
        }
    }

    @TargetApi(16)
    public MediaCodec.CryptoInfo getFrameworkCryptoInfoV16() {
        return frameworkCryptoInfo;
    }

    @TargetApi(16)
    private MediaCodec.CryptoInfo newFrameworkCryptoInfoV16() {
        return new MediaCodec.CryptoInfo();
    }

    @TargetApi(16)
    private void updateFrameworkCryptoInfoV16() {
        frameworkCryptoInfo.set(numSubSamples, numBytesOfClearData, numBytesOfEncryptedData, key, iv,
                mode);
    }
}

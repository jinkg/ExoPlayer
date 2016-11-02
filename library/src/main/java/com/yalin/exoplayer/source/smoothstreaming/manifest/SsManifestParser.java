package com.yalin.exoplayer.source.smoothstreaming.manifest;

import android.net.Uri;

import com.yalin.exoplayer.upstream.ParsingLoadable;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * 作者：YaLin
 * 日期：2016/11/2.
 */

public class SsManifestParser implements ParsingLoadable.Parser<SsManifest> {
    private final XmlPullParserFactory xmlPullParserFactory;

    public SsManifestParser() {
        try {
            xmlPullParserFactory = XmlPullParserFactory.newInstance();
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
        }
    }

    @Override
    public SsManifest parse(Uri uri, InputStream inputStream) throws IOException {
        return null;
    }
}

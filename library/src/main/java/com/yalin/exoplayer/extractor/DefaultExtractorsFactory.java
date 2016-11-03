package com.yalin.exoplayer.extractor;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者：YaLin
 * 日期：2016/11/3.
 */

public final class DefaultExtractorsFactory implements ExtractorsFactory {

    private static List<Class<? extends Extractor>> defaultExtractorClasses;

    public DefaultExtractorsFactory() {
        synchronized (DefaultExtractorsFactory.class) {
            if (defaultExtractorClasses == null) {
                List<Class<? extends Extractor>> extractorClasses = new ArrayList<>();

                try {
                    extractorClasses.add(
                            Class.forName("com.google.android.exoplayer2.extractor.mkv.MatroskaExtractor")
                                    .asSubclass(Extractor.class));
                } catch (ClassNotFoundException ignored) {

                }
                defaultExtractorClasses = extractorClasses;
            }
        }
    }

    @Override
    public Extractor[] createExtractors() {
        Extractor[] extractors = new Extractor[defaultExtractorClasses.size()];
        for (int i = 0; i < extractors.length; i++) {
            try {
                extractors[i] = defaultExtractorClasses.get(i).getConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Unexpected error creating default extractor", e);
            }
        }
        return extractors;
    }
}

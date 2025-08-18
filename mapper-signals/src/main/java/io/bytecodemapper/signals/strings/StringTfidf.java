// >>> AUTOGEN: BYTECODEMAPPER StringTfidf BEGIN
package io.bytecodemapper.signals.strings;

import io.bytecodemapper.signals.tfidf.TfIdfModel;
import io.bytecodemapper.signals.common.Cosine;

import java.util.*;

public final class StringTfidf {
    private StringTfidf(){}

    public static TfIdfModel buildModel(List<List<String>> docs) {
        return TfIdfModel.build(docs);
    }

    public static double cosineSimilarity(TfIdfModel model, List<String> a, List<String> b) {
        double[] va = model.vectorize(a);
        double[] vb = model.vectorize(b);
        return Cosine.cosine(va, vb);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER StringTfidf END

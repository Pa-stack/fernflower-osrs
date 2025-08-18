// >>> AUTOGEN: BYTECODEMAPPER CallBagTfidf BEGIN
package io.bytecodemapper.signals.calls;

import io.bytecodemapper.signals.tfidf.TfIdfModel;
import io.bytecodemapper.signals.common.Cosine;

import java.util.*;

public final class CallBagTfidf {
    private CallBagTfidf(){}

    public static TfIdfModel buildModel(List<List<String>> callDocs) {
        return TfIdfModel.build(callDocs);
    }

    public static double cosineSimilarity(TfIdfModel model, List<String> a, List<String> b) {
        double[] va = model.vectorize(a);
        double[] vb = model.vectorize(b);
        return Cosine.cosine(va, vb);
    }
}
// <<< AUTOGEN: BYTECODEMAPPER CallBagTfidf END

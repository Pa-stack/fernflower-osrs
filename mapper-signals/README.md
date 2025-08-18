<!-- >>> AUTOGEN: BYTECODEMAPPER signals README BEGIN -->
# mapper-signals — Secondary Signals (Java 8, deterministic)

This module provides **secondary tie-breaker signals** for the DF/TDF-centric mapper:

- **Call-bag TF-IDF** (excludes `java.*`, `javax.*`)
- **String TF-IDF** (lightweight)
- **Opcode features**: order-invariant histograms (cosine) and optional 2–3-gram frequencies (order-sensitive)

> These signals *never* override strong DF/TDF+WL evidence. They only disambiguate near-ties.

---

## Determinism & compatibility

- **Java 1.8** and **Gradle 6.4.9**.
- Deterministic vocabularies (lexicographically sorted), stable iteration, and pure-function cosine.
- ASM pinned to **7.3.1** across the repo.

---

## Call-bag TF-IDF

- **Extraction**: tokens of the form `owner#name:desc` from `MethodInsnNode`; treats `INVOKEDYNAMIC` as `INVOKEDYNAMIC#name:desc`.
- **Filters**: exclude owners starting with `java/` or `javax/`.
- **Owner normalization**: **stubbed (identity)** here; real normalization happens **after class matching** (Phase-1).
- **Model**: `TfIdfModel` with IDF = `log((N+1)/(df+1)) + 1`, TF = raw counts; cosine in `Cosine`.

APIs:

- `CallBagExtractor.extract(ownerInternalName, mn, normalizer)`
- `CallBagTfidf.buildModel(docs)`, `cosineSimilarity(model, a, b)`


---

## String TF-IDF

- **Extraction**: string constants from `LdcInsnNode` (length ≥ 2).
- **Model**: same TF-IDF as call-bag.

APIs:

- `StringBagExtractor.extract(mn)`
- `StringTfidf.buildModel(docs)`, `cosineSimilarity(model, a, b)`


---

## Opcode features

- **Histogram** (order-invariant): counts over opcodes `[0..199]`; cosine similarity.
- **N-grams** (order-sensitive): bigram/trigram frequency maps (fastutil), cosine over intersection.

APIs:

- `OpcodeFeatures.opcodeHistogram(mn)`, `cosineHistogram(h1, h2)`
- `OpcodeFeatures.opcodeNGram(mn, n /* 2 or 3 */)`, `cosineNGram(mapA, mapB)`

**Testing note:** deliberate permutations keep histograms equal (`cos=1.0`) but drop 2-gram similarity `< 1.0`.

---

## Micropatterns (for reference)

Micropatterns live in `:mapper-signals` as well, with a **frozen 17-bit ABI**.
See `docs/bitset.md` and `MicroScore.blended(...)` for Jaccard/IDF-weighted cosine blending.

---

## Usage (quick sketch)

```java
// calls
List<String> bagA = CallBagExtractor.extract("pkg/Foo", mnA);
List<String> bagB = CallBagExtractor.extract("pkg/Bar", mnB);
TfIdfModel callsModel = CallBagTfidf.buildModel(Arrays.asList(bagA, bagB));
double sCalls = CallBagTfidf.cosineSimilarity(callsModel, bagA, bagB);

// strings
List<String> strA = StringBagExtractor.extract(mnA);
List<String> strB = StringBagExtractor.extract(mnB);
TfIdfModel strModel = StringTfidf.buildModel(Arrays.asList(strA, strB));
double sStr = StringTfidf.cosineSimilarity(strModel, strA, strB);

// opcode
int[] hA = OpcodeFeatures.opcodeHistogram(mnA);
int[] hB = OpcodeFeatures.opcodeHistogram(mnB);
double sOpc = OpcodeFeatures.cosineHistogram(hA, hB);
```

<!-- <<< AUTOGEN: BYTECODEMAPPER signals README END -->

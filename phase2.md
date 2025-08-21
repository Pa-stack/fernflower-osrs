# Phase 2 — WL-relaxed (L1 + size-band)

## Files & snippets

* `mapper-core/src/main/java/io/bytecodemapper/core/wl/WLBags.java#l1/withinBand`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-core\src\main\java\io\bytecodemapper\core\wl\WLBags.java
  // Deterministic L1 distance over two sorted multisets; and size-band gate
  public final class WLBags {
    public static int l1(Long2IntSortedMap a, Long2IntSortedMap b) {
      LongBidirectionalIterator ia = a.keySet().iterator();
      LongBidirectionalIterator ib = b.keySet().iterator();
      long ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
      long kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
      int d = 0;
      while (ka != Long.MAX_VALUE || kb != Long.MAX_VALUE) {
        if (ka == kb) {
          d += Math.abs(a.get(ka) - b.get(kb));
          ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
          kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
        } else if (ka < kb) {
          d += a.get(ka);
          ka = ia.hasNext() ? ia.nextLong() : Long.MAX_VALUE;
        } else { // ka > kb
          d += b.get(kb);
          kb = ib.hasNext() ? ib.nextLong() : Long.MAX_VALUE;
        }
      }
      return d;
    }

    public static boolean withinBand(Long2IntSortedMap a, Long2IntSortedMap b, double pct) {
      int sa = sum(a); int sb = sum(b);
      int lo = (int)Math.floor(sa * (1.0 - pct));
      int hi = (int)Math.ceil (sa * (1.0 + pct));
      return sb >= lo && sb <= hi;
    }
    // ...
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#relaxedCandidates`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // WL-relaxed: build final-iteration bags, gate by L1 ≤ τ and size-band, sort by (L1, owner, name)
  private static java.util.List<NewRef> relaxedCandidates(
      java.util.Map<String, org.objectweb.asm.tree.ClassNode> oldClasses,
      java.util.Map<String, org.objectweb.asm.tree.ClassNode> newClasses,
      String oldOwner,
      String oldName,
      String desc,
      java.util.Map<String, java.util.Map<String, MethodFeatureCacheEntry>> newFeat,
      String newOwner,
      boolean deterministic,
      int l1Tau,
      double sizeBand) {

    java.util.Map<String, MethodFeatureCacheEntry> nm = newFeat.get(newOwner);
    if (nm == null) return java.util.Collections.emptyList();

    // Build old WL token bag once (deterministic)
    it.unimi.dsi.fastutil.longs.Long2IntSortedMap oldBag;
    {
      org.objectweb.asm.tree.ClassNode ocn = oldClasses.get(oldOwner);
      org.objectweb.asm.tree.MethodNode omn = findMethod(ocn, oldName, desc);
      if (omn == null) return java.util.Collections.emptyList();
      io.bytecodemapper.core.cfg.ReducedCFG ocfg = io.bytecodemapper.core.cfg.ReducedCFG.build(omn);
      io.bytecodemapper.core.dom.Dominators odom = io.bytecodemapper.core.dom.Dominators.compute(ocfg);
      oldBag = io.bytecodemapper.core.wl.WLRefinement.tokenBagFinal(ocfg, odom, WL_K);
    }

    java.util.ArrayList<NewRef> pool = new java.util.ArrayList<NewRef>();
    java.util.ArrayList<String> sigs = new java.util.ArrayList<String>(nm.keySet());
    java.util.Collections.sort(sigs); // deterministic

    // Cache new-side WL bags by method name for this owner/desc scope
    java.util.LinkedHashMap<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap> newBags = new java.util.LinkedHashMap<String, it.unimi.dsi.fastutil.longs.Long2IntSortedMap>();

    for (String sig : sigs) {
      String name = sig.substring(0, sig.indexOf('('));
      String d    = sig.substring(sig.indexOf('('));
      if (!desc.equals(d)) continue;
      MethodFeatureCacheEntry mfe = nm.get(sig);
      if (mfe == null) continue;
      // Compute new WL bag (cache per (owner,name,desc))
      it.unimi.dsi.fastutil.longs.Long2IntSortedMap nb = newBags.get(name);
      if (nb == null) {
        org.objectweb.asm.tree.ClassNode ncn = newClasses.get(newOwner);
        org.objectweb.asm.tree.MethodNode nmn = findMethod(ncn, name, desc);
        if (nmn == null) continue;
        io.bytecodemapper.core.cfg.ReducedCFG ncfg = io.bytecodemapper.core.cfg.ReducedCFG.build(nmn);
        io.bytecodemapper.core.dom.Dominators ndom = io.bytecodemapper.core.dom.Dominators.compute(ncfg);
        nb = io.bytecodemapper.core.wl.WLRefinement.tokenBagFinal(ncfg, ndom, WL_K);
        newBags.put(name, nb);
      }
      int dist = io.bytecodemapper.core.wl.WLBags.l1(oldBag, nb);
      boolean band = io.bytecodemapper.core.wl.WLBags.withinBand(oldBag, nb, sizeBand);
      if (dist <= l1Tau && band) {
        pool.add(new NewRef(newOwner, name, desc, mfe.wlSignature));
      }
    }

    // distance asc, then owner, then name (stable deterministic order)
    java.util.Collections.sort(pool, new java.util.Comparator<NewRef>() {
      public int compare(NewRef a, NewRef b) {
        // Compare by computed L1; fallback stable by owner/name
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap nba = newBags.get(a.name);
        it.unimi.dsi.fastutil.longs.Long2IntSortedMap nbb = newBags.get(b.name);
        int da = 0, db = 0;
        if (nba != null && nbb != null) {
          da = io.bytecodemapper.core.wl.WLBags.l1(oldBag, nba);
          db = io.bytecodemapper.core.wl.WLBags.l1(oldBag, nbb);
        }
        int c = Integer.compare(da, db); if (c!=0) return c;
        c = a.owner.compareTo(b.owner); if (c!=0) return c;
        return a.name.compareTo(b.name);
      }
    });

    if (pool.size() > MAX_CANDIDATES) return pool.subList(0, MAX_CANDIDATES);
    return pool;
  }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/core/match/MethodMatcher.java#tiers (WL-relaxed usage)`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\core\match\MethodMatcher.java
  // Use run options; record counters; provenance "wl_relaxed"
  final int l1Tau = options.wlRelaxedL1;
  final double band = options.wlSizeBand;
  java.util.List<NewRef> xs = relaxedCandidates(..., l1Tau, band);
  if (!xs.isEmpty()) { out.wlRelaxedGatePasses++; out.wlRelaxedCandidates += xs.size(); }
  candsRelax.addAll(xs);
  for (NewRef r : xs) { String k = r.owner + "\u0000" + desc + "\u0000" + r.name; if (!candProv.containsKey(k)) candProv.put(k, "wl_relaxed"); }
  ```

* `mapper-cli/src/main/java/io/bytecodemapper/cli/MapOldNew.java#flags`

  ```java
  // c:\Users\pashq\IdeaProjects\fernflower-osrs\fernflower-osrs\mapper-cli\src\main\java\io\bytecodemapper\cli\MapOldNew.java
  // Parse WL-relaxed flags into OrchestratorOptions
  } else if ("--wl-relaxed-l1".equals(a) && i+1<args.length) {
      try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(args[++i])); } catch (NumberFormatException ignore) {}
  } else if (a.startsWith("--wl-relaxed-l1=")) {
      try { wlRelaxedL1 = Integer.valueOf(Integer.parseInt(a.substring("--wl-relaxed-l1=".length()))); } catch (NumberFormatException ignore) {}
  } else if ("--wl-size-band".equals(a) && i+1<args.length) {
      try { wlSizeBand = Double.valueOf(Double.parseDouble(args[++i])); } catch (NumberFormatException ignore) {}
  } else if (a.startsWith("--wl-size-band=")) {
      try { wlSizeBand = Double.valueOf(Double.parseDouble(a.substring("--wl-size-band=".length()))); } catch (NumberFormatException ignore) {}
  }
  // ... later
  if (wlRelaxedL1 != null) o.wlRelaxedL1 = wlRelaxedL1.intValue();
  if (wlSizeBand != null) o.wlSizeBand = wlSizeBand.doubleValue();
  ```

---

### Flags & defaults

| Flag              | Values            |  Default | Purpose                                                     |
| ----------------- | ----------------- | -------: | ----------------------------------------------------------- |
| `--wl-relaxed-l1` | integer ≥ 0       |    2     | Sets the L1 threshold `N` for WL-relaxed gate (`L1 ≤ N`).   |
| `--wl-size-band`  | fraction ∈ [0,1]  |    0.10  | Enforces bag-size band: new bag count within ±(100×value)%. |

---

### Tests present

* `mapper-core/src/test/java/io/bytecodemapper/core/wl/WLBagsDistanceTest.java` — L1 distances 0..3 and size-band at 10%.
* `mapper-core/src/test/java/io/bytecodemapper/core/wl/WLTokenBagDeterminismTest.java` — final-iteration bags stable.
* `mapper-cli/src/test/java/io/bytecodemapper/core/match/WLRelaxedDefaultsReportTest.java` — defaults and overrides echoed.
* `mapper-cli/src/test/java/io/bytecodemapper/core/match/WLRelaxedCountersIT.java` — counters increment only as feeder.
* `mapper-cli/src/test/java/io/bytecodemapper/cli/MapOldNewWlRelaxedFlagsTest.java` — CLI flag parsing and report fields.

---

### Determinism notes

* Final WL bags are derived deterministically from `WLRefinement.tokenBagFinal` over reduced CFG + dominators.
* L1/size-band use `WLBags` which iterates sorted keys; no map-order dependence.
* Candidates from WL-relaxed are sorted by distance then lexicographically; acceptance still gated by τ/margin.

---

### Machine summary (JSON)

```json
{
  "wl_relaxed": {
    "distance": "L1",
    "size_band": 0.10,
    "threshold_default": 2,
    "gate_only": true,
    "report_fields": ["wl_relaxed_l1", "wl_relaxed_size_band", "wl_relaxed_gate_passes", "wl_relaxed_candidates"]
  }
}
```

---

Acceptance checks (optional):

* Build and run WL unit tests
  * Windows PowerShell
    * gradlew.bat :mapper-core:test --tests *WLBags* --no-daemon
* Verify CLI echoes WL-relaxed defaults in report
  * Run task "Mapper: mapOldNew" (which passes --deterministic and writes a report) and confirm report JSON has wl_relaxed_l1=2 and wl_relaxed_size_band=0.10.

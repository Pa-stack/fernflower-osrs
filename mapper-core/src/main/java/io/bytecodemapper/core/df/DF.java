package io.bytecodemapper.core.df;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;
import io.bytecodemapper.core.dom.Dominators;

import java.util.*;

/** Cytron DF producing deterministic sorted arrays without external deps. */
public final class DF {
    private DF() {}

    public static Map<Integer,int[]> compute(ReducedCFG cfg, Dominators dom){int[] nodes=cfg.allBlockIds(); Arrays.sort(nodes); Map<Integer,Set<Integer>> sets=new LinkedHashMap<Integer,Set<Integer>>(); for(int b: nodes) sets.put(b,new HashSet<Integer>());
        for(int n: nodes){Block bn=cfg.block(n); int[] preds=bn.preds(); int id=dom.idom(n); for(int p: preds){int r=p; while(r!=-1 && r!=id){sets.get(r).add(n); r=dom.idom(r);} }} Map<Integer,int[]> out=new LinkedHashMap<Integer,int[]>(); for(int b: nodes) out.put(b, toSorted(sets.get(b))); return out; }

    public static Map<Integer,int[]> iterateToFixpoint(Map<Integer,int[]> df){int[] keys=new int[df.size()]; int i=0; for(Integer k: df.keySet()) keys[i++]=k.intValue(); Arrays.sort(keys); Map<Integer,Set<Integer>> work=new LinkedHashMap<Integer,Set<Integer>>(); for(int k: keys){Set<Integer> s=new HashSet<Integer>(); for(int v: df.get(k)) s.add(v); work.put(k, s);} int guard=0; boolean changed; do{changed=false; for(int b: keys){Set<Integer> s=work.get(b); List<Integer> add=new ArrayList<Integer>(); for(int y: s){int[] dy=df.get(y); if(dy!=null) for(int z: dy) add.add(z);} if(!add.isEmpty()){int before=s.size(); s.addAll(add); if(s.size()!=before) changed=true; } } guard++; if(guard>1000) throw new IllegalStateException("TDF: no fixpoint"); }while(changed);
        Map<Integer,int[]> out=new LinkedHashMap<Integer,int[]>(); for(int b: keys) out.put(b, toSorted(work.get(b))); return out; }

    private static int[] toSorted(Set<Integer> s){ if(s==null||s.isEmpty()) return new int[0]; int[] a=new int[s.size()]; int i=0; for(Integer v: s) a[i++]=v.intValue(); Arrays.sort(a); return a; }
}

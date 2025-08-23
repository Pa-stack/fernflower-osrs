package io.bytecodemapper.core.dom;

import io.bytecodemapper.core.cfg.ReducedCFG;
import io.bytecodemapper.core.cfg.ReducedCFG.Block;

import java.util.*;

/** Cooper–Harvey–Kennedy dominators over ReducedCFG (deterministic). */
public final class Dominators {
    private final int[] nodes; private final Map<Integer,Integer> indexOf;
    private final int[] idomIndex; private final int[] idomIds; private final int[] depth; private final Map<Integer,int[]> children = new HashMap<Integer,int[]>();
    private Dominators(ReducedCFG c,int[] nodes,Map<Integer,Integer> indexOf,int[] rpo,int[] idomIndex,int[] idomIds,int[] depth){this.nodes=nodes;this.indexOf=indexOf;this.idomIndex=idomIndex;this.idomIds=idomIds;this.depth=depth; buildChildren();}

    public static Dominators compute(ReducedCFG cfg){int[] nodes=cfg.allBlockIds(); Arrays.sort(nodes); int n=nodes.length; if(n==0) return new Dominators(cfg,new int[0],new HashMap<Integer,Integer>(),new int[0],new int[0],new int[0],new int[0]); Map<Integer,Integer> idx=new HashMap<Integer,Integer>(); for(int i=0;i<n;i++) idx.put(nodes[i], i);
        int[][] succIdx=new int[n][], predIdx=new int[n][]; for(int i=0;i<n;i++){Block b=cfg.block(nodes[i]); succIdx[i]=mapIds(b.succs(),idx); predIdx[i]=mapIds(b.preds(),idx);} int entry=0; for(int i=1;i<n;i++) if(nodes[i]<nodes[entry]) entry=i; int[] rpo=computeRpo(entry,succIdx,n);
    int[] idom=new int[n]; Arrays.fill(idom,-1); idom[entry]=entry; int[] rpoPos=new int[n];
    // >>> CODEGEN: Dominators.rpoPos init BEGIN
    java.util.Arrays.fill(rpoPos,-1); for(int i=0;i<rpo.length;i++) rpoPos[rpo[i]]=i;
    // <<< CODEGEN: Dominators.rpoPos init END
    boolean changed; do{changed=false; for(int i=0;i<n;i++){int b=rpo[i]; if(b==entry) continue; int newI=-1; for(int p:predIdx[b]) if(idom[p]!=-1){newI=p; break;} if(newI==-1) continue; for(int p:predIdx[b]){ if(p==newI||idom[p]==-1) continue; newI=intersect(idom,rpoPos,p,newI);} if(idom[b]!=newI){idom[b]=newI; changed=true;}} }while(changed);
        int[] idomIds=new int[n], depth=new int[n]; for(int i=0;i<n;i++) idomIds[i]=nodes[idom[i]]; for(int i=0;i<n;i++){int d=0,cur=i,guard=0; while(idom[cur]!=cur && guard++<n+2){d++; cur=idom[cur];} depth[i]=d; }
        return new Dominators(cfg,nodes,idx,rpo,idom,idomIds,depth);
    }

    private static int[] mapIds(int[] ids, Map<Integer,Integer> idx){int[] out=new int[ids.length]; for(int i=0;i<ids.length;i++){Integer v=idx.get(ids[i]); out[i]=v==null?-1:v.intValue();} return out;}
    // >>> CODEGEN: Dominators.computeRpo (returns TRUE RPO) BEGIN
    private static int[] computeRpo(int entry,int[][] succ,int n){boolean[] seen=new boolean[n]; int[] out=new int[n]; int oi=0; Deque<Integer> stack=new ArrayDeque<Integer>(); Deque<Integer> it=new ArrayDeque<Integer>(); stack.push(entry); it.push(0); while(!stack.isEmpty()){int u=stack.peek(); int k=it.pop(); if(!seen[u]){seen[u]=true; k=0;} if(k<succ[u].length){int v=succ[u][k]; it.push(k+1); if(v>=0 && v<n && !seen[v]){stack.push(v); it.push(0);} } else {out[oi++]=u; stack.pop();}} int[] r=new int[oi]; System.arraycopy(out,0,r,0,oi); for(int i=0;i<n;i++) if(!seen[i]){r=append(r,i);} // 'order' is postorder; reverse to get TRUE RPO.
        for(int i=0,j=r.length-1;i<j;i++,j--){int tmp=r[i]; r[i]=r[j]; r[j]=tmp;} return r;}
    // <<< CODEGEN: Dominators.computeRpo (returns TRUE RPO) END
    private static int[] append(int[] a,int v){int[] r=Arrays.copyOf(a,a.length+1); r[a.length]=v; return r;}
    // >>> CODEGEN: Dominators.intersect (RPO-pos, guarded) BEGIN
    private static int intersect(int[] idom,int[] rpoPos,int a0,int b0){int a=a0,b=b0; if(a<0) return b; if(b<0) return a; while(a!=b){int pa=rpoPos[a], pb=rpoPos[b]; if(pa<0||pb<0) break; while(pa<pb){int na=idom[a]; if(na==a||na<0){a=na; break;} a=na; pa=rpoPos[a]; if(a==b) return a;} if(a==b) break; while(pb<pa){int nb=idom[b]; if(nb==b||nb<0){b=nb; break;} b=nb; pb=rpoPos[b]; if(a==b) return a;}} return a;}
    // <<< CODEGEN: Dominators.intersect (RPO-pos, guarded) END

    // >>> CODEGEN: Dominators.debugDump (guarded) BEGIN
    private static final boolean DEBUG = Boolean.getBoolean("mapper.debug");
    @SuppressWarnings("unused")
    private static void debugDumpIdoms(int[] nodes,int[] idomIndex,int[] rpo){ if(!DEBUG) return; StringBuilder sb=new StringBuilder(); sb.append("# idoms (node->idom)\n"); for(int i=0;i<nodes.length;i++){int me=nodes[i]; int pi=idomIndex[i]; sb.append(me).append(" -> ").append(pi>=0? nodes[pi]: -1).append('\n');} sb.append("# rpo\n"); for(int i=0;i<rpo.length;i++){sb.append(i).append(':').append(rpo[i]).append('\n');} System.err.print(sb.toString()); }
    // <<< CODEGEN: Dominators.debugDump (guarded) END
    private void buildChildren(){for(int id: nodes) children.put(id, new int[0]); for(int i=0;i<nodes.length;i++){int me=nodes[i]; int pi=idomIndex[i]; if(pi==i) continue; int parent=nodes[pi]; children.put(parent, append(children.get(parent), me)); } for(Map.Entry<Integer,int[]> e: children.entrySet()){int[] a=e.getValue(); Arrays.sort(a); e.setValue(a);} }

    public int idom(int blockId){Integer i=indexOf.get(blockId); if(i==null) return -1; return idomIds[i.intValue()];}
    public int domDepth(int blockId){Integer i=indexOf.get(blockId); if(i==null) return -1; return depth[i.intValue()];}
    public int[] children(int blockId){int[] ch=children.get(blockId); return ch==null? new int[0]: Arrays.copyOf(ch,ch.length);}
    public boolean dominates(int vBlock,int uBlock){ if(vBlock==uBlock) return true; Integer vi=indexOf.get(vBlock), ui=indexOf.get(uBlock); if(vi==null||ui==null) return false; int v=vi.intValue(), u=ui.intValue(); int guard=0; while(idomIndex[u]!=u && guard++<nodes.length+2){u=idomIndex[u]; if(u==v) return true;} return false; }
}

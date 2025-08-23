package io.bytecodemapper.core.df;

import java.util.Map;

public final class TDF { private TDF(){} public static Map<Integer,int[]> of(Map<Integer,int[]> df){ return DF.iterateToFixpoint(df);} }

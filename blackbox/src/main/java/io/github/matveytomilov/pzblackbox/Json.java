package io.github.matveytomilov.pzblackbox;

import java.util.*;

/** Deliberately accepts only data values, never arbitrary game object toString(). */
public final class Json {
    private Json() {}
    public static String encode(Object value) { StringBuilder s = new StringBuilder(); append(s,value,0); return s.toString(); }
    private static void append(StringBuilder s,Object v,int depth) {
        if(depth>16) throw new IllegalArgumentException("JSON nesting limit");
        if(v==null) s.append("null");
        else if(v instanceof String || v instanceof Enum<?>) quote(s,v instanceof String ? (String)v : ((Enum<?>)v).name());
        else if(v instanceof Boolean) s.append(v);
        else if(v instanceof Number n) { if((n instanceof Double || n instanceof Float) && !Double.isFinite(n.doubleValue())) s.append("null"); else s.append(n); }
        else if(v instanceof Map<?,?> m) { s.append('{');boolean first=true; for(var e:m.entrySet()){if(!(e.getKey() instanceof String))throw new IllegalArgumentException("String keys required");if(!first)s.append(',');first=false;quote(s,(String)e.getKey());s.append(':');append(s,e.getValue(),depth+1);}s.append('}'); }
        else if(v instanceof Iterable<?> a) { s.append('[');boolean first=true;for(Object item:a){if(!first)s.append(',');first=false;append(s,item,depth+1);}s.append(']'); }
        else if(v instanceof long[] a) {s.append('[');for(int i=0;i<a.length;i++){if(i>0)s.append(',');s.append(a[i]);}s.append(']');}
        else throw new IllegalArgumentException("Unsupported JSON value class");
        if(s.length()>131072)throw new IllegalArgumentException("JSON record exceeds 128KiB");
    }
    private static void quote(StringBuilder s,String v){s.append('"');int limit=Math.min(v.length(),8192);for(int i=0;i<limit;i++){char c=v.charAt(i);switch(c){case '"'->s.append("\\\"");case '\\'->s.append("\\\\");case '\n'->s.append("\\n");case '\r'->s.append("\\r");case '\t'->s.append("\\t");default->{if(c<32 || Character.isSurrogate(c))s.append(String.format("\\u%04x",(int)c));else s.append(c);}}}if(v.length()>limit)s.append("[TRUNCATED]");s.append('"');}
    public static Map<String,Object> map(Object... pairs){Map<String,Object> r=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)r.put((String)pairs[i],pairs[i+1]);return r;}
}

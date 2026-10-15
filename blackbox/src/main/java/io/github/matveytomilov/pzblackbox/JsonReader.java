package io.github.matveytomilov.pzblackbox;

import java.util.*;

/** Strict parser for our own bounded JSON records, also used by export privacy filtering. */
public final class JsonReader {
    private final String s;private int p;
    private JsonReader(String s){if(s.length()>131072)throw new IllegalArgumentException("JSON too long");this.s=s;}
    public static Object parse(String s){JsonReader r=new JsonReader(s);Object v=r.value(0);r.ws();if(r.p!=s.length())throw new IllegalArgumentException("Trailing input");return v;}
    private void ws(){while(p<s.length()&&Character.isWhitespace(s.charAt(p)))p++;}
    private char take(){if(p>=s.length())throw new IllegalArgumentException("Unexpected EOF");return s.charAt(p++);}
    private Object value(int depth){if(depth>16)throw new IllegalArgumentException("Depth");ws();char c=take();
        if(c=='"')return string();
        if(c=='{'){Map<String,Object> m=new LinkedHashMap<>();ws();if(p<s.length()&&s.charAt(p)=='}'){p++;return m;}for(;;){ws();if(take()!='"')throw new IllegalArgumentException("Key");String k=string();ws();if(take()!=':')throw new IllegalArgumentException("Colon");m.put(k,value(depth+1));ws();char end=take();if(end=='}')return m;if(end!=',')throw new IllegalArgumentException("Comma");}}
        if(c=='['){List<Object> a=new ArrayList<>();ws();if(p<s.length()&&s.charAt(p)==']'){p++;return a;}for(;;){a.add(value(depth+1));ws();char e=take();if(e==']')return a;if(e!=',')throw new IllegalArgumentException("Comma");}}
        p--;if(s.startsWith("true",p)){p+=4;return true;}if(s.startsWith("false",p)){p+=5;return false;}if(s.startsWith("null",p)){p+=4;return null;}
        int start=p;while(p<s.length()&&"-+0123456789.eE".indexOf(s.charAt(p))>=0)p++;if(start==p)throw new IllegalArgumentException("Value");String n=s.substring(start,p);if(n.contains(".")||n.contains("e")||n.contains("E"))return Double.parseDouble(n);return Long.parseLong(n);
    }
    private String string(){StringBuilder b=new StringBuilder();for(;;){char c=take();if(c=='"')return b.toString();if(c=='\\'){c=take();switch(c){case '"','\\','/'->b.append(c);case 'n'->b.append('\n');case 'r'->b.append('\r');case 't'->b.append('\t');case 'b'->b.append('\b');case 'f'->b.append('\f');case 'u'->{if(p+4>s.length())throw new IllegalArgumentException();b.append((char)Integer.parseInt(s.substring(p,p+4),16));p+=4;}default->throw new IllegalArgumentException("Escape");}}else{if(c<32)throw new IllegalArgumentException("Control");b.append(c);}}}
}

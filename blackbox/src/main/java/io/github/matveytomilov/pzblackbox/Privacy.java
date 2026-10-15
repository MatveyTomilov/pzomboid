package io.github.matveytomilov.pzblackbox;

import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Privacy {
    private static final Pattern STEAM=Pattern.compile("(?<![0-9])[0-9]{17}(?![0-9])");
    private static final Pattern IP=Pattern.compile("(?<![\\w])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?");
    private static final Pattern IPV6=Pattern.compile("(?i)(?<![\\w])(?:[0-9a-f]{0,4}:){2,}[0-9a-f:%]{0,32}");
    private static final Pattern SECRET=Pattern.compile("(?i)(password|passwd|token|secret|api[_-]?key|authorization)(\\s*[=:]\\s*|\\s+)([^\\s,;]+)");
    private static final Pattern USER=Pattern.compile("(?i)([a-z]:[/\\\\]Users[/\\\\])[^/\\\\\\s]+|(/(?:home|Users)/)[^/\\s]+");
    public static String clean(String s){if(s==null)return null;s=USER.matcher(s).replaceAll("<USER_HOME>");s=STEAM.matcher(s).replaceAll("<STEAM_ID>");s=IP.matcher(s).replaceAll("<IP>");s=IPV6.matcher(s).replaceAll("<IPV6>");s=SECRET.matcher(s).replaceAll("$1=<REDACTED>");return s.replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/-]+=*","Bearer <REDACTED>");}
    public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String path(String s){return clean(s==null?null:s.replace('\\','/'));}
}

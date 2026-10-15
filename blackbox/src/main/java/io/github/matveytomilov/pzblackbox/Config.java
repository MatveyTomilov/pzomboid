package io.github.matveytomilov.pzblackbox;

import java.nio.file.Path;
import java.util.Properties;
import java.io.Reader;
import java.nio.file.Files;

/** Recorder configuration only; never reads or writes game settings. */
public final class Config {
    public enum Mode { LIGHT, BALANCED, DEEP }
    public Mode mode = Mode.BALANCED;
    public int queueCapacity = 8192, ringCapacity = 16384, maxFunctions = 2048;
    public long rotationBytes = 8L << 20, sessionBytes = 256L << 20, diskBytes = 1L << 30;
    public long jfrBytes = 64L << 20, preNs = 30_000_000_000L, postNs = 15_000_000_000L;
    public boolean jfr = true, automaticExport = true;
    public static Config load(Path root) {
        Config c = new Config();
        Path file = root.resolve("blackbox.properties");
        if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) return c;
        try (Reader r = Files.newBufferedReader(file)) {
            Properties p = new Properties(); p.load(r);
            c.mode = Mode.valueOf(p.getProperty("mode", "BALANCED").trim().toUpperCase(java.util.Locale.ROOT));
            c.jfr = Boolean.parseBoolean(p.getProperty("jfr", "true"));
            c.automaticExport = Boolean.parseBoolean(p.getProperty("automaticExport", "true"));
        } catch (Exception ignored) { /* Defaults remain safe; startup metadata records effective values. */ }
        return c;
    }
}

package net.sneakycharactermanager.proxy.common;

import net.sneakycharactermanager.common.io.AtomicFiles;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public final class YamlFiles {
    private YamlFiles() {}

    private static Yaml yaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setWidth(120);
        return new Yaml(options);
    }

    /**
     * Loads a YAML file into a map. Returns null if the file exists but could
     * not be parsed (callers must treat null as "do not overwrite").
     * Returns an empty map only when the file genuinely does not exist.
     */
    public static Map<String, Object> load(File file, ProxyLogger logger) {
        if (!file.exists()) return new LinkedHashMap<>();

        try (InputStream in = new FileInputStream(file)) {
            Object root = yaml().load(in);
            if (root instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
            // File was empty or contained a non-map root (e.g. just "---").
            return new LinkedHashMap<>();
        } catch (Exception e) {
            logger.severe("Failed to load YAML file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
            return null;
        }
    }

    public static void save(File file, Map<String, Object> data, ProxyLogger logger) {
        try {
            AtomicFiles.writeUtf8(file.toPath(), yaml().dump(data));
        } catch (Exception e) {
            logger.severe("Failed to save YAML file " + file.getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }
}


package net.sneakycharactermanager.proxy.common;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadOrEmpty(File file) {
        try {
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
                return new LinkedHashMap<>();
            }
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static void save(File file, Map<String, Object> data) {
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            try (FileWriter w = new FileWriter(file, StandardCharsets.UTF_8)) {
                yaml().dump(data, w);
            }
        } catch (Exception ignored) {
            // Persistence best-effort; callers log at higher level when needed.
        }
    }
}


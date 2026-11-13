package net.sneakycharactermanager.paper.handlers.skins;

import net.sneakycharactermanager.proxy.common.SkinUrlPolicy;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkinFetchingPolicyTest {

    @Test
    void missingSectionUsesDefaults() {
        assertEquals(SkinUrlPolicy.defaults(), SkinFetching.policyFrom(new YamlConfiguration()));
    }

    @Test
    void readsConfiguredValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                skin:
                  fetch:
                    allowedHosts: [i.imgur.com, .discordapp.net]
                    allowedPorts: [443]
                    maxBytes: 4096
                    maxRedirects: 1
                    timeoutSeconds: 2
                """);
        SkinUrlPolicy policy = SkinFetching.policyFrom(config);
        assertEquals(Set.of("i.imgur.com", ".discordapp.net"), policy.allowedHosts());
        assertEquals(Set.of(443), policy.allowedPorts());
        assertEquals(4096, policy.maxBytes());
        assertEquals(1, policy.maxRedirects());
        assertEquals(2000, policy.timeoutMillis());
    }
}

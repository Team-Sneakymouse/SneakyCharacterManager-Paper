package net.sneakycharactermanager.proxy.common;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static net.sneakycharactermanager.proxy.common.PngSkinHeaderTest.assertReason;
import static org.junit.jupiter.api.Assertions.*;

class SkinUrlPolicyTest {

    @Test
    void defaultsAcceptPlainHttpAndHttps() throws Exception {
        SkinUrlPolicy policy = SkinUrlPolicy.defaults();
        assertEquals("i.imgur.com", policy.check("https://i.imgur.com/abc.png").getHost());
        assertEquals("example.com", policy.check("http://example.com:80/skin.png").getHost());
    }

    @Test
    void rejectsBadSchemesUserInfoPortsAndGarbage() {
        SkinUrlPolicy policy = SkinUrlPolicy.defaults();
        for (String url : new String[] {
                "", "   ", "httpfoo", "ftp://example.com/a.png", "file:///etc/passwd", "gopher://x/",
                "https://user:pw@example.com/a.png", "https://example.com:8080/a.png", "http://example.com:22/",
                "https:///nohost.png", "http://exa mple.com/" }) {
            assertReason(SkinFetchException.Reason.INVALID_URL, () -> policy.check(url));
        }
    }

    @Test
    void allowListMatchesExactHostsAndDottedSubdomains() throws Exception {
        SkinUrlPolicy policy = new SkinUrlPolicy(Set.of("i.imgur.com", ".DiscordApp.net"), Set.of(), 4096, 1, 1000);
        policy.check("https://i.imgur.com/a.png");
        policy.check("https://media.discordapp.net/a.png");
        policy.check("https://discordapp.net/a.png");
        policy.check("https://i.imgur.com./a.png");
        policy.check("http://textures.minecraft.net/texture/abc");
        assertReason(SkinFetchException.Reason.HOST_NOT_ALLOWED, () -> policy.check("https://imgur.com/a.png"));
        assertReason(SkinFetchException.Reason.HOST_NOT_ALLOWED, () -> policy.check("https://evildiscordapp.net/a.png"));
        assertReason(SkinFetchException.Reason.HOST_NOT_ALLOWED, () -> policy.check("https://i.imgur.com.evil.test/a.png"));
    }

    @Test
    void mojangPolicyOnlyAllowsTextureHost() throws Exception {
        SkinUrlPolicy policy = SkinUrlPolicy.mojangTexturesOnly(5000, 1024);
        policy.check("http://textures.minecraft.net/texture/abc");
        assertReason(SkinFetchException.Reason.HOST_NOT_ALLOWED, () -> policy.check("http://169.254.169.254/latest/meta-data"));
        assertEquals(0, policy.maxRedirects());
    }
}

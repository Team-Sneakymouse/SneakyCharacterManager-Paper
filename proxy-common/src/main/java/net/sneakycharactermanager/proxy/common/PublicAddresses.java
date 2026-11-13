package net.sneakycharactermanager.proxy.common;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Decides whether an address is on the public internet. Used to stop skin URLs from reaching
 * loopback, private, link-local (including cloud metadata) and other special-purpose ranges.
 */
public final class PublicAddresses {

    private PublicAddresses() {}

    public static boolean isPublic(InetAddress address) {
        if (address == null) return false;
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            return isPublicV4(b);
        }
        if (address instanceof Inet6Address) {
            return isPublicV6(b);
        }
        return false;
    }

    private static boolean isPublicV4(byte[] b) {
        int a0 = b[0] & 0xFF, a1 = b[1] & 0xFF, a2 = b[2] & 0xFF;
        if (a0 == 0) return false;                                  // 0.0.0.0/8 "this network"
        if (a0 == 10) return false;                                 // 10/8 private
        if (a0 == 100 && (a1 & 0xC0) == 64) return false;           // 100.64/10 carrier-grade NAT
        if (a0 == 127) return false;                                // loopback
        if (a0 == 169 && a1 == 254) return false;                   // link-local, cloud metadata
        if (a0 == 172 && (a1 & 0xF0) == 16) return false;           // 172.16/12 private
        if (a0 == 192 && a1 == 0 && a2 == 0) return false;          // 192.0.0/24 IETF protocol assignments
        if (a0 == 192 && a1 == 0 && a2 == 2) return false;          // TEST-NET-1
        if (a0 == 192 && a1 == 168) return false;                   // 192.168/16 private
        if (a0 == 198 && (a1 & 0xFE) == 18) return false;           // 198.18/15 benchmarking
        if (a0 == 198 && a1 == 51 && a2 == 100) return false;       // TEST-NET-2
        if (a0 == 203 && a1 == 0 && a2 == 113) return false;        // TEST-NET-3
        if (a0 >= 224) return false;                                // multicast, 240/4 reserved, broadcast
        return true;
    }

    private static boolean isPublicV6(byte[] b) {
        // IPv4-mapped (::ffff:a.b.c.d) and IPv4-compatible (::a.b.c.d) addresses: judge the embedded IPv4 address.
        boolean firstTenZero = true;
        for (int i = 0; i < 10; i++) {
            if (b[i] != 0) { firstTenZero = false; break; }
        }
        if (firstTenZero && ((b[10] == (byte) 0xFF && b[11] == (byte) 0xFF) || (b[10] == 0 && b[11] == 0))) {
            return isPublicV4(new byte[] { b[12], b[13], b[14], b[15] });
        }
        int first = b[0] & 0xFF;
        if ((first & 0xFE) == 0xFC) return false;                   // fc00::/7 unique local
        if (first == 0xFE && (b[1] & 0xC0) == 0x80) return false;   // fe80::/10 link-local
        if (first == 0xFE && (b[1] & 0xC0) == 0xC0) return false;   // fec0::/10 deprecated site-local
        if (first == 0xFF) return false;                            // multicast
        if (first == 0x00) return false;                            // ::/8 reserved (incl. ::, ::1, 64:ff9b::/96 NAT64)
        if (first == 0x20 && b[1] == 0x01 && b[2] == 0x0D && (b[3] & 0xFF) == 0xB8) return false; // 2001:db8::/32 docs
        if (first == 0x20 && b[1] == 0x02) return false;            // 2002::/16 6to4 can embed private IPv4
        if (first == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) return false; // 2001::/32 Teredo
        return true;
    }
}

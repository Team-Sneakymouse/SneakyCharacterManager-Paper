package net.sneakycharactermanager.proxy.common;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

public final class ProxyCrypto {
    private ProxyCrypto() {}

    public static String sign(byte[] messageBytes, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(messageBytes);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    public static boolean verify(byte[] messageBytes, String receivedSignature, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(messageBytes);
        return signature.verify(Base64.getDecoder().decode(receivedSignature));
    }
}


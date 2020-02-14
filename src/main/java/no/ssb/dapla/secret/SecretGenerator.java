package no.ssb.dapla.secret;

import com.google.protobuf.ByteString;
import no.ssb.dapla.secret.service.protobuf.Secret;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class SecretGenerator {

    public static Secret generate(String secretId, String type) {
        int keyLength = 32;
        try {
            keyLength = Integer.parseInt(type.replace("AES", ""));
        } catch (Exception e) { /* swallow */ }
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(keyLength);
            SecretKey secretKey = keyGen.generateKey();
            return Secret.newBuilder().setId(secretId).setType(type).setContent(ByteString.copyFrom(secretKey.getEncoded())).build();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create secret of type %s", type), e);
        }
    }
}

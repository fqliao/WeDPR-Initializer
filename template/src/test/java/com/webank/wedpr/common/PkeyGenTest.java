package com.webank.wedpr.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PkeyGenTest {

    @Test
    public void shardAndRecoverSecret() throws Exception {
        // Sharding secret.
        String masterSecret = Utils.bytesToString(Utils.getSecret());
        List<String> secretShares = Utils.shardingSecretKey(masterSecret, 5, 3);

        // Recover secret.
        List<String> recoverSecretShares = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            recoverSecretShares.add(secretShares.get(i));
        }
        String recoveredMasterSecret = Utils.recoverSecretKey(recoverSecretShares);

        assertTrue(masterSecret.equals(recoveredMasterSecret));
    }

    @Test
    public void encryptAndDecryptData() throws Exception {
        // Generate master secret.
        byte[] masterSecret = Utils.getSecret();

        // Encrypt master secret by password.
        String password = "123456";
        String keyStore = Utils.encryptSecret(masterSecret, password);

        // Decrypt master secret by password.
        byte[] decryptMasterSecret = Utils.decryptSecret(keyStore, password);

        assertTrue(
                Utils.bytesToString(masterSecret).equals(Utils.bytesToString(decryptMasterSecret)));
    }

    @Test
    public void generateValidSecretKey() throws Exception {
        byte[] masterSecret = Utils.getSecret();
        String secretKey = Utils.getSecretKey(masterSecret).getSecretKey();
        String uuid = Utils.getSecretKey(masterSecret).getUuid();

        assertEquals(Utils.stringToBytes(secretKey).length, 32);
        assertEquals(uuid.length(), 36);
    }
}

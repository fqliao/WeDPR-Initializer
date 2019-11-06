package com.webank.wedpr.common;

import static org.junit.Assert.assertTrue;

import com.webank.wedpr.example.assethiding.PublicKeyCryptoExample;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.junit.Test;

public class PublicKeyCryptoExampleTest {

    @Test
    public void encryptAndDecrypt() throws Exception {
        PublicKeyCrypto publicKeyEncryptInterface = new PublicKeyCryptoExample();
        // Generate keypair.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        byte[] publicKey = ecKeyPair.getPublicKey().toByteArray();
        byte[] privateKey = ecKeyPair.getPrivateKey().toByteArray();

        // Encrypt data.
        String data = "Hello World";
        byte[] cipherData = publicKeyEncryptInterface.encrypt(publicKey, data.getBytes());

        // Decrypt
        String decryptedData =
                new String(publicKeyEncryptInterface.decrypt(privateKey, cipherData));

        assertTrue(data.equals(decryptedData));
    }
}

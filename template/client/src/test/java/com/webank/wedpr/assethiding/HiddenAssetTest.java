package com.webank.wedpr.assethiding;

import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.assethiding.DemoMain;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.junit.Before;
import org.junit.Test;

public class HiddenAssetTest {

    @Before
    public void init()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
                    NoSuchProviderException {
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        DemoMain.regulatorPublicKey = ecKeyPair.getPublicKey().toByteArray();
        DemoMain.regulatorSecretKey = ecKeyPair.getPrivateKey().toByteArray();
        DemoMain.publicKeyCrypto = new PublicKeyCryptoExample();
    }

    @Test
    public void transferNumbericAsset() throws Exception {

        DemoMain.transferNumbericAsset();
    }

    @Test
    public void transferNonNumbericAsset() throws Exception {
        DemoMain.transferNonnumericalAsset();
    }

    @Test
    public void splitNumbericAsset() throws Exception {
        DemoMain.splitNumbericAsset();
    }
}

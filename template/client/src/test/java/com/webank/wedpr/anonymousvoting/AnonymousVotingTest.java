package com.webank.wedpr.anonymousvoting;

import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.anonymousvoting.DemoMain;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.junit.Before;
import org.junit.Test;

public class AnonymousVotingTest {

    @Before
    public void init()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
                    NoSuchProviderException {
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        DemoMain.regulatorSecretKey = ecKeyPair.getPrivateKey().toByteArray();
        DemoMain.regulatorPublicKey = ecKeyPair.getPublicKey().toByteArray();
        DemoMain.publicKeyCrypto = new PublicKeyCryptoExample();
    }

    @Test
    public void boundedVoting() throws Exception {
        DemoMain.doVoteBounded();
    }

    @Test
    public void unboundedVoting() throws Exception {
        DemoMain.doVoteUnbounded();
    }
}

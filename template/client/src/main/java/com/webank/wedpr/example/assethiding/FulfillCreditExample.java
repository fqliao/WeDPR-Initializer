package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.RedeemerClient;
import com.webank.wedpr.assethiding.RedeemerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;

public class FulfillCreditExample {

    public static void fulfillCredit(
            TransferType transferType,
            EncodedKeyPair redeemerKeyPair,
            CreditCredential creditCredential,
            StorageExampleClient storageClient)
            throws Exception {
        // 1 Redeemer executes fulfill request.
        RedeemerResult redeemerFulfillResult = null;
        if (transferType == TransferType.Numberic) {
            redeemerFulfillResult = RedeemerClient.fulfillNumericalCredit(creditCredential);
        } else {
            redeemerFulfillResult = RedeemerClient.fulfillNonnumericalCredit(creditCredential);
        }
        // 2 Redeemer verifies fulfill request and then removing credit on blockchain.
        storageClient.fulfillCredit(redeemerFulfillResult.fulfillArgument);

        System.out.println("Blockchain verify fulfill credit successful!");
        CreditValue creditValue = creditCredential.getCreditSecret().getCreditValue();
        System.out.println("fulfill credit value: " + creditValue);
    }
}

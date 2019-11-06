package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.RedeemerClient;
import com.webank.wedpr.assethiding.RedeemerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;

public class FulfillCreditExampleProtocol {

    public static void fulfillCredit(
            TransferType transferType,
            RedeemerClient redeemerClient,
            EncodedKeyPair redeemerKeyPair,
            CreditCredential creditCredential,
            StorageExampleClient storageClientExample)
            throws Exception {
        RedeemerResult redeemerFulfillResult = null;
        if (transferType == TransferType.Numberic) {
            redeemerFulfillResult =
                    redeemerClient.fulfillNumericalCredit(
                            redeemerKeyPair, Utils.protoToEncodedString(creditCredential));
        } else {
            redeemerFulfillResult =
                    redeemerClient.fulfillNonnumericalCredit(
                            redeemerKeyPair, Utils.protoToEncodedString(creditCredential));
        }
        if (Utils.hasWedprError(redeemerFulfillResult)) {
            throw new WedprException(redeemerFulfillResult.wedprErrorMessage);
        }
        TransactionReceipt fulfillCreditReceipt =
                storageClientExample.fulfillCredit(redeemerFulfillResult.fulfillArgument);
        if (!Utils.isTransactionSucceeded(fulfillCreditReceipt)) {
            throw new WedprException("Blockchain verify fulfill credit failed!");
        }
        System.out.println("Blockchain verify fulfill credit successful!");

        CreditValue creditValue = creditCredential.getCreditSecret().getCreditValue();
        System.out.println("fulfill credit value: " + creditValue);
    }
}

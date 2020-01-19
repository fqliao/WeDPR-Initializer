package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;

public class TransferCreditExample {

    public static CreditCredential transferCredit(
            TransferType transferType,
            OwnerState senderOwnerState,
            OwnerState receiverOwnerState,
            TransactionInfo transactionInfo,
            StorageExampleClient storageClient,
            byte[] regulatorPublicKey)
            throws Exception {
        /////////////////////////////////////
        // 1 Receiver sends transfer request.
        /////////////////////////////////////

        OwnerResult ownerResult = null;
        if (transferType == TransferType.Numberic) {
            ownerResult =
                    OwnerClient.receiverTransferNumericalStep1(receiverOwnerState, transactionInfo);
        } else {
            ownerResult =
                    OwnerClient.receiverTransferNonnumericalStep1(
                            receiverOwnerState, transactionInfo);
        }

        /////////////////////////////////////
        // 2 Sender sends transfer request.
        /////////////////////////////////////
        // 2.1 Sender sends transfer request.
        String encodedTransferArgument = ownerResult.transferArgument;
        if (transferType == TransferType.Numberic) {
            ownerResult =
                    OwnerClient.senderTransferNumericalFinal(
                            senderOwnerState, transactionInfo, encodedTransferArgument);
        } else {
            ownerResult =
                    OwnerClient.senderTransferNonnumericalFinal(
                            senderOwnerState, transactionInfo, encodedTransferArgument);
        }
        // 2.2 Sender verifies transfer request and then removing old credit and uploading new
        // credit on blockchain
        // if the request passed the validation.
        storageClient.transferCredit(ownerResult.transferRequest);
        System.out.println("Blockchain verify transfer credit successful!");

        ///////////////////////////////////////////
        // 3 Receiver executes transfer step final.
        ///////////////////////////////////////////
        // 3.1 Receiver executes transfer step final.
        OwnerResult receiverOwnerResult =
                OwnerClient.receiverTransferFinal(receiverOwnerState, ownerResult.creditCredential);
        CreditCredential creditCredentialForRecevier =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(receiverOwnerResult.creditCredential));

        // 3.2 (Optional) Receiver uploads regulation information to blockchain.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String regulationCurrentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialForRecevier);
        String regulationSpentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(
                        senderOwnerState.getCreditCredential());
        String regulationBlindingRG =
                AssethidingUtils.makeTransferBlindingRG(ownerResult.transferArgument);
        String encryptedRegulationInfo =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto, regulatorPublicKey, transactionInfo, regulationBlindingRG);
        storageClient.uploadRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedRegulationInfo);

        return creditCredentialForRecevier;
    }
}

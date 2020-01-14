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

public class TransferCreditExampleProtocol {

    public static CreditCredential transferCredit(
            TransferType transferType,
            OwnerState senderOwnerState,
            OwnerState receiverOwnerState,
            TransactionInfo transactionInfo,
            StorageExampleClient storageClient,
            byte[] regulatorPublicKey)
            throws Exception {
        // 1 receiver transfer step1
        OwnerResult ownerResult = null;
        if (transferType == TransferType.Numberic) {
            ownerResult =
                    OwnerClient.receiverTransferNumericalStep1(receiverOwnerState, transactionInfo);
        } else {
            ownerResult =
                    OwnerClient.receiverTransferNonnumericalStep1(
                            receiverOwnerState, transactionInfo);
        }

        // 2 sender transfer step final
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

        // 3 verify transfer credit and remove old credit and save new credit on blockchain
        storageClient.transferCredit(ownerResult.transferRequest);

        System.out.println("Blockchain verify transfer credit successful!");

        // 4 receiver transfer step final
        String encodedCreditCredential = ownerResult.creditCredential;
        OwnerResult receiverOwnerResult =
                OwnerClient.receiverTransferFinal(receiverOwnerState, encodedCreditCredential);

        String encodeCreditCredentialForReceiver = receiverOwnerResult.creditCredential;
        CreditCredential creditCredentialForRecevier =
                CreditCredential.parseFrom(Utils.stringToBytes(encodeCreditCredentialForReceiver));

        // (Optional) Upload regulation information to blockchain.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String regulationCurrentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialForRecevier);
        String regulationSpentCredit = AssethidingUtils.makeRegulationSpentCredit(senderOwnerState);
        String regulationBlindingRG =
                AssethidingUtils.makeTransferBlindingRG(ownerResult.transferArgument);
        String encryptedRegulationInfo =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto, regulatorPublicKey, transactionInfo, regulationBlindingRG);
        storageClient.insertRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedRegulationInfo);

        return creditCredentialForRecevier;
    }
}

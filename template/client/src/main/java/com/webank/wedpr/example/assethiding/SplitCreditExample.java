package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import java.util.ArrayList;
import java.util.List;

public class SplitCreditExample {
    public static List<CreditCredential> splitCredit(
            OwnerState senderOwnerState,
            OwnerState receiverOwnerState,
            TransactionInfo transactionInfo,
            StorageExampleClient storageClient,
            byte[] regulatorPublicKey)
            throws Exception {
        /////////////////////////////////
        // 1 Sender sends split request.
        /////////////////////////////////
        OwnerResult ownerResultSenderSplitStep1 = OwnerClient.senderSplitStep1(senderOwnerState);

        /////////////////////////////////
        // 2 Receiver sends split request.
        //////////////////////////////////
        OwnerResult ownerResultReceiverSplitStepFinal =
                OwnerClient.receiverSplitStepFinal(
                        receiverOwnerState,
                        transactionInfo,
                        ownerResultSenderSplitStep1.splitArgument);

        /////////////////////////////////////
        // 3 Sender executes split step final.
        /////////////////////////////////////
        // 3.1 Sender executes split step final.
        OwnerResult ownerResultSenderSplitStepFinal =
                OwnerClient.senderSplitStepFinal(
                        senderOwnerState,
                        transactionInfo,
                        ownerResultReceiverSplitStepFinal.splitArgument);
        // 3.2 Sender verifies split request and then removing old credit and uploading new
        // credit on blockchain
        String splitRequest = ownerResultSenderSplitStepFinal.splitRequest;
        storageClient.splitCredit(splitRequest);
        System.out.println("Blockchain verify split credit successful!");

        ///////////////////////////////////////////////////
        // 4 Sender and receiver obtains credit credential.
        ///////////////////////////////////////////////////

        // 4.1 Sender obtains credit credential.
        CreditCredential creditCredentialForSender =
                AssethidingUtils.makeCreditCredentialForSenderBySplit(
                        ownerResultSenderSplitStepFinal.creditCredential);

        // 4.2 Receiver obtains credit credential.
        String creditCredential = ownerResultReceiverSplitStepFinal.creditCredential;
        CreditCredential creditCredentialForReceiver =
                AssethidingUtils.makeCreditCredentialForReceiverBySplit(
                        creditCredential, splitRequest);

        List<CreditCredential> creditCredentialResult = new ArrayList<>();
        creditCredentialResult.add(creditCredentialForSender);
        creditCredentialResult.add(creditCredentialForReceiver);

        // 4.3 (Optional) Sender uploads regulation information on blockchain.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String regulationCurrentCreditSender =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialForSender);
        String regulationSpentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(
                        senderOwnerState.getCreditCredential());
        String regulationBlindingRGSender =
                AssethidingUtils.makeSplitArgumentRGForSender(
                        ownerResultSenderSplitStep1.splitArgument);
        String encryptedRegulationInfoSender =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto,
                        regulatorPublicKey,
                        transactionInfo,
                        regulationBlindingRGSender);
        storageClient.uploadRegulationInfo(
                regulationCurrentCreditSender,
                regulationSpentCredit,
                encryptedRegulationInfoSender);

        // 4.4 (Optional) Receiver uploads regulation information on blockchain.
        String regulationCurrentCreditReceiver =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialForReceiver);
        String regulationBlindingRGReceiver =
                AssethidingUtils.makeSplitArgumentRGForReceiver(
                        ownerResultReceiverSplitStepFinal.splitArgument);
        String encryptedRegulationInfoReceiver =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto,
                        regulatorPublicKey,
                        transactionInfo,
                        regulationBlindingRGReceiver);
        storageClient.uploadRegulationInfo(
                regulationCurrentCreditReceiver,
                regulationSpentCredit,
                encryptedRegulationInfoReceiver);

        return creditCredentialResult;
    }
}

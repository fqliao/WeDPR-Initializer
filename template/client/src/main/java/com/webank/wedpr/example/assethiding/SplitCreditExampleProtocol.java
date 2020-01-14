package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditStorage;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.SplitRequest;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import java.util.ArrayList;
import java.util.List;

public class SplitCreditExampleProtocol {
    public static List<CreditCredential> splitCredit(
            OwnerState senderOwnerState,
            OwnerState receiverOwnerState,
            TransactionInfo transactionInfo,
            StorageExampleClient storageClient,
            byte[] regulatorPublicKey)
            throws Exception {

        // 1 sender split step1
        OwnerResult ownerResultSenderSplitStep1 = OwnerClient.senderSplitStep1(senderOwnerState);

        // 2 receiver split step final
        OwnerResult ownerResultReceiverSplitStepFinal =
                OwnerClient.receiverSplitStepFinal(
                        receiverOwnerState,
                        transactionInfo,
                        ownerResultSenderSplitStep1.splitArgument);
        CreditCredential creditCredentialReceiver =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(ownerResultReceiverSplitStepFinal.creditCredential));

        // 3 sender split step final
        OwnerResult ownerResultSenderSplitStepFinal =
                OwnerClient.senderSplitStepFinal(
                        senderOwnerState,
                        transactionInfo,
                        ownerResultReceiverSplitStepFinal.splitArgument);
        CreditCredential creditCredentialSender =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(ownerResultSenderSplitStepFinal.creditCredential));

        // assemble creditCredential for receiver
        SplitRequest splitRequest =
                SplitRequest.parseFrom(
                        Utils.stringToBytes(ownerResultSenderSplitStepFinal.splitRequest));
        CreditStorage receiverCreditStorage = splitRequest.getNewCredit(0);
        creditCredentialReceiver =
                creditCredentialReceiver
                        .toBuilder()
                        .setCreditStorage(receiverCreditStorage)
                        .build();

        List<CreditCredential> creditCredentialResult = new ArrayList<>();
        creditCredentialResult.add(creditCredentialSender);
        creditCredentialResult.add(creditCredentialReceiver);

        // 4 verify split credit and remove old credit and save new credit on blockchain
        storageClient.splitCredit(ownerResultSenderSplitStepFinal.splitRequest);
        System.out.println("Blockchain verify split credit successful!");

        // (Optional) Upload regulation information to blockchain.
        // Encrypts regulation information for sender and receiver respectively.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();

        String regulationCurrentCreditSender =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialSender);
        String regulationSpentCredit = AssethidingUtils.makeRegulationSpentCredit(senderOwnerState);
        String regulationBlindingRGSender =
                AssethidingUtils.makeSplitArgumentRGForSender(
                        ownerResultSenderSplitStep1.splitArgument);
        String encryptedRegulationInfoSender =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto,
                        regulatorPublicKey,
                        transactionInfo,
                        regulationBlindingRGSender);
        storageClient.insertRegulationInfo(
                regulationCurrentCreditSender,
                regulationSpentCredit,
                encryptedRegulationInfoSender);

        String regulationCurrentCreditReceiver =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredentialReceiver);
        String regulationBlindingRGReceiver =
                AssethidingUtils.makeSplitArgumentRGForReceiver(
                        ownerResultReceiverSplitStepFinal.splitArgument);
        String encryptedRegulationInfoReceiver =
                AssethidingUtils.makeRegulationInfo(
                        publicKeyCrypto,
                        regulatorPublicKey,
                        transactionInfo,
                        regulationBlindingRGReceiver);
        // Saves regulation information on blockchain for sender and receiver respectively.
        storageClient.insertRegulationInfo(
                regulationCurrentCreditReceiver,
                regulationSpentCredit,
                encryptedRegulationInfoReceiver);

        return creditCredentialResult;
    }
}

package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditStorage;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.assethiding.proto.SplitArgument;
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
        String encodedSenderOwnerState = Utils.protoToEncodedString(senderOwnerState);
        OwnerResult ownerResultSenderSplitStep1 =
                OwnerClient.senderSplitStep1(encodedSenderOwnerState);
        Utils.checkWedprResult(ownerResultSenderSplitStep1);
        // 2 receiver split step final
        String encodedReceiverOwnerState = Utils.protoToEncodedString(receiverOwnerState);
        String encodedTransactionInfo = Utils.protoToEncodedString(transactionInfo);

        OwnerResult ownerResultReceiverSplitStepFinal =
                OwnerClient.receiverSplitStepFinal(
                        encodedReceiverOwnerState,
                        encodedTransactionInfo,
                        ownerResultSenderSplitStep1.splitArgument);
        Utils.checkWedprResult(ownerResultReceiverSplitStepFinal);
        CreditCredential creditCredentialReceiver =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(ownerResultReceiverSplitStepFinal.creditCredential));

        // 3 sender split step final
        OwnerResult ownerResultSenderSplitStepFinal =
                OwnerClient.senderSplitStepFinal(
                        encodedSenderOwnerState,
                        encodedTransactionInfo,
                        ownerResultReceiverSplitStepFinal.splitArgument);
        Utils.checkWedprResult(ownerResultSenderSplitStepFinal);
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
        // Sets sender regulation information.
        String regulationCurrentCreditSender =
                Utils.protoToEncodedString(
                        creditCredentialSender.getCreditStorage().getCurrentCredit());
        String regulationSpentCredit =
                Utils.protoToEncodedString(
                        senderOwnerState
                                .getCreditCredential()
                                .getCreditStorage()
                                .getCurrentCredit());
        SplitArgument splitArgumentSender =
                SplitArgument.parseFrom(
                        Utils.stringToBytes(ownerResultSenderSplitStep1.splitArgument));
        String regulationRGSender = splitArgumentSender.getSender(0).getRG();
        byte[] regulationInfoSender =
                RegulationInfo.newBuilder()
                        .setNumericalValue(transactionInfo.getCreditValue().getNumericalValue())
                        .setStringValue(transactionInfo.getCreditValue().getStringValue())
                        .setTransactionMessage(transactionInfo.getTransactionMessage())
                        .setBlindingRG(regulationRGSender)
                        .build()
                        .toByteArray();

        // Sets receiver regulation information.
        String regulationCurrentCreditReceiver =
                Utils.protoToEncodedString(
                        creditCredentialReceiver.getCreditStorage().getCurrentCredit());
        SplitArgument splitArgumentReceiver =
                SplitArgument.parseFrom(
                        Utils.stringToBytes(ownerResultReceiverSplitStepFinal.splitArgument));
        String regulationRGReceiver = splitArgumentReceiver.getReceiver(0).getRG();
        byte[] regulationInfoRecevier =
                RegulationInfo.newBuilder()
                        .setNumericalValue(transactionInfo.getCreditValue().getNumericalValue())
                        .setStringValue(transactionInfo.getCreditValue().getStringValue())
                        .setTransactionMessage(transactionInfo.getTransactionMessage())
                        .setBlindingRG(regulationRGReceiver)
                        .build()
                        .toByteArray();

        // Encrypts regulation information for sender and receiver respectively.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String encryptedregulationInfoSender =
                Utils.bytesToString(
                        publicKeyCrypto.encrypt(regulatorPublicKey, regulationInfoSender));
        String encryptedregulationInfoReceiver =
                Utils.bytesToString(
                        publicKeyCrypto.encrypt(regulatorPublicKey, regulationInfoRecevier));

        // Saves regulation information on blockchain for sender and receiver respectively.
        storageClient.insertRegulationInfo(
                regulationCurrentCreditSender,
                regulationSpentCredit,
                encryptedregulationInfoSender);
        storageClient.insertRegulationInfo(
                regulationCurrentCreditReceiver,
                regulationSpentCredit,
                encryptedregulationInfoReceiver);

        return creditCredentialResult;
    }
}

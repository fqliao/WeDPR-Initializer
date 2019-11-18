package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.assethiding.proto.TransferArgument;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
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
        String encodedTransactionInfo = Utils.protoToEncodedString(transactionInfo);
        String encodedReceiverOwnerState = Utils.protoToEncodedString(receiverOwnerState);
        OwnerResult ownerResult = null;
        if (transferType == TransferType.Numberic) {
            ownerResult =
                    OwnerClient.receiverTransferNumericalStep1(
                            encodedReceiverOwnerState, encodedTransactionInfo);
        } else {
            ownerResult =
                    OwnerClient.receiverTransferNonnumericalStep1(
                            encodedReceiverOwnerState, encodedTransactionInfo);
        }
        if (Utils.hasWedprError(ownerResult)) {
            throw new WedprException(ownerResult.wedprErrorMessage);
        }

        // 2 sender transfer step final
        String encodedSenderOwnerState = Utils.protoToEncodedString(senderOwnerState);
        String encodedTransferArgument = ownerResult.transferArgument;
        if (transferType == TransferType.Numberic) {
            ownerResult =
                    OwnerClient.senderTransferNumericalFinal(
                            encodedSenderOwnerState,
                            encodedTransactionInfo,
                            encodedTransferArgument);
        } else {
            ownerResult =
                    OwnerClient.senderTransferNonnumericalFinal(
                            encodedSenderOwnerState,
                            encodedTransactionInfo,
                            encodedTransferArgument);
        }
        if (Utils.hasWedprError(ownerResult)) {
            throw new WedprException(ownerResult.wedprErrorMessage);
        }
        // 3 verify transfer credit and remove old credit and save new credit on blockchain
        storageClient.transferCredit(ownerResult.transferRequest);

        System.out.println("Blockchain verify transfer credit successful!");

        // 4 receiver transfer step final
        String encodedCreditCredential = ownerResult.creditCredential;
        OwnerResult receiverOwnerResult =
                OwnerClient.receiverTransferFinal(
                        encodedReceiverOwnerState, encodedCreditCredential);

        String encodeCreditCredentialForReceiver = receiverOwnerResult.creditCredential;
        CreditCredential creditCredentialForRecevier =
                CreditCredential.parseFrom(Utils.stringToBytes(encodeCreditCredentialForReceiver));

        // (Optional) Upload regulation information to blockchain.
        String regulationCurrentCredit =
                Utils.protoToEncodedString(
                        creditCredentialForRecevier.getCreditStorage().getCurrentCredit());
        String regulationSpentCredit =
                Utils.protoToEncodedString(
                        senderOwnerState
                                .getCreditCredential()
                                .getCreditStorage()
                                .getCurrentCredit());
        TransferArgument transferArgument =
                TransferArgument.parseFrom(Utils.stringToBytes(ownerResult.transferArgument));
        String regulationRG = transferArgument.getRG();
        byte[] regulationInfo =
                RegulationInfo.newBuilder()
                        .setNumericalValue(transactionInfo.getCreditValue().getNumericalValue())
                        .setStringValue(transactionInfo.getCreditValue().getStringValue())
                        .setTransactionMessage(transactionInfo.getTransactionMessage())
                        .setBlindingRG(regulationRG)
                        .build()
                        .toByteArray();

        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String encryptedregulationInfo =
                Utils.bytesToString(publicKeyCrypto.encrypt(regulatorPublicKey, regulationInfo));
        storageClient.insertRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedregulationInfo);

        return creditCredentialForRecevier;
    }
}

package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.TransferResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.assethiding.proto.TransferArgument;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;

public class TransferCreditExampleProtocol {

    public static CreditCredential transferCredit(
            TransferType transferType,
            OwnerClient ownerClient,
            OwnerState senderOwnerState,
            OwnerState receiverOwnerState,
            TransactionInfo transactionInfo,
            StorageExampleClient storageExampleClient,
            byte[] regulatorPublicKey)
            throws Exception {
        // 1 receiver transfer step1
        String encodedTransactionInfo = Utils.protoToEncodedString(transactionInfo);
        String encodedReceiverOwnerState = Utils.protoToEncodedString(receiverOwnerState);
        TransferResult transferResult = null;
        if (transferType == TransferType.Numberic) {
            transferResult =
                    ownerClient.receiverTransferNumericalStep1(
                            encodedReceiverOwnerState, encodedTransactionInfo);
        } else {
            transferResult =
                    ownerClient.receiverTransferNonnumericalStep1(
                            encodedReceiverOwnerState, encodedTransactionInfo);
        }
        if (Utils.hasWedprError(transferResult)) {
            throw new WedprException(transferResult.wedprErrorMessage);
        }

        // 2 sender transfer step final
        String encodedSenderOwnerState = Utils.protoToEncodedString(senderOwnerState);
        String encodedTransferArgument = transferResult.transferArgument;
        if (transferType == TransferType.Numberic) {
            transferResult =
                    ownerClient.senderTransferNumericalFinal(
                            encodedSenderOwnerState,
                            encodedTransactionInfo,
                            encodedTransferArgument);
        } else {
            transferResult =
                    ownerClient.senderTransferNonnumericalFinal(
                            encodedSenderOwnerState,
                            encodedTransactionInfo,
                            encodedTransferArgument);
        }
        if (Utils.hasWedprError(transferResult)) {
            throw new WedprException(transferResult.wedprErrorMessage);
        }
        // 3 verify transfer credit and remove old credit and save new credit on blockchain
        TransactionReceipt transferCreditReceipt =
                storageExampleClient.transferCredit(transferResult.transferRequest);
        if (!Utils.isTransactionSucceeded(transferCreditReceipt)) {
            throw new WedprException("Blockchain verify transfer credit failed!");
        }
        System.out.println("Blockchain verify transfer credit successful!");

        // 4 receiver transfer step final
        String encodedCreditCredential = transferResult.creditCredential;
        TransferResult receiverTransferResult =
                ownerClient.receiverTransferFinal(
                        encodedReceiverOwnerState, encodedCreditCredential);

        String encodeCreditCredentialForReceiver = receiverTransferResult.creditCredential;
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
                TransferArgument.parseFrom(Utils.stringToBytes(transferResult.transferArgument));
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
        TransactionReceipt insertregulationInfoReceipt =
                storageExampleClient.insertRegulationInfo(
                        regulationCurrentCredit, regulationSpentCredit, encryptedregulationInfo);
        if (!Utils.isTransactionSucceeded(insertregulationInfoReceipt)) {
            throw new WedprException("Inserts regulation information about transferring failed.");
        }

        return creditCredentialForRecevier;
    }
}
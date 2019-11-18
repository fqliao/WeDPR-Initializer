package com.webank.wedpr.assethiding;

import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditStorage;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.IssueArgument;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.SplitArgument;
import com.webank.wedpr.assethiding.proto.SplitRequest;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.assethiding.proto.TransferArgument;
import com.webank.wedpr.assethiding.proto.TransferRequest;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.assethiding.DemoMain;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;

class IssueCreditParams {
    public OwnerClient ownerClient;
    public RedeemerClient redeemerClient;
    public HiddenAssetExamplePerf hiddenAssetExamplePerf;
    public EncodedKeyPair redeemerKeyPair;
    public String hiddenAssetTableName;
    public String issueArgument;
    public CreditValue creditValue;
    public byte[] masterSecret;
    public CreditCredential creditCredential;
}

class TransferCreditParams {
    public HiddenAssetExamplePerf hiddenAssetExamplePerf;
    public String hiddenAssetTableName;
    public String transferRequest;
}

class SplitCreditParams {
    public HiddenAssetExamplePerf hiddenAssetExamplePerf;
    public String hiddenAssetTableName;
    public String splitRequest;
}

public class PerfHiddenAssetUtils {

    public static IssueCreditParams getIssueCreditParams() throws Exception {
        // 1 Deploy contract HiddenAssetExamplePerf.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = DemoMain.getWeb3j(groupID);
        HiddenAssetExamplePerf hiddenAssetExamplePerf =
                HiddenAssetExamplePerf.deploy(
                                web3j,
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();

        // 2 Init hidde asset table.
        String hiddenAssetTableName = "example_" + hiddenAssetExamplePerf.getContractAddress();
        String regulationInfoTable =
                "regulation_info_" + hiddenAssetExamplePerf.getContractAddress();
        TransactionReceipt transactionReceipt =
                hiddenAssetExamplePerf.init(hiddenAssetTableName, regulationInfoTable).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }

        // 3 Owner issue credit.
        OwnerClient ownerClient = new OwnerClient();
        Path path = Paths.get(ClassLoader.getSystemResource(DemoMain.SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        byte[] masterSecret = Utils.decryptSecret(encryptedSecret, "example123");
        String secretKey = Utils.getSecretKey(masterSecret).getSecretKey();
        IssueResult issueResult = ownerClient.issueCredit(secretKey);
        if (Utils.hasWedprError(issueResult)) {
            throw new WedprException(issueResult.wedprErrorMessage);
        }

        // 4 Redeemer confirm credit.
        RedeemerClient redeemerClient = new RedeemerClient();
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();
        int value = 100;
        CreditValue creditValue = CreditValue.newBuilder().setNumericalValue(value).build();
        RedeemerResult redeemerResult =
                redeemerClient.confirmNumericalCredit(redeemerKeyPair, issueResult, creditValue);

        if (Utils.hasWedprError(redeemerResult)) {
            throw new WedprException(redeemerResult.wedprErrorMessage);
        }

        // 5 Clear issue credit secret data.
        IssueArgument issueArgument =
                IssueArgument.parseFrom(Utils.stringToBytes(redeemerResult.issueArgument));
        String handledIssueArgument =
                Utils.protoToEncodedString(
                        issueArgument.toBuilder().clearCreditValue().clearRG().build());
        CreditCredential creditCredential =
                ownerClient.makeCreditCredential(redeemerResult, secretKey);
        IssueCreditParams issueCreditParams = new IssueCreditParams();
        issueCreditParams.ownerClient = ownerClient;
        issueCreditParams.redeemerClient = redeemerClient;
        issueCreditParams.hiddenAssetExamplePerf = hiddenAssetExamplePerf;
        issueCreditParams.redeemerKeyPair = redeemerKeyPair;
        issueCreditParams.hiddenAssetTableName = hiddenAssetTableName;
        issueCreditParams.issueArgument = handledIssueArgument;
        issueCreditParams.creditValue = creditValue;
        issueCreditParams.masterSecret = masterSecret;
        issueCreditParams.creditCredential = creditCredential;

        return issueCreditParams;
    }

    public static TransferCreditParams getTransferCreditParams() throws Exception {
        // Gets a creditCredential and redeemer execute fulfill credit.
        IssueCreditParams issueCreditParams = PerfHiddenAssetUtils.getIssueCreditParams();
        OwnerClient ownerClient = issueCreditParams.ownerClient;
        HiddenAssetExamplePerf hiddenAssetExamplePerf = issueCreditParams.hiddenAssetExamplePerf;
        String hiddenAssetTableName = issueCreditParams.hiddenAssetTableName;
        CreditValue creditValue = issueCreditParams.creditValue;
        CreditCredential creditCredential = issueCreditParams.creditCredential;

        // sender init OwnerState
        OwnerState senderOwnerState =
                AssethidingUtils.getSenderOwnerStateForTransfer(creditCredential);
        // receiver init OwnerState
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);
        // set TransactionInfo
        TransactionInfo transactionInfo =
                TransactionInfo.newBuilder()
                        .setCreditValue(creditValue)
                        .setTransactionMessage("transfer")
                        .build();
        // 1 receiver transfer step1
        String encodedTransactionInfo = Utils.protoToEncodedString(transactionInfo);
        String encodedReceiverOwnerState = Utils.protoToEncodedString(receiverOwnerState);
        TransferResult transferResult = null;
        transferResult =
                ownerClient.receiverTransferNumericalStep1(
                        encodedReceiverOwnerState, encodedTransactionInfo);

        if (Utils.hasWedprError(transferResult)) {
            throw new WedprException(transferResult.wedprErrorMessage);
        }
        // 2 sender transfer step final
        String encodedSenderOwnerState = Utils.protoToEncodedString(senderOwnerState);
        String encodedTransferArgument = transferResult.transferArgument;
        transferResult =
                ownerClient.senderTransferNumericalFinal(
                        encodedSenderOwnerState, encodedTransactionInfo, encodedTransferArgument);
        if (Utils.hasWedprError(transferResult)) {
            throw new WedprException(transferResult.wedprErrorMessage);
        }
        // 3 verify transfer credit and remove old credit and save new credit on blockchain
        // Clear RG in transferRequest.
        TransferRequest transferRequest =
                TransferRequest.parseFrom(Utils.stringToBytes(transferResult.transferRequest));
        TransferArgument transferArgument =
                transferRequest.toBuilder().getArgumentBuilder().clearRG().build();
        String handledTransferRequest =
                Utils.protoToEncodedString(
                        transferRequest
                                .toBuilder()
                                .clearArgument()
                                .setArgument(transferArgument)
                                .build());
        TransferCreditParams transferCreditParams = new TransferCreditParams();
        transferCreditParams.hiddenAssetExamplePerf = hiddenAssetExamplePerf;
        transferCreditParams.hiddenAssetTableName = hiddenAssetTableName;
        transferCreditParams.transferRequest = handledTransferRequest;

        return transferCreditParams;
    }

    public static SplitCreditParams getSplitCreditParams() throws Exception {
        // Gets a creditCredential and redeemer execute fulfill credit.
        IssueCreditParams issueCreditParams = PerfHiddenAssetUtils.getIssueCreditParams();
        OwnerClient ownerClient = issueCreditParams.ownerClient;
        HiddenAssetExamplePerf hiddenAssetExamplePerf = issueCreditParams.hiddenAssetExamplePerf;
        String hiddenAssetTableName = issueCreditParams.hiddenAssetTableName;
        byte[] masterSecret = issueCreditParams.masterSecret;
        CreditCredential creditCredential = issueCreditParams.creditCredential;

        // sender init OwnerState
        OwnerState senderOwnerState =
                AssethidingUtils.getSenderOwnerStateForSplit(masterSecret, creditCredential);

        // receiver init OwnerState
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);

        // set TransactionInfo
        TransactionInfo transactionInfo =
                TransactionInfo.newBuilder()
                        .setCreditValue(CreditValue.newBuilder().setNumericalValue(60).build())
                        .setTransactionMessage("split")
                        .build();

        // 1 sender split step1
        String encodedSenderOwnerState = Utils.protoToEncodedString(senderOwnerState);
        SplitResult splitResultSenderSplitStep1 =
                ownerClient.senderSplitStep1(encodedSenderOwnerState);
        if (Utils.hasWedprError(splitResultSenderSplitStep1)) {
            throw new WedprException(splitResultSenderSplitStep1.wedprErrorMessage);
        }
        // 2 receiver split step final
        String encodedReceiverOwnerState = Utils.protoToEncodedString(receiverOwnerState);
        String encodedTransactionInfo = Utils.protoToEncodedString(transactionInfo);

        SplitResult splitResultReceiverSplitStepFinal =
                ownerClient.receiverSplitStepFinal(
                        encodedReceiverOwnerState,
                        encodedTransactionInfo,
                        splitResultSenderSplitStep1.splitArgument);
        if (Utils.hasWedprError(splitResultReceiverSplitStepFinal)) {
            throw new WedprException(splitResultReceiverSplitStepFinal.wedprErrorMessage);
        }
        CreditCredential creditCredentialReceiver =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(splitResultReceiverSplitStepFinal.creditCredential));

        // 3 sender split step final
        SplitResult splitResultSenderSplitStepFinal =
                ownerClient.senderSplitStepFinal(
                        encodedSenderOwnerState,
                        encodedTransactionInfo,
                        splitResultReceiverSplitStepFinal.splitArgument);
        if (Utils.hasWedprError(splitResultSenderSplitStepFinal)) {
            throw new WedprException(splitResultSenderSplitStepFinal.wedprErrorMessage);
        }
        CreditCredential creditCredentialSender =
                CreditCredential.parseFrom(
                        Utils.stringToBytes(splitResultSenderSplitStepFinal.creditCredential));

        // assemble creditCredential for receiver
        SplitRequest splitRequest =
                SplitRequest.parseFrom(
                        Utils.stringToBytes(splitResultSenderSplitStepFinal.splitRequest));
        CreditStorage receiverCreditStorage = splitRequest.getNewCredit(0);
        creditCredentialReceiver =
                creditCredentialReceiver
                        .toBuilder()
                        .setCreditStorage(receiverCreditStorage)
                        .build();

        List<CreditCredential> creditCredentialResult = new ArrayList<>();
        creditCredentialResult.add(creditCredentialSender);
        creditCredentialResult.add(creditCredentialReceiver);
        // Clear RG in splitRequest.
        TransferArgument senderArgument =
                splitRequest
                        .toBuilder()
                        .clone()
                        .getArgumentBuilder()
                        .getSenderBuilder(0)
                        .clearRG()
                        .build();
        TransferArgument receiverArgument =
                splitRequest
                        .toBuilder()
                        .clone()
                        .getArgumentBuilder()
                        .getReceiverBuilder(0)
                        .clearRG()
                        .build();
        String messageHash = splitRequest.toBuilder().getArgument().getMessageHash();
        SplitArgument splitArgument =
                SplitArgument.newBuilder()
                        .addSender(senderArgument)
                        .addReceiver(receiverArgument)
                        .setMessageHash(messageHash)
                        .build();
        String handledSplitRequest =
                Utils.protoToEncodedString(
                        splitRequest
                                .toBuilder()
                                .clearArgument()
                                .setArgument(splitArgument)
                                .build());

        SplitCreditParams splitCreditParams = new SplitCreditParams();
        splitCreditParams.hiddenAssetExamplePerf = hiddenAssetExamplePerf;
        splitCreditParams.hiddenAssetTableName = hiddenAssetTableName;
        splitCreditParams.splitRequest = handledSplitRequest;

        return splitCreditParams;
    }
}

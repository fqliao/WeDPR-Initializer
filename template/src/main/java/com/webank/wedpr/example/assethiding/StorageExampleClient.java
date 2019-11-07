package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.proto.IssueArgument;
import com.webank.wedpr.assethiding.proto.SplitArgument;
import com.webank.wedpr.assethiding.proto.SplitRequest;
import com.webank.wedpr.assethiding.proto.TransferArgument;
import com.webank.wedpr.assethiding.proto.TransferRequest;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tuples.generated.Tuple3;

public class StorageExampleClient {

    private HiddenAssetExample hiddenAsset;
    private String hiddenAssetTableName;
    private String regulationInfoTableName;

    public StorageExampleClient(
            HiddenAssetExample hiddenAsset,
            String hiddenAssetTableName,
            String regulationInfoTableName) {
        this.hiddenAsset = hiddenAsset;
        this.hiddenAssetTableName = hiddenAssetTableName;
        this.regulationInfoTableName = regulationInfoTableName;
    }

    /**
     * Initializes the data tables.
     *
     * @param tableName
     * @return
     * @throws Exception
     */
    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                hiddenAsset.init(hiddenAssetTableName, regulationInfoTableName).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Queries whether an unspent credit exists.
     *
     * @param currentCredit
     * @return creditStorage
     * @throws Exception
     */
    public String queryCredit(String currentCredit) throws Exception {
        return hiddenAsset.queryCredit(hiddenAssetTableName, currentCredit).send().getValue2();
    }

    /**
     * Issues a new credit if the request passed the validation.
     *
     * @param issueArgumentPb
     * @throws Exception
     */
    public void issueCredit(String issueArgumentPb) throws Exception {
        // Clear creditValue and RG(it will reveal v) in issueArgument.
        IssueArgument issueArgument = IssueArgument.parseFrom(Utils.stringToBytes(issueArgumentPb));
        String handledIssueArgument =
                Utils.protoToEncodedString(
                        issueArgument.toBuilder().clearCreditValue().clearRG().build());
        TransactionReceipt transactionReceipt =
                hiddenAsset.issueCredit(hiddenAssetTableName, handledIssueArgument).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Fulfills an existing credit if the request passed the validation.
     *
     * @param fulfillArgumentPb
     * @throws Exception
     */
    public void fulfillCredit(String fulfillArgumentPb) throws Exception {
        TransactionReceipt transactionReceipt =
                hiddenAsset.fulfillCredit(hiddenAssetTableName, fulfillArgumentPb).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Transfers an existing credit if the request passed the validation.
     *
     * @param transferRequestPb
     * @throws Exception
     */
    public void transferCredit(String transferRequestPb) throws Exception {
        // Clear RG in transferRequest.
        TransferRequest transferRequest =
                TransferRequest.parseFrom(Utils.stringToBytes(transferRequestPb));
        TransferArgument transferArgument =
                transferRequest.toBuilder().getArgumentBuilder().clearRG().build();
        String handledTransferRequest =
                Utils.protoToEncodedString(
                        transferRequest
                                .toBuilder()
                                .clearArgument()
                                .setArgument(transferArgument)
                                .build());
        TransactionReceipt transactionReceipt =
                hiddenAsset.transferredCredit(hiddenAssetTableName, handledTransferRequest).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Splits an existing credit if the request passed the validation.
     *
     * @param splitRequestPb
     * @throws Exception
     */
    public void splitCredit(String splitRequestPb) throws Exception {
        // Clear RG in splitRequest.
        SplitRequest splitRequest = SplitRequest.parseFrom(Utils.stringToBytes(splitRequestPb));
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
        TransactionReceipt transactionReceipt =
                hiddenAsset.splitCredit(hiddenAssetTableName, handledSplitRequest).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Inserts regulation information.
     *
     * @param currentCreditPb
     * @param spentCreditPb
     * @param regulationInfoPb
     * @throws Exception
     */
    public void insertRegulationInfo(
            String currentCreditPb, String spentCreditPb, String regulationInfoPb)
            throws Exception {
        TransactionReceipt transactionReceipt =
                hiddenAsset
                        .insertRegulationInfo(
                                regulationInfoTableName,
                                currentCreditPb,
                                spentCreditPb,
                                regulationInfoPb)
                        .send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Queries regulation information.
     *
     * @param currentCreditPb
     * @return
     * @throws Exception
     */
    public Tuple3<List<String>, List<String>, List<String>> queryRegulationInfo(
            String currentCreditPb) throws Exception {
        return hiddenAsset.queryRegulationInfo(regulationInfoTableName, currentCreditPb).send();
    }
}

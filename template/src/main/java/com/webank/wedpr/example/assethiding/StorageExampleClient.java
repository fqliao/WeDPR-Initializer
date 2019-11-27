package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.proto.IssueArgument;
import com.webank.wedpr.assethiding.proto.SplitArgument;
import com.webank.wedpr.assethiding.proto.SplitRequest;
import com.webank.wedpr.assethiding.proto.TransferArgument;
import com.webank.wedpr.assethiding.proto.TransferRequest;
import com.webank.wedpr.common.Utils;
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
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Queries whether an unspent credit exists.
     *
     * @param currentCredit
     * @return creditStorage
     * @throws Exception
     */
    public String queryCredit(String currentCredit) throws Exception {
        return hiddenAsset.queryCredit(currentCredit).send().getValue2();
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
                hiddenAsset.issueCredit(handledIssueArgument).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Fulfills an existing credit if the request passed the validation.
     *
     * @param fulfillArgumentPb
     * @throws Exception
     */
    public void fulfillCredit(String fulfillArgumentPb) throws Exception {
        TransactionReceipt transactionReceipt = hiddenAsset.fulfillCredit(fulfillArgumentPb).send();
        Utils.checkTranactionReceipt(transactionReceipt);
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
                hiddenAsset.transferredCredit(handledTransferRequest).send();
        Utils.checkTranactionReceipt(transactionReceipt);
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
        TransactionReceipt transactionReceipt = hiddenAsset.splitCredit(handledSplitRequest).send();
        Utils.checkTranactionReceipt(transactionReceipt);
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
                        .insertRegulationInfo(currentCreditPb, spentCreditPb, regulationInfoPb)
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
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
        return hiddenAsset.queryRegulationInfo(currentCreditPb).send();
    }
}

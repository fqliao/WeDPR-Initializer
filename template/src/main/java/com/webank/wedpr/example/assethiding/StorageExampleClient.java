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
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

public class StorageExampleClient {

    private HiddenAssetExample hiddenAsset;
    private String hiddenAssetTable;
    private String regulationInfoTable;
    private TransactionDecoder transactionDecoder;

    public StorageExampleClient(
            HiddenAssetExample hiddenAsset, String hiddenAssetTable, String regulationInfoTable) {
        this.hiddenAsset = hiddenAsset;
        this.hiddenAssetTable = hiddenAssetTable;
        this.regulationInfoTable = regulationInfoTable;
        transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(HiddenAssetExample.ABI, "");
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
                hiddenAsset.init(hiddenAssetTable, regulationInfoTable).send();
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
        return hiddenAsset.queryCredit(hiddenAssetTable, currentCredit).send().getValue2();
    }

    /**
     * Issues a new credit if the request passed the validation.
     *
     * @param issueArgumentPb
     * @return
     * @throws Exception
     */
    public TransactionReceipt issueCredit(String issueArgumentPb) throws Exception {
        // Clear creditValue and RG(it will reveal v) in issueArgument.
        IssueArgument issueArgument = IssueArgument.parseFrom(Utils.stringToBytes(issueArgumentPb));
        String handledIssueArgument =
                Utils.protoToEncodedString(
                        issueArgument.toBuilder().clearCreditValue().clearRG().build());
        return hiddenAsset.issueCredit(hiddenAssetTable, handledIssueArgument).send();
    }

    /**
     * Fulfills an existing credit if the request passed the validation.
     *
     * @param fulfillArgumentPb
     * @return
     * @throws Exception
     */
    public TransactionReceipt fulfillCredit(String fulfillArgumentPb) throws Exception {
        return hiddenAsset.fulfillCredit(hiddenAssetTable, fulfillArgumentPb).send();
    }

    /**
     * Transfers an existing credit if the request passed the validation.
     *
     * @param transferRequestPb
     * @return
     * @throws Exception
     */
    public TransactionReceipt transferCredit(String transferRequestPb) throws Exception {
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
        return hiddenAsset.transferredCredit(hiddenAssetTable, handledTransferRequest).send();
    }

    /**
     * Splits an existing credit if the request passed the validation.
     *
     * @param splitRequestPb
     * @return
     * @throws Exception
     */
    public TransactionReceipt splitCredit(String splitRequestPb) throws Exception {
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
        return hiddenAsset.splitCredit(hiddenAssetTable, handledSplitRequest).send();
    }

    /**
     * Inserts regulation information.
     *
     * @param currentCreditPb
     * @param regulationInfoPb
     * @return
     * @throws Exception
     */
    public TransactionReceipt insertRegulationInfo(
            String currentCreditPb, String spentCreditPb, String regulationInfoPb)
            throws Exception {
        return hiddenAsset
                .insertRegulationInfo(
                        regulationInfoTable, currentCreditPb, spentCreditPb, regulationInfoPb)
                .send();
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
        return hiddenAsset.queryRegulationInfo(regulationInfoTable, currentCreditPb).send();
    }
}

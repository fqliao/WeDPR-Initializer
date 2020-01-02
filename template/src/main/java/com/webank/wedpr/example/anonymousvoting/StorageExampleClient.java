package com.webank.wedpr.example.anonymousvoting;

import com.webank.wedpr.anonymousvoting.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousvoting.proto.VoteResultRequest;
import com.webank.wedpr.common.Utils;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.ResultEntity;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

public class StorageExampleClient {

    private AnonymousVotingExample anonymousVoting;
    private String voterTableName;
    private String counterTableName;
    private String regulationInfoTableName;
    private String voterAggregateTableName;
    private String counterAggregateTableName;
    private TransactionDecoder transactionDecoder;

    public StorageExampleClient(
            AnonymousVotingExample anonymousVoting,
            String voterTableName,
            String counterTableName,
            String voterAggregateTableName,
            String counterAggregateTableName,
            String regulationInfoTableName) {
        this.anonymousVoting = anonymousVoting;
        this.voterTableName = voterTableName;
        this.counterTableName = counterTableName;
        this.voterAggregateTableName = voterAggregateTableName;
        this.counterAggregateTableName = counterAggregateTableName;
        this.regulationInfoTableName = regulationInfoTableName;
        transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(AnonymousVotingExample.ABI, "");
    }

    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .init(
                                voterTableName,
                                counterTableName,
                                regulationInfoTableName,
                                voterAggregateTableName,
                                counterAggregateTableName)
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public TransactionDecoder getTransactionDecoder() {
        return transactionDecoder;
    }

    public TransactionReceipt verifyUnboundedVoteRequest(
            String voteRequest, SystemParametersStorage systemParameters) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyUnboundedVoteRequest(
                                voteRequest, Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
        return transactionReceipt;
    }

    public TransactionReceipt verifyBoundedVoteRequest(
            String voteRequest, SystemParametersStorage systemParameters) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyBoundedVoteRequest(
                                voteRequest, Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
        return transactionReceipt;
    }

    public boolean aggregateVoteStorage(SystemParametersStorage systemParameters) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .aggregateVoteStorage(Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
        List<ResultEntity> receiptOutputResult =
                Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);

        return (Boolean) receiptOutputResult.get(0).getData();
    }

    public void verifyCountRequest(
            String hPointShare,
            String decryptedRequest,
            String voteStorage,
            SystemParametersStorage systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyCountRequest(
                                hPointShare,
                                decryptedRequest,
                                voteStorage,
                                Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public boolean aggregateDecryptedPart(SystemParametersStorage systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .aggregateDecryptedPart(Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
        List<ResultEntity> receiptOutputResult =
                Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);
        return (Boolean) receiptOutputResult.get(0).getData();
    }
    /**
     * Inserts a counter hPoint share.
     *
     * @param counterId
     * @param hPointShare
     * @return
     * @throws Exception
     */
    public void insertHPointShare(String counterId, String hPointShare) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.insertHPointShare(counterId, hPointShare).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Queries a counter hPoint share.
     *
     * @param counterId
     * @return
     * @throws Exception
     */
    public List<String> queryHPointShare(String counterId) throws Exception {
        return anonymousVoting.queryHPointShare(counterId).send();
    }

    /**
     * Moves to the next contract state.
     *
     * @return
     * @throws Exception
     */
    public void nextContractState() throws Exception {
        TransactionReceipt transactionReceipt = anonymousVoting.nextContractState().send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public void verifyVoteResult(
            VoteResultRequest voteResultRequest, SystemParametersStorage systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyVoteResult(
                                Utils.protoToEncodedString(voteResultRequest),
                                Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public void setCandidates(List<String> candidates) throws Exception {
        TransactionReceipt transactionReceipt = anonymousVoting.setCandidates(candidates).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public BigInteger getContractState() throws Exception {
        return anonymousVoting.contractState().send();
    }

    public List<String> getCandidates() throws Exception {
        return anonymousVoting.getCandidates().send();
    }

    public String getHPoint() throws Exception {
        return anonymousVoting.getHPoint().send();
    }

    public String getDecryptedResultPartStorageSumTotal() throws Exception {
        return anonymousVoting.getDecryptedResultPartStorageSum().send();
    }

    public String getVoteStorageSum() throws Exception {
        return anonymousVoting.getVoteStorageSum().send();
    }

    public String getVoteResultStorage() throws Exception {
        return anonymousVoting.getVoteResultStorage().send();
    }

    public List<String> queryVoteStoragePart(String blankBallot) throws Exception {
        return anonymousVoting.queryVoteStoragePart(blankBallot).send();
    }

    /**
     * Inserts regulation information.
     *
     * @param blankBallot
     * @param regulationInfoPb
     * @return
     * @throws Exception
     */
    public void insertRegulationInfo(String blankBallot, String regulationInfoPb) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.insertRegulationInfo(blankBallot, regulationInfoPb).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Queries regulation information.
     *
     * @param blankBallot
     * @return
     * @throws Exception
     */
    public List<String> queryRegulationInfo(String blankBallot) throws Exception {
        return anonymousVoting.queryRegulationInfo(blankBallot).send();
    }
}

package com.webank.wedpr.example.anonymousvoting;

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
            String regulationInfoTableName,
            String voterAggregateTableName,
            String counterAggregateTableName) {
        this.anonymousVoting = anonymousVoting;
        this.voterTableName = voterTableName;
        this.counterTableName = counterTableName;
        this.regulationInfoTableName = regulationInfoTableName;
        this.voterAggregateTableName = voterAggregateTableName;
        this.counterAggregateTableName = counterAggregateTableName;
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
            String voteRequest, String systemParameters) throws Exception {
        return anonymousVoting.verifyUnboundedVoteRequest(voteRequest, systemParameters).send();
    }

    public TransactionReceipt verifyBoundedVoteRequest(String voteRequest, String systemParameters)
            throws Exception {
        return anonymousVoting.verifyBoundedVoteRequest(voteRequest, systemParameters).send();
    }

    public boolean aggregateVoteStorage(String systemParameters) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.aggregateVoteStorage(systemParameters).send();
        List<ResultEntity> receiptOutputResult =
                Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);
        return (Boolean) receiptOutputResult.get(0).getData();
    }

    public void verifyCountRequest(
            String hPointShare,
            String decryptedRequest,
            String voteStorage,
            String systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyCountRequest(
                                hPointShare, decryptedRequest, voteStorage, systemParameters)
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public boolean aggregateDecryptedPart(String systemParameters) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.aggregateDecryptedPart(systemParameters).send();
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

    public void verifyVoteResult(String voteResultRequest, String systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.verifyVoteResult(voteResultRequest, systemParameters).send();
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

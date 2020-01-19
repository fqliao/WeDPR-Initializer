package com.webank.wedpr.example.anonymousvoting;

import com.webank.wedpr.anonymousvoting.proto.StringToInt64Pair;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousvoting.proto.VoteResultStorage;
import com.webank.wedpr.common.Utils;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.ResultEntity;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

/**
 * @author caryliao
 * @date 2020/01/15
 */
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

    /**
     * Inits contract.
     *
     * @throws Exception
     */
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

    /**
     * Verifies unbounded voteRequest
     *
     * @param voteRequest
     * @param systemParameters
     * @return
     * @throws Exception
     */
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

    /**
     * Verifies bounded voteRequest
     *
     * @param voteRequest
     * @param systemParameters
     * @return
     * @throws Exception
     */
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

    /**
     * Aggregates voteStorage
     *
     * @param systemParameters
     * @throws Exception
     */
    public void aggregateVoteStorage(SystemParametersStorage systemParameters) throws Exception {
        boolean result = true;
        do {
            TransactionReceipt transactionReceipt =
                    anonymousVoting
                            .aggregateVoteStorage(Utils.protoToEncodedString(systemParameters))
                            .send();
            Utils.checkTranactionReceipt(transactionReceipt);
            List<ResultEntity> receiptOutputResult =
                    Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);

            result = (Boolean) receiptOutputResult.get(0).getData();
        } while (result);
    }

    /**
     * Verifies countRequest
     *
     * @param hPointShare
     * @param decryptedRequest
     * @param voteStorage
     * @param systemParameters
     * @throws Exception
     */
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

    /**
     * Aggregates decryptedPart.
     *
     * @param systemParameters
     * @throws Exception
     */
    public void aggregateDecryptedPart(SystemParametersStorage systemParameters) throws Exception {
        boolean result = true;
        do {
            TransactionReceipt transactionReceipt =
                    anonymousVoting
                            .aggregateDecryptedPart(Utils.protoToEncodedString(systemParameters))
                            .send();
            Utils.checkTranactionReceipt(transactionReceipt);
            List<ResultEntity> receiptOutputResult =
                    Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);
            result = (Boolean) receiptOutputResult.get(0).getData();
        } while (result);
    }

    /**
     * Inserts a counter hPoint share.
     *
     * @param counterId
     * @param hPointShare
     * @return
     * @throws Exception
     */
    public void uploadHPointShare(String counterId, String hPointShare) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.insertHPointShare(counterId, hPointShare).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public String queryHPointShare(String counterId) throws Exception {
        return (String) anonymousVoting.queryHPointShare(counterId).send().get(0);
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

    /**
     * Verifies vote result.
     *
     * @param voteResultRequest
     * @param systemParameters
     * @throws Exception
     */
    public void verifyVoteResult(String voteResultRequest, SystemParametersStorage systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyVoteResult(
                                voteResultRequest, Utils.protoToEncodedString(systemParameters))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Uploads candidates.
     *
     * @param candidates
     * @throws Exception
     */
    public void uploadCandidates(List<String> candidates) throws Exception {
        TransactionReceipt transactionReceipt = anonymousVoting.setCandidates(candidates).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public BigInteger queryContractState() throws Exception {
        return anonymousVoting.contractState().send();
    }

    public List<String> queryCandidates() throws Exception {
        return anonymousVoting.getCandidates().send();
    }

    public String queryHPoint() throws Exception {
        return anonymousVoting.getHPoint().send();
    }

    public String queryDecryptedResultPartStorageSumTotal() throws Exception {
        return anonymousVoting.getDecryptedResultPartStorageSum().send();
    }

    public String queryVoteStorageSum() throws Exception {
        return anonymousVoting.getVoteStorageSum().send();
    }

    public List<StringToInt64Pair> queryVoteResult() throws Exception {
        String encodedVoteResultStorage = anonymousVoting.getVoteResultStorage().send();
        VoteResultStorage voteResultStorage =
                VoteResultStorage.parseFrom(Utils.stringToBytes(encodedVoteResultStorage));
        return voteResultStorage.getResultList();
    }

    public List<String> queryVoteStoragePart(String blankBallot) throws Exception {
        return anonymousVoting.queryVoteStoragePart(blankBallot).send();
    }

    public void insertRegulationInfo(String blankBallot, String regulationInfoPb) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.insertRegulationInfo(blankBallot, regulationInfoPb).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public List<String> queryRegulationInfo(String blankBallot) throws Exception {
        return anonymousVoting.queryRegulationInfo(blankBallot).send();
    }
}

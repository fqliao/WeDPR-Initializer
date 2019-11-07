package com.webank.wedpr.example.anonymousvoting;

import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

public class StorageExampleClient {

    private AnonymousVotingExample anonymousVoting;
    private String voterTableName;
    private String counterTableName;
    private String regulationInfoTableName;
    private TransactionDecoder transactionDecoder;

    public StorageExampleClient(
            AnonymousVotingExample anonymousVoting,
            String voterTableName,
            String counterTableName,
            String regulationInfoTableName) {
        this.anonymousVoting = anonymousVoting;
        this.voterTableName = voterTableName;
        this.counterTableName = counterTableName;
        this.regulationInfoTableName = regulationInfoTableName;
        transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(AnonymousVotingExample.ABI, "");
    }

    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .init(voterTableName, counterTableName, regulationInfoTableName)
                        .send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    public TransactionDecoder getTransactionDecoder() {
        return transactionDecoder;
    }

    public TransactionReceipt verifyUnboundedVoteRequest(
            String systemParameters, String voteRequest) throws Exception {
        return anonymousVoting
                .verifyUnboundedVoteRequest(voterTableName, systemParameters, voteRequest)
                .send();
    }

    public TransactionReceipt verifyBoundedVoteRequest(String systemParameters, String voteRequest)
            throws Exception {
        return anonymousVoting
                .verifyBoundedVoteRequest(voterTableName, systemParameters, voteRequest)
                .send();
    }

    public void verifyCountRequest(
            String systemParameters,
            String voteStorage,
            String hPointShare,
            String decryptedRequest)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyCountRequest(
                                counterTableName,
                                systemParameters,
                                voteStorage,
                                hPointShare,
                                decryptedRequest)
                        .send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
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
                anonymousVoting.insertHPointShare(counterTableName, counterId, hPointShare).send();
    }

    /**
     * Queries a counter hPoint share.
     *
     * @param counterId
     * @return
     * @throws Exception
     */
    public List<String> queryHPointShare(String counterId) throws Exception {
        return anonymousVoting.queryHPointShare(counterTableName, counterId).send();
    }

    /**
     * Moves to the next contract state.
     *
     * @return
     * @throws Exception
     */
    public void nextContractState() throws Exception {
        TransactionReceipt transactionReceipt = anonymousVoting.nextContractState().send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    public void verifyVoteResult(
            String systemParameters,
            String voteStorageSum,
            String decryptedResultPartStorageSum,
            String voteResultRequest)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting
                        .verifyVoteResult(
                                systemParameters,
                                voteStorageSum,
                                decryptedResultPartStorageSum,
                                voteResultRequest)
                        .send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    public void setCandidates(List<String> candidates) throws Exception {
        TransactionReceipt transactionReceipt = anonymousVoting.setCandidates(candidates).send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
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
        return anonymousVoting.getDecryptedResultPartStorageSumTotal().send();
    }

    public String getVoteStorageSumTotal() throws Exception {
        return anonymousVoting.getVoteStorageSumTotal().send();
    }

    public String getVoteResultStorage() throws Exception {
        return anonymousVoting.getVoteResultStorage().send();
    }

    public List<String> queryVoteStoragePart(String blankBallot) throws Exception {
        return anonymousVoting.queryVoteStoragePart(voterTableName, blankBallot).send();
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
                anonymousVoting
                        .insertRegulationInfo(
                                regulationInfoTableName, blankBallot, regulationInfoPb)
                        .send();
        if (!Utils.isTransactionSucceeded(transactionReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(transactionReceipt));
        }
    }

    /**
     * Queries regulation information.
     *
     * @param blankBallot
     * @return
     * @throws Exception
     */
    public List<String> queryRegulationInfo(String blankBallot) throws Exception {
        return anonymousVoting.queryRegulationInfo(regulationInfoTableName, blankBallot).send();
    }
}

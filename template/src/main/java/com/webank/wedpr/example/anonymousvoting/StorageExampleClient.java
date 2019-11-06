package com.webank.wedpr.example.anonymousvoting;

import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.txdecode.InputAndOutputResult;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

public class StorageExampleClient {

    private AnonymousVotingExample anonymousVoting;
    private String voterTableName;
    private String counterTableName;
    private TransactionDecoder transactionDecoder;

    public StorageExampleClient(
            AnonymousVotingExample anonymousVoting,
            String voterTableName,
            String counterTableName) {
        this.anonymousVoting = anonymousVoting;
        this.voterTableName = voterTableName;
        this.counterTableName = counterTableName;
        transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(AnonymousVotingExample.ABI, "");
    }

    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVoting.init(voterTableName, counterTableName).send();
        InputAndOutputResult inputAndOutputResult =
                transactionDecoder.decodeOutputReturnObject(
                        transactionReceipt.getInput(), transactionReceipt.getOutput());

        int voterTableResult =
                ((BigInteger) inputAndOutputResult.getResult().get(0).getData()).intValue();
        int counterTableResult =
                ((BigInteger) inputAndOutputResult.getResult().get(1).getData()).intValue();
        if (!Utils.isInitSucceeded(voterTableResult)) {
            throw new WedprException("Initializes the voter table failed.");
        }
        if (!Utils.isInitSucceeded(counterTableResult)) {
            throw new WedprException("Initializes the counter table failed.");
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

    public TransactionReceipt verifyCountRequest(
            String systemParameters,
            String voteStorage,
            String hPointShare,
            String decryptedRequest)
            throws Exception {
        return anonymousVoting
                .verifyCountRequest(
                        counterTableName,
                        systemParameters,
                        voteStorage,
                        hPointShare,
                        decryptedRequest)
                .send();
    }

    /**
     * Inserts a counter hPoint share.
     *
     * @param counterId
     * @param hPointShare
     * @return
     * @throws Exception
     */
    public TransactionReceipt insertHPointShare(String counterId, String hPointShare)
            throws Exception {
        return anonymousVoting.insertHPointShare(counterTableName, counterId, hPointShare).send();
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
    public TransactionReceipt nextContractState() throws Exception {
        return anonymousVoting.nextContractState().send();
    }

    public TransactionReceipt verifyVoteResult(
            String systemParameters,
            String voteStorageSum,
            String decryptedResultPartStorageSum,
            String voteResultRequest)
            throws Exception {
        return anonymousVoting
                .verifyVoteResult(
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSum,
                        voteResultRequest)
                .send();
    }

    public TransactionReceipt setCandidates(List<String> candidates) throws Exception {
        return anonymousVoting.setCandidates(candidates).send();
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
}

package com.webank.wedpr.anonymousvoting;

import com.webank.wedpr.anonymousvoting.proto.CoordinatorState;
import com.webank.wedpr.anonymousvoting.proto.CounterState;
import com.webank.wedpr.anonymousvoting.proto.StringToIntPair;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersShareRequest;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousvoting.proto.VoteResultRequest;
import com.webank.wedpr.anonymousvoting.proto.VoterState;
import com.webank.wedpr.anonymousvoting.proto.VotingChoices;
import com.webank.wedpr.anonymousvoting.proto.VotingChoices.Builder;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.anonymousvoting.DemoMain;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;
import org.fisco.bcos.web3j.tx.txdecode.ResultEntity;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

class VoteRequestParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String systemParameters;
    public List<String> votingRequestList;
    public List<CounterState> counterStateList;
    public List<String> hPointShareList;
}

class CountRequestParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String systemParameters;
    public String voteStorageSum;
    public List<String> hPointShareList;
    public String decryptedResultPartRequest;
}

@Data
class VoteResultParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String systemParameters;
    public String voteResultRequest;
}

public class PerfAnonymousVotingUtils {

    public static VoteRequestParams getVerifyBoundedVoteRequestParams() throws Exception {

        // 1 Deploy contract AnonymousVotingExamplePerf.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = DemoMain.getWeb3j(groupID);
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                AnonymousVotingExamplePerf.deploy(
                                web3j,
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();

        TransactionReceipt enableParallelTransactionReceipt =
                anonymousVotingExamplePerf.enableParallel().send();
        Utils.checkTranactionReceipt(enableParallelTransactionReceipt);

        String voterTableName =
                DemoMain.voterTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String counterTableName =
                DemoMain.counterTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String regulationInfoTableName =
                DemoMain.regulationInfoTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String voterAggregateTableName =
                DemoMain.voterAggregateTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String counterAggregateTableName =
                DemoMain.counterAggregateTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        anonymousVotingExamplePerf
                .init(
                        voterTableName,
                        counterTableName,
                        regulationInfoTableName,
                        voterAggregateTableName,
                        counterAggregateTableName)
                .send();

        // 2 init counter
        List<CounterState> counterStateList = DemoMain.initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        List<String> hPointShareList = new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CounterResult counterResult =
                    CounterClient.makeSystemParametersShare(
                            Utils.protoToEncodedString(counterStateList.get(i)));
            Utils.checkWedprResult(counterResult);
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(counterResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(DemoMain.COUNTER_ID_LIST.get(i), hPointShare)
                            .send();
            Utils.checkTranactionReceipt(insertHPointShareReceipt);
            hPointShareList.add(hPointShare);
        }

        // 3 coordinate init
        CoordinatorState coordinatorState = DemoMain.initCoordinator();

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.CANDIDATE_LIST).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        Utils.checkTranactionReceipt(nextContractStateReceipt);
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 voter init ==============================================
        List<VoterState> voterStateList = DemoMain.initVoterStateForBoundedVoting(hPoint);

        // 4.1 voter register
        List<String> registrationRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        SystemParametersStorage systemParameters =
                DemoMain.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeBoundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 coordinator certify
        List<String> registrationResponseList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyBoundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            DemoMain.BLANK_BALLOT_COUNT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            Builder votingChoicesBuilder = VotingChoices.newBuilder();
            for (int j = 0; j < candidates.size(); j++) {
                votingChoicesBuilder.addChoice(
                        StringToIntPair.newBuilder()
                                .setKey(candidates.get(j))
                                .setValue(DemoMain.VOTING_BALLOT_COUNT[i][j]));
            }
            VotingChoices votingChoices = votingChoicesBuilder.build();
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteBounded(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(votingChoices),
                            registrationResponse);
            Utils.checkWedprResult(voterResult);
            votingRequestList.add(voterResult.voteRequest);
        }

        VoteRequestParams boundedVoteRequestParams = new VoteRequestParams();
        boundedVoteRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        boundedVoteRequestParams.systemParameters = Utils.protoToEncodedString(systemParameters);
        boundedVoteRequestParams.votingRequestList = votingRequestList;
        boundedVoteRequestParams.counterStateList = counterStateList;
        boundedVoteRequestParams.hPointShareList = hPointShareList;

        return boundedVoteRequestParams;
    }

    public static VoteRequestParams getVerifyUnboundedVoteRequestParams() throws Exception {

        // 1 Deploy contract AnonymousVotingExamplePerf.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = DemoMain.getWeb3j(groupID);
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                AnonymousVotingExamplePerf.deploy(
                                web3j,
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();

        TransactionReceipt enableParallelTransactionReceipt =
                anonymousVotingExamplePerf.enableParallel().send();
        Utils.checkTranactionReceipt(enableParallelTransactionReceipt);
        String voterTableName =
                DemoMain.voterTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String counterTableName =
                DemoMain.counterTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String regulationInfoTableName =
                DemoMain.regulationInfoTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String voterAggregateTableName =
                DemoMain.voterAggregateTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        String counterAggregateTableName =
                DemoMain.counterAggregateTableName
                        + anonymousVotingExamplePerf.getContractAddress().substring(2, 10);
        anonymousVotingExamplePerf
                .init(
                        voterTableName,
                        counterTableName,
                        regulationInfoTableName,
                        voterAggregateTableName,
                        counterAggregateTableName)
                .send();

        // 2 init counter
        List<CounterState> counterStateList = DemoMain.initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        List<String> localHPointShareList = new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CounterResult counterResult =
                    CounterClient.makeSystemParametersShare(
                            Utils.protoToEncodedString(counterStateList.get(i)));
            Utils.checkWedprResult(counterResult);
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(counterResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(DemoMain.COUNTER_ID_LIST.get(i), hPointShare)
                            .send();
            Utils.checkTranactionReceipt(insertHPointShareReceipt);
            localHPointShareList.add(hPointShare);
        }

        // 3 coordinate init
        CoordinatorState coordinatorState = DemoMain.initCoordinator();

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.CANDIDATE_LIST).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        Utils.checkTranactionReceipt(nextContractStateReceipt);
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 voter init ==============================================
        List<VoterState> voterStateList = DemoMain.initVoterStateForUnboundedVoting(hPoint);

        // 4.1 voter register
        List<String> registrationRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        SystemParametersStorage systemParameters =
                DemoMain.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeUnboundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 coordinator certify
        List<String> registrationResponseList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyUnboundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            DemoMain.BLANK_BALLOT_WEIGHT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            Builder votingChoicesBuilder = VotingChoices.newBuilder();
            for (int j = 0; j < candidates.size(); j++) {
                votingChoicesBuilder.addChoice(
                        StringToIntPair.newBuilder()
                                .setKey(candidates.get(j))
                                .setValue(DemoMain.VOTING_BALLOT_WEIGHT[i][j]));
            }
            VotingChoices votingChoices = votingChoicesBuilder.build();
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteUnbounded(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(votingChoices),
                            registrationResponse);
            Utils.checkWedprResult(voterResult);
            votingRequestList.add(voterResult.voteRequest);
        }

        VoteRequestParams boundedVoteRequestParams = new VoteRequestParams();
        boundedVoteRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        boundedVoteRequestParams.systemParameters = Utils.protoToEncodedString(systemParameters);
        boundedVoteRequestParams.votingRequestList = votingRequestList;

        return boundedVoteRequestParams;
    }

    public static CountRequestParams getCountRequestParams() throws Exception {
        VoteRequestParams boundedVoteRequestParams = getVerifyBoundedVoteRequestParams();
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                boundedVoteRequestParams.anonymousVotingExamplePerf;
        List<CounterState> counterStateList = boundedVoteRequestParams.counterStateList;
        List<String> hPointShareList = boundedVoteRequestParams.hPointShareList;
        List<String> votingRequestList = boundedVoteRequestParams.votingRequestList;
        String encodedSystemParameters = boundedVoteRequestParams.systemParameters;

        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            anonymousVotingExamplePerf
                    .verifyBoundedVoteRequest(Utils.getUuid(), voteRequest, encodedSystemParameters)
                    .send();
        }
        boolean aggregateVoteStorageResult = true;
        do {
            aggregateVoteStorageResult =
                    aggregateVoteStorage(anonymousVotingExamplePerf, encodedSystemParameters);
        } while (aggregateVoteStorageResult);
        anonymousVotingExamplePerf.nextContractState().send();

        // counter counting
        String voteStorageSum = anonymousVotingExamplePerf.getVoteStorageSum().send();
        List<String> decryptedResultPartRequestList =
                new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(
                            Utils.protoToEncodedString(counterStateList.get(i)), voteStorageSum);
            Utils.checkWedprResult(counterResult);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }

        CountRequestParams countRequestParams = new CountRequestParams();
        countRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        countRequestParams.systemParameters = encodedSystemParameters;
        countRequestParams.hPointShareList = hPointShareList;
        countRequestParams.voteStorageSum = voteStorageSum;
        countRequestParams.decryptedResultPartRequest = decryptedResultPartRequestList.get(0);

        return countRequestParams;
    }

    public static VoteResultParams getVerifyVoteResultParams() throws Exception {
        VoteRequestParams boundedVoteRequestParams = getVerifyBoundedVoteRequestParams();
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                boundedVoteRequestParams.anonymousVotingExamplePerf;
        List<CounterState> counterStateList = boundedVoteRequestParams.counterStateList;
        List<String> hPointShareList = boundedVoteRequestParams.hPointShareList;
        List<String> votingRequestList = boundedVoteRequestParams.votingRequestList;
        String encodedSystemParameters = boundedVoteRequestParams.systemParameters;
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            anonymousVotingExamplePerf
                    .verifyBoundedVoteRequest(Utils.getUuid(), voteRequest, encodedSystemParameters)
                    .send();
        }
        boolean aggregateVoteStorageResult = true;
        do {
            aggregateVoteStorageResult =
                    aggregateVoteStorage(anonymousVotingExamplePerf, encodedSystemParameters);
        } while (aggregateVoteStorageResult);

        anonymousVotingExamplePerf.nextContractState().send();

        // counter counting
        String voteStorageSum = anonymousVotingExamplePerf.getVoteStorageSum().send();
        List<String> decryptedResultPartRequestList =
                new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(
                            Utils.protoToEncodedString(counterStateList.get(i)), voteStorageSum);
            Utils.checkWedprResult(counterResult);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }
        // 6.1 blockchain verify count request
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            TransactionReceipt verifyCountRequestReceipt =
                    anonymousVotingExamplePerf
                            .verifyCountRequest(
                                    Utils.getUuid(),
                                    voteStorageSum,
                                    hPointShareList.get(i),
                                    decryptedResultPartRequestList.get(i),
                                    encodedSystemParameters)
                            .send();
            Utils.checkTranactionReceipt(verifyCountRequestReceipt);
        }
        boolean aggregateDecryptedPartResult = true;
        do {
            aggregateDecryptedPartResult =
                    aggregateDecryptedPart(anonymousVotingExamplePerf, encodedSystemParameters);
        } while (aggregateDecryptedPartResult);

        anonymousVotingExamplePerf.nextContractState().send();

        // counter count ballot
        String decryptedResultPartStorageSum =
                anonymousVotingExamplePerf.getDecryptedResultPartStorageSum().send();
        CounterResult counterResult =
                CounterClient.finalizeVoteResult(
                        encodedSystemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSum,
                        DemoMain.MAX_VOTE_NUMBER);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(counterResult.voteResultRequest));

        VoteResultParams voteResultParams = new VoteResultParams();
        voteResultParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        voteResultParams.systemParameters = encodedSystemParameters;
        voteResultParams.voteResultRequest = Utils.protoToEncodedString(voteResultRequest);

        return voteResultParams;
    }

    private static boolean aggregateVoteStorage(
            AnonymousVotingExamplePerf anonymousVotingExamplePerf, String systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVotingExamplePerf.aggregateVoteStorage(systemParameters).send();
        TransactionDecoder transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(
                        AnonymousVotingExamplePerf.ABI, "");
        List<ResultEntity> receiptOutputResult =
                Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);
        return (Boolean) receiptOutputResult.get(0).getData();
    }

    private static boolean aggregateDecryptedPart(
            AnonymousVotingExamplePerf anonymousVotingExamplePerf, String systemParameters)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousVotingExamplePerf.aggregateDecryptedPart(systemParameters).send();
        Utils.checkTranactionReceipt(transactionReceipt);
        TransactionDecoder transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(
                        AnonymousVotingExamplePerf.ABI, "");
        List<ResultEntity> receiptOutputResult =
                Utils.getReceiptOutputResult(transactionDecoder, transactionReceipt);
        return (Boolean) receiptOutputResult.get(0).getData();
    }
}

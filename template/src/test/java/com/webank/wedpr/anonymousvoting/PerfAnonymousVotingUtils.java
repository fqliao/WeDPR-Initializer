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
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.anonymousvoting.DemoMain;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;

class VoteRequestParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String voterTableName;
    public SystemParametersStorage systemParameters;
    public List<String> votingRequestList;
    public CounterClient counterClient;
    public List<CounterState> counterStateList;
    public List<String> hPointShareList;
    public String counterTableName;
}

class CountRequestParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String counterTableName;
    public String systemParameters;
    public String voteStorageSumTotal;
    public List<String> hPointShareList;
    public String decryptedResultPartRequest;
}

@Data
class VoteResultParams {
    public AnonymousVotingExamplePerf anonymousVotingExamplePerf;
    public String systemParameters;
    public String voteStorageSumTotal;
    public String decryptedResultPartStorageSumTotal;
    public String voteResultRequest;
}

public class PerfAnonymousVotingUtils {

    public static VoteRequestParams getVerifyBoundedVoteRequestParams() throws Exception {

        // 1 Deploy contract AnonymousVotingExamplePerf.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = Utils.getWeb3j(groupID);
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                AnonymousVotingExamplePerf.deploy(
                                web3j,
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();
        String voterTableName = "voter_example_" + anonymousVotingExamplePerf.getContractAddress();
        String counterTableName =
                "counter_example_" + anonymousVotingExamplePerf.getContractAddress();
        anonymousVotingExamplePerf.init(voterTableName, counterTableName).send();

        // 2 init counter
        List<CounterState> counterStateList = DemoMain.initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        CounterClient counterClient = new CounterClient();
        List<String> hPointShareList = new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CountResult countResult =
                    counterClient.makeSystemParametersShare(
                            Utils.protoToEncodedString(counterStateList.get(i)));
            if (Utils.hasWedprError(countResult)) {
                throw new WedprException(countResult.wedprErrorMessage);
            }
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(countResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(
                                    counterTableName, DemoMain.COUNTER_ID_LIST.get(i), hPointShare)
                            .send();
            if (!Utils.isTransactionSucceeded(insertHPointShareReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(insertHPointShareReceipt));
            }
            hPointShareList.add(hPointShare);
        }

        // 3 coordinate init
        CoordinatorState coordinatorState = DemoMain.initCoordinator();

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.CANDIDATE_LIST).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 voter init ==============================================
        List<VoterState> voterStateList = DemoMain.initVoterStateForBoundedVoting(hPoint);

        // 4.1 voter register
        VoterClient voterClient = new VoterClient();
        List<String> registrationRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        SystemParametersStorage systemParameters =
                DemoMain.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoteResult voteResult =
                    voterClient.makeBoundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voteResult.registrationRequest);
        }

        // 4.2 coordinator certify
        CoordinatorClient coordinatorClient = new CoordinatorClient();
        List<String> registrationResponseList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            CoordinateResult coordinateResult =
                    coordinatorClient.certifyBoundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            DemoMain.BLANK_BALLOT_COUNT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinateResult.registrationResponse);
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
            VoteResult voteResult =
                    voterClient.voteBounded(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(votingChoices),
                            registrationResponse);
            if (Utils.hasWedprError(voteResult)) {
                throw new WedprException(voteResult.wedprErrorMessage);
            }
            votingRequestList.add(voteResult.voteRequest);
        }

        VoteRequestParams boundedVoteRequestParams = new VoteRequestParams();
        boundedVoteRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        boundedVoteRequestParams.systemParameters = systemParameters;
        boundedVoteRequestParams.voterTableName = voterTableName;
        boundedVoteRequestParams.counterTableName = counterTableName;
        boundedVoteRequestParams.votingRequestList = votingRequestList;
        boundedVoteRequestParams.counterClient = counterClient;
        boundedVoteRequestParams.counterStateList = counterStateList;
        boundedVoteRequestParams.hPointShareList = hPointShareList;

        return boundedVoteRequestParams;
    }

    public static VoteRequestParams getVerifyUnboundedVoteRequestParams() throws Exception {

        // 1 Deploy contract AnonymousVotingExamplePerf.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = Utils.getWeb3j(groupID);
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                AnonymousVotingExamplePerf.deploy(
                                web3j,
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();
        String voterTableName = "voter_example_" + anonymousVotingExamplePerf.getContractAddress();
        String counterTableName =
                "counter_example_" + anonymousVotingExamplePerf.getContractAddress();
        anonymousVotingExamplePerf.init(voterTableName, counterTableName).send();

        // 2 init counter
        List<CounterState> counterStateList = DemoMain.initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        CounterClient counterClient = new CounterClient();
        List<String> localHPointShareList = new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CountResult countResult =
                    counterClient.makeSystemParametersShare(
                            Utils.protoToEncodedString(counterStateList.get(i)));
            if (Utils.hasWedprError(countResult)) {
                throw new WedprException(countResult.wedprErrorMessage);
            }
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(countResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(
                                    counterTableName, DemoMain.COUNTER_ID_LIST.get(i), hPointShare)
                            .send();
            if (!Utils.isTransactionSucceeded(insertHPointShareReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(insertHPointShareReceipt));
            }
            localHPointShareList.add(hPointShare);
        }

        // 3 coordinate init
        CoordinatorState coordinatorState = DemoMain.initCoordinator();

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.CANDIDATE_LIST).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 voter init ==============================================
        List<VoterState> voterStateList = DemoMain.initVoterStateForUnboundedVoting(hPoint);

        // 4.1 voter register
        VoterClient voterClient = new VoterClient();
        List<String> registrationRequestList = new ArrayList<>(DemoMain.VOTER_COUNT);
        SystemParametersStorage systemParameters =
                DemoMain.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoteResult voteResult =
                    voterClient.makeUnboundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voteResult.registrationRequest);
        }

        // 4.2 coordinator certify
        CoordinatorClient coordinatorClient = new CoordinatorClient();
        List<String> registrationResponseList = new ArrayList<>(DemoMain.VOTER_COUNT);
        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            CoordinateResult coordinateResult =
                    coordinatorClient.certifyUnboundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            DemoMain.BLANK_BALLOT_WEIGHT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinateResult.registrationResponse);
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
            VoteResult voteResult =
                    voterClient.voteUnbounded(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(votingChoices),
                            registrationResponse);
            if (Utils.hasWedprError(voteResult)) {
                throw new WedprException(voteResult.wedprErrorMessage);
            }
            votingRequestList.add(voteResult.voteRequest);
        }

        VoteRequestParams boundedVoteRequestParams = new VoteRequestParams();
        boundedVoteRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        boundedVoteRequestParams.systemParameters = systemParameters;
        boundedVoteRequestParams.voterTableName = voterTableName;
        boundedVoteRequestParams.votingRequestList = votingRequestList;

        return boundedVoteRequestParams;
    }

    public static CountRequestParams getCountRequestParams() throws Exception {
        VoteRequestParams boundedVoteRequestParams = getVerifyBoundedVoteRequestParams();
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                boundedVoteRequestParams.anonymousVotingExamplePerf;
        CounterClient counterClient = boundedVoteRequestParams.counterClient;
        List<CounterState> counterStateList = boundedVoteRequestParams.counterStateList;
        List<String> hPointShareList = boundedVoteRequestParams.hPointShareList;
        List<String> votingRequestList = boundedVoteRequestParams.votingRequestList;
        SystemParametersStorage systemParameters = boundedVoteRequestParams.systemParameters;

        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            anonymousVotingExamplePerf
                    .verifyBoundedVoteRequest(
                            boundedVoteRequestParams.voterTableName,
                            Utils.protoToEncodedString(systemParameters),
                            voteRequest)
                    .send();
        }
        anonymousVotingExamplePerf.nextContractState().send();

        // counter counting
        String voteStorageSumTotal = anonymousVotingExamplePerf.getVoteStorageSumTotal().send();
        List<String> decryptedResultPartRequestList =
                new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CountResult countResult =
                    counterClient.count(
                            Utils.protoToEncodedString(counterStateList.get(i)),
                            voteStorageSumTotal);
            if (Utils.hasWedprError(countResult)) {
                throw new WedprException(countResult.wedprErrorMessage);
            }
            decryptedResultPartRequestList.add(countResult.decryptedResultPartRequest);
        }

        CountRequestParams countRequestParams = new CountRequestParams();
        countRequestParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        countRequestParams.counterTableName = boundedVoteRequestParams.counterTableName;
        countRequestParams.systemParameters = Utils.protoToEncodedString(systemParameters);
        countRequestParams.hPointShareList = hPointShareList;
        countRequestParams.voteStorageSumTotal = voteStorageSumTotal;
        countRequestParams.decryptedResultPartRequest = decryptedResultPartRequestList.get(0);

        return countRequestParams;
    }

    public static VoteResultParams getVerifyVoteResultParams() throws Exception {
        VoteRequestParams boundedVoteRequestParams = getVerifyBoundedVoteRequestParams();
        AnonymousVotingExamplePerf anonymousVotingExamplePerf =
                boundedVoteRequestParams.anonymousVotingExamplePerf;
        CounterClient counterClient = boundedVoteRequestParams.counterClient;
        List<CounterState> counterStateList = boundedVoteRequestParams.counterStateList;
        List<String> hPointShareList = boundedVoteRequestParams.hPointShareList;
        List<String> votingRequestList = boundedVoteRequestParams.votingRequestList;
        SystemParametersStorage systemParameters = boundedVoteRequestParams.systemParameters;

        for (int i = 0; i < DemoMain.VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            anonymousVotingExamplePerf
                    .verifyBoundedVoteRequest(
                            boundedVoteRequestParams.voterTableName,
                            Utils.protoToEncodedString(systemParameters),
                            voteRequest)
                    .send();
        }
        anonymousVotingExamplePerf.nextContractState().send();

        // counter counting
        String voteStorageSumTotal = anonymousVotingExamplePerf.getVoteStorageSumTotal().send();
        List<String> decryptedResultPartRequestList =
                new ArrayList<>(DemoMain.COUNTER_ID_LIST.size());
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            CountResult countResult =
                    counterClient.count(
                            Utils.protoToEncodedString(counterStateList.get(i)),
                            voteStorageSumTotal);
            if (Utils.hasWedprError(countResult)) {
                throw new WedprException(countResult.wedprErrorMessage);
            }
            decryptedResultPartRequestList.add(countResult.decryptedResultPartRequest);
        }
        // 6.1 blockchain verify count request
        for (int i = 0; i < DemoMain.COUNTER_ID_LIST.size(); i++) {
            TransactionReceipt verifyCountRequestReceipt =
                    anonymousVotingExamplePerf
                            .verifyCountRequest(
                                    boundedVoteRequestParams.counterTableName,
                                    Utils.protoToEncodedString(systemParameters),
                                    voteStorageSumTotal,
                                    hPointShareList.get(i),
                                    decryptedResultPartRequestList.get(i))
                            .send();
            if (!Utils.isTransactionSucceeded(verifyCountRequestReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(verifyCountRequestReceipt));
            }
        }
        anonymousVotingExamplePerf.nextContractState().send();

        // counter count ballot
        String decryptedResultPartStorageSumTotal =
                anonymousVotingExamplePerf.getDecryptedResultPartStorageSumTotal().send();
        CountResult countResult =
                counterClient.finalizeVoteResult(
                        Utils.protoToEncodedString(systemParameters),
                        voteStorageSumTotal,
                        decryptedResultPartStorageSumTotal,
                        DemoMain.MAX_VOTE_NUMBER);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(countResult.voteResultRequest));

        VoteResultParams voteResultParams = new VoteResultParams();
        voteResultParams.anonymousVotingExamplePerf = anonymousVotingExamplePerf;
        voteResultParams.systemParameters = Utils.protoToEncodedString(systemParameters);
        voteResultParams.voteStorageSumTotal = voteStorageSumTotal;
        voteResultParams.decryptedResultPartStorageSumTotal = decryptedResultPartStorageSumTotal;
        voteResultParams.voteResultRequest = Utils.protoToEncodedString(voteResultRequest);

        return voteResultParams;
    }
}

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
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.UtilsForTest;
import com.webank.wedpr.example.anonymousvoting.DemoMain;
import java.util.ArrayList;
import java.util.List;
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
        Web3j web3j = UtilsForTest.getWeb3j(groupID);
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
                DemoMain.voterTableName + Utils.truncateAddress(anonymousVotingExamplePerf);
        String counterTableName =
                DemoMain.counterTableName + Utils.truncateAddress(anonymousVotingExamplePerf);
        String regulationInfoTableName =
                DemoMain.regulationInfoTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        String voterAggregateTableName =
                DemoMain.voterAggregateTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        String counterAggregateTableName =
                DemoMain.counterAggregateTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        anonymousVotingExamplePerf
                .init(
                        voterTableName,
                        counterTableName,
                        voterAggregateTableName,
                        counterAggregateTableName,
                        regulationInfoTableName)
                .send();

        // 2 Generate counter state
        int size = DemoMain.counter_id_list.size();
        List<CounterState> counterStateList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String counterId = DemoMain.counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }

        // 2.1 counter make hPointShare and upload hPointShare
        List<String> hPointShareList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CounterResult counterResult =
                    CounterClient.makeSystemParametersShare(counterStateList.get(i));
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(counterResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(DemoMain.counter_id_list.get(i), hPointShare)
                            .send();
            Utils.checkTranactionReceipt(insertHPointShareReceipt);
            hPointShareList.add(hPointShare);
        }

        // 3 Generate coordinate state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.candidate_list).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        Utils.checkTranactionReceipt(nextContractStateReceipt);
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 Generate voter state
        int voter_count = DemoMain.voter_count;
        List<VoterState> voterStateList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String secret = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteBounded(secret, hPoint);
            voterStateList.add(voterState);
        }

        // 4.1 voter register
        List<String> registrationRequestList = new ArrayList<>(DemoMain.voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeBoundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 coordinator certify
        List<String> registrationResponseList = new ArrayList<>(DemoMain.voter_count);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyBoundedVoter(
                            coordinatorState,
                            DemoMain.blank_ballot_count[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(DemoMain.voter_count);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            VotingChoices votingChoices =
                    AnonymousvotingUtils.makeVotingChoices(
                            candidates, DemoMain.voting_ballot_count[i]);
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteBounded(voterState, votingChoices, registrationResponse);
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
        Web3j web3j = UtilsForTest.getWeb3j(groupID);
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
                DemoMain.voterTableName + Utils.truncateAddress(anonymousVotingExamplePerf);
        String counterTableName =
                DemoMain.counterTableName + Utils.truncateAddress(anonymousVotingExamplePerf);
        String regulationInfoTableName =
                DemoMain.regulationInfoTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        String voterAggregateTableName =
                DemoMain.voterAggregateTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        String counterAggregateTableName =
                DemoMain.counterAggregateTableName
                        + Utils.truncateAddress(anonymousVotingExamplePerf);
        anonymousVotingExamplePerf
                .init(
                        voterTableName,
                        counterTableName,
                        voterAggregateTableName,
                        counterAggregateTableName,
                        regulationInfoTableName)
                .send();

        // 2 Generate counter state
        int size = DemoMain.counter_id_list.size();
        List<CounterState> counterStateList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String counterId = DemoMain.counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }

        // 2.1 counter make hPointShare and upload hPointShare
        List<String> localHPointShareList = new ArrayList<>(DemoMain.counter_id_list.size());
        for (int i = 0; i < size; i++) {
            CounterResult counterResult =
                    CounterClient.makeSystemParametersShare(counterStateList.get(i));
            SystemParametersShareRequest systemParametersShareRequest =
                    SystemParametersShareRequest.parseFrom(
                            Utils.stringToBytes(counterResult.systemParametersShareRequest));
            String hPointShare = systemParametersShareRequest.getHPointShare();
            TransactionReceipt insertHPointShareReceipt =
                    anonymousVotingExamplePerf
                            .insertHPointShare(DemoMain.counter_id_list.get(i), hPointShare)
                            .send();
            Utils.checkTranactionReceipt(insertHPointShareReceipt);
            localHPointShareList.add(hPointShare);
        }

        // 3 Generate coordinate state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 3.1 save candidates on blockchain
        anonymousVotingExamplePerf.setCandidates(DemoMain.candidate_list).send();
        TransactionReceipt nextContractStateReceipt =
                anonymousVotingExamplePerf.nextContractState().send();
        Utils.checkTranactionReceipt(nextContractStateReceipt);
        // 3.2 query hPoint from blockchain
        String hPoint = anonymousVotingExamplePerf.getHPoint().send();

        // 3.3 query candidates from blockchain
        List<String> candidates = anonymousVotingExamplePerf.getCandidates().send();

        // 4 Generate voter state
        List<VoterState> voterStateList = new ArrayList<>(DemoMain.voter_count);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            String secretR = Utils.getSecretString();
            String secretZeroR = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteUnbounded(
                            secretR, secretZeroR, hPoint);
            voterStateList.add(voterState);
        }

        // 4.1 voter register
        List<String> registrationRequestList = new ArrayList<>(DemoMain.voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeUnboundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 coordinator certify
        List<String> registrationResponseList = new ArrayList<>(DemoMain.voter_count);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyUnboundedVoter(
                            coordinatorState,
                            DemoMain.blank_ballot_weight[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(DemoMain.voter_count);
        for (int i = 0; i < DemoMain.voter_count; i++) {
            Builder votingChoicesBuilder = VotingChoices.newBuilder();
            for (int j = 0; j < candidates.size(); j++) {
                votingChoicesBuilder.addChoice(
                        StringToIntPair.newBuilder()
                                .setKey(candidates.get(j))
                                .setValue(DemoMain.voting_ballot_weight[i][j]));
            }
            VotingChoices votingChoices = votingChoicesBuilder.build();
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteUnbounded(voterState, votingChoices, registrationResponse);
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

        for (int i = 0; i < DemoMain.voter_count; i++) {
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
                new ArrayList<>(DemoMain.counter_id_list.size());
        for (int i = 0; i < DemoMain.counter_id_list.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
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
        SystemParametersStorage systemParameters =
                SystemParametersStorage.parseFrom(Utils.stringToBytes(encodedSystemParameters));
        for (int i = 0; i < DemoMain.voter_count; i++) {
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
                new ArrayList<>(DemoMain.counter_id_list.size());
        for (int i = 0; i < DemoMain.counter_id_list.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }
        // 6.1 blockchain verify count request
        for (int i = 0; i < DemoMain.counter_id_list.size(); i++) {
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
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSum,
                        DemoMain.max_vote_number);
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

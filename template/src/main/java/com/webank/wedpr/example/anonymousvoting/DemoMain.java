package com.webank.wedpr.example.anonymousvoting;

import static org.junit.Assert.assertTrue;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;

import com.google.protobuf.InvalidProtocolBufferException;
import com.webank.pkeygen.exception.PkeyGenException;
import com.webank.wedpr.anonymousvoting.AnonyousvotingUtils;
import com.webank.wedpr.anonymousvoting.CoordinateResult;
import com.webank.wedpr.anonymousvoting.CoordinatorClient;
import com.webank.wedpr.anonymousvoting.CountResult;
import com.webank.wedpr.anonymousvoting.CounterClient;
import com.webank.wedpr.anonymousvoting.VoteResult;
import com.webank.wedpr.anonymousvoting.VoterClient;
import com.webank.wedpr.anonymousvoting.proto.CoordinatorState;
import com.webank.wedpr.anonymousvoting.proto.CounterState;
import com.webank.wedpr.anonymousvoting.proto.StringToInt64Pair;
import com.webank.wedpr.anonymousvoting.proto.StringToIntPair;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersShareRequest;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousvoting.proto.VoteResultRequest;
import com.webank.wedpr.anonymousvoting.proto.VoteResultStorage;
import com.webank.wedpr.anonymousvoting.proto.VoterState;
import com.webank.wedpr.anonymousvoting.proto.VotingChoices;
import com.webank.wedpr.anonymousvoting.proto.VotingChoices.Builder;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.SecretKey;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;

/**
 * @author caryliao
 * @date 2019/10/17
 */
public class DemoMain {

    public static List<String> CANDIDATE_LIST = Arrays.asList("Kitten", "Doge", "Bunny");
    public static List<String> COUNTER_ID_LIST = Arrays.asList("10086", "10010");

    public static int VOTER_COUNT = 3;
    /**
     * Blank ballot count for bounded voting for example. Voter1 to voter3 is allocated 10, 20 and
     * 30 blank ballots respectively.
     */
    public static int[] BLANK_BALLOT_COUNT = {10, 20, 30};

    /**
     * Voting ballot for bounded voting for example. Voter1 vote 1, 2, 3 for candidate1 to
     * candidate3 respectively. Voter2 vote 2, 3, 4 for candidate1 to candidate3 respectively.
     * Voter3 vote 3, 4, 5 for candidate1 to candidate3 respectively. Candidate1 to candidate3 will
     * get 6, 9 and 12 ballots respectively.
     */
    public static int[][] VOTING_BALLOT_COUNT = {{1, 2, 3}, {2, 3, 4}, {3, 4, 5}};

    /**
     * Blank ballot count for unbounded voting for example. Voter1 to voter3 is allocated 10, 20 and
     * 30 weight for blank ballot respectively.
     */
    public static int[] BLANK_BALLOT_WEIGHT = {10, 20, 30};

    /**
     * Voting ballot for unbounded voting for example. Voter1 vote 10, 10, 10 for candidate1 to
     * candidate3 respectively. Voter2 vote 0, 0, 0 for candidate1 to candidate3 respectively.
     * Voter3 vote 30, 30, 30 for candidate1 to candidate3 respectively. Candidate1 to candidate3
     * will get 40, 40 and 40 ballots respectively.
     */
    public static int[][] VOTING_BALLOT_WEIGHT = {{10, 0, 10}, {0, 20, 20}, {30, 30, 30}};

    public static long MAX_VOTE_NUMBER = 10000;

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            if ("voteBounded".equals(args[0])) {
                doVoteBounded();
            }
            if ("voteUnbounded".equals(args[0])) {
                doVoteUnbounded();
            }
        } else {
            System.out.println(
                    "Please provide one parameter, such as 'boundedVote' or 'unboundedVote'. ");
            System.exit(-1);
        }
        System.exit(0);
    }

    public static void doVoteBounded() throws Exception {
        // 1 init blockchain
        StorageExampleClient storageClient = initBlockchain();

        // 2 init counter
        List<CounterState> counterStateList = initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        CounterClient counterClient = new CounterClient();
        List<String> localHPointShareList =
                uploadHPointShare(storageClient, counterStateList, counterClient);

        // 2.2 query hPointShare from blockchain
        List<String> hPointShareList = queryHPointShareList(storageClient, localHPointShareList);

        // 3 coordinate init
        CoordinatorState coordinatorState = initCoordinator();

        // 3.1 save candidates on blockchain
        TransactionReceipt setCandidatesReceipt = storageClient.setCandidates(CANDIDATE_LIST);
        if (!Utils.isTransactionSucceeded(setCandidatesReceipt)) {
            throw new WedprException("Blockchain sets candidates failed!");
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from Initializing to Voting.
        TransactionReceipt nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 3.2 query hPoint from blockchain
        String hPoint = storageClient.getHPoint();

        // 3.3 query candidates from blockchain
        List<String> candidates = storageClient.getCandidates();
        assertTrue("Query candidates failed.", candidates.equals(CANDIDATE_LIST));

        // 4 voter init ==============================================
        List<VoterState> voterStateList;
        voterStateList = initVoterStateForBoundedVoting(hPoint);

        // 4.1 voter register
        VoterClient voterClient = new VoterClient();
        List<String> registrationRequestList = new ArrayList<>(VOTER_COUNT);
        SystemParametersStorage systemParameters = makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoteResult voteResult =
                    voterClient.makeBoundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voteResult.registrationRequest);
        }

        // 4.2 coordinator certify
        CoordinatorClient coordinatorClient = new CoordinatorClient();
        List<String> registrationResponseList = new ArrayList<>(VOTER_COUNT);
        for (int i = 0; i < VOTER_COUNT; i++) {
            CoordinateResult coordinateResult =
                    coordinatorClient.certifyBoundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            BLANK_BALLOT_COUNT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinateResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(VOTER_COUNT);
        for (int i = 0; i < VOTER_COUNT; i++) {
            Builder votingChoicesBuilder = VotingChoices.newBuilder();
            for (int j = 0; j < candidates.size(); j++) {
                votingChoicesBuilder.addChoice(
                        StringToIntPair.newBuilder()
                                .setKey(candidates.get(j))
                                .setValue(VOTING_BALLOT_COUNT[i][j]));
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

        // 5.1 blockchain verify vote request
        for (int i = 0; i < VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyBoundedVoteRequest(
                            Utils.protoToEncodedString(systemParameters), voteRequest);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            String blankBallot =
                    (String)
                            Utils.getReceiptOutputResult(storageClient.getTransactionDecoder(), verifyVoteRequestReceipt)
                                    .get(0)
                                    .getData();
            System.out.println("Save the blankBallot:" + blankBallot);
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from Voting to CountingStep1.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 6 counter counting
        String voteStorageSumTotal = storageClient.getVoteStorageSumTotal();
        List<String> decryptedResultPartRequestList = new ArrayList<>(COUNTER_ID_LIST.size());
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
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
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
            TransactionReceipt verifyCountRequestReceipt =
                    storageClient.verifyCountRequest(
                            Utils.protoToEncodedString(systemParameters),
                            voteStorageSumTotal,
                            hPointShareList.get(i),
                            decryptedResultPartRequestList.get(i));
            if (!Utils.isTransactionSucceeded(verifyCountRequestReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(verifyCountRequestReceipt));
            }
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from CountingStep1 to CountingStep2.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 7 counter count ballot
        String decryptedResultPartStorageSumTotal =
                storageClient.getDecryptedResultPartStorageSumTotal();
        CountResult countResult =
                counterClient.finalizeVoteResult(
                        Utils.protoToEncodedString(systemParameters),
                        voteStorageSumTotal,
                        decryptedResultPartStorageSumTotal,
                        MAX_VOTE_NUMBER);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(countResult.voteResultRequest));

        // 8 verify vote result and save vote result to blockchain
        TransactionReceipt verifyVoteResultReceipt =
                storageClient.verifyVoteResult(
                        Utils.protoToEncodedString(systemParameters),
                        voteStorageSumTotal,
                        decryptedResultPartStorageSumTotal,
                        Utils.protoToEncodedString(voteResultRequest));
        if (!Utils.isTransactionSucceeded(verifyVoteResultReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(verifyVoteResultReceipt));
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from CountingStep2 to End.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 9 query vote result storage from blockchain.
        String encodedVoteResultStorage = storageClient.getVoteResultStorage();
        VoteResultStorage voteResultStorage =
                VoteResultStorage.parseFrom(Utils.stringToBytes(encodedVoteResultStorage));
        List<StringToInt64Pair> resultList = voteResultStorage.getResultList();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);
    }

    public static void doVoteUnbounded() throws Exception {
        // 1 init blockchain
        StorageExampleClient storageClient = initBlockchain();

        // 2 init counter
        List<CounterState> counterStateList = initCounter();

        // 2.1 counter make hPointShare and upload hPointShare
        CounterClient counterClient = new CounterClient();
        List<String> localHPointShareList =
                uploadHPointShare(storageClient, counterStateList, counterClient);

        // 2.2 query hPointShare from blockchain
        List<String> hPointShareList = queryHPointShareList(storageClient, localHPointShareList);

        // 3 coordinate init
        CoordinatorState coordinatorState = initCoordinator();

        // 3.1 save candidates on blockchain
        TransactionReceipt setCandidatesReceipt = storageClient.setCandidates(CANDIDATE_LIST);
        if (!Utils.isTransactionSucceeded(setCandidatesReceipt)) {
            throw new WedprException("Blockchain sets candidates failed!");
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from Initializing to Voting.
        TransactionReceipt nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 3.2 query hPoint from blockchain
        String hPoint = storageClient.getHPoint();

        // 3.3 query candidates from blockchain
        List<String> candidates = storageClient.getCandidates();
        assertTrue("Query candidates failed.", candidates.equals(CANDIDATE_LIST));

        // 4 voter init ==============================================
        List<VoterState> voterStateList;
        voterStateList = initVoterStateForUnboundedVoting(hPoint);

        // 4.1 voter register
        VoterClient voterClient = new VoterClient();
        List<String> registrationRequestList = new ArrayList<>(VOTER_COUNT);
        SystemParametersStorage systemParameters = makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < VOTER_COUNT; i++) {
            VoterState voterState = voterStateList.get(i);
            VoteResult voteResult =
                    voterClient.makeUnboundedRegistrationRequest(
                            Utils.protoToEncodedString(voterState),
                            Utils.protoToEncodedString(systemParameters));
            registrationRequestList.add(voteResult.registrationRequest);
        }

        // 4.2 coordinator certify
        CoordinatorClient coordinatorClient = new CoordinatorClient();
        List<String> registrationResponseList = new ArrayList<>(VOTER_COUNT);

        for (int i = 0; i < VOTER_COUNT; i++) {
            CoordinateResult coordinateResult =
                    coordinatorClient.certifyUnboundedVoter(
                            Utils.protoToEncodedString(coordinatorState),
                            BLANK_BALLOT_WEIGHT[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinateResult.registrationResponse);
        }

        // 5 voter vote
        List<String> votingRequestList = new ArrayList<>(VOTER_COUNT);

        for (int i = 0; i < VOTER_COUNT; i++) {
            Builder votingChoicesBuilder = VotingChoices.newBuilder();
            for (int j = 0; j < candidates.size(); j++) {
                votingChoicesBuilder.addChoice(
                        StringToIntPair.newBuilder()
                                .setKey(candidates.get(j))
                                .setValue(VOTING_BALLOT_WEIGHT[i][j]));
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

        // 5.1 blockchain verify vote request
        for (int i = 0; i < VOTER_COUNT; i++) {
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyUnboundedVoteRequest(
                            Utils.protoToEncodedString(systemParameters), voteRequest);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            String blankBallot =
                    (String)
                            Utils.getReceiptOutputResult(storageClient.getTransactionDecoder(), verifyVoteRequestReceipt)
                                    .get(0)
                                    .getData();
            System.out.println("Save the blankBallot:" + blankBallot);
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from Voting to CountingStep1.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 6 counter counting
        String voteStorageSumTotal = storageClient.getVoteStorageSumTotal();
        List<String> decryptedResultPartRequestList = new ArrayList<>(COUNTER_ID_LIST.size());
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
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
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
            TransactionReceipt verifyCountRequestReceipt =
                    storageClient.verifyCountRequest(
                            Utils.protoToEncodedString(systemParameters),
                            voteStorageSumTotal,
                            hPointShareList.get(i),
                            decryptedResultPartRequestList.get(i));
            if (!Utils.isTransactionSucceeded(verifyCountRequestReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(verifyCountRequestReceipt));
            }
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from CountingStep1 to CountingStep2.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 7 counter count ballot
        String decryptedResultPartStorageSumTotal =
                storageClient.getDecryptedResultPartStorageSumTotal();
        CountResult countResult =
                counterClient.finalizeVoteResult(
                        Utils.protoToEncodedString(systemParameters),
                        voteStorageSumTotal,
                        decryptedResultPartStorageSumTotal,
                        MAX_VOTE_NUMBER);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(countResult.voteResultRequest));

        // 8 verify vote result and save vote result to blockchain
        TransactionReceipt verifyVoteResultReceipt =
                storageClient.verifyVoteResult(
                        Utils.protoToEncodedString(systemParameters),
                        voteStorageSumTotal,
                        decryptedResultPartStorageSumTotal,
                        Utils.protoToEncodedString(voteResultRequest));
        if (!Utils.isTransactionSucceeded(verifyVoteResultReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(verifyVoteResultReceipt));
        }

        // NOTICE: The owner(coordinator or other administrator) who deploys the anonymous voting
        // contract can
        // change the contract state.
        // Contract state change from CountingStep2 to End.
        nextContractStateReceipt = storageClient.nextContractState();
        if (!Utils.isTransactionSucceeded(nextContractStateReceipt)) {
            throw new WedprException(Utils.getReceiptOutputError(nextContractStateReceipt));
        }

        // 9 query vote result storage from blockchain.
        String encodedVoteResultStorage = storageClient.getVoteResultStorage();
        VoteResultStorage voteResultStorage =
                VoteResultStorage.parseFrom(Utils.stringToBytes(encodedVoteResultStorage));
        List<StringToInt64Pair> resultList = voteResultStorage.getResultList();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);
    }

    public static SystemParametersStorage makeSystemParameters(
            String hPoint, List<String> candidates) {
        SystemParametersStorage.Builder systemParametersStorageBuilder =
                SystemParametersStorage.newBuilder();
        systemParametersStorageBuilder.setHPoint(hPoint);
        for (int i = 0; i < candidates.size(); i++) {
            systemParametersStorageBuilder.addCandidates(candidates.get(i));
        }
        SystemParametersStorage systemParameters = systemParametersStorageBuilder.build();
        return systemParameters;
    }

    public static CoordinatorState initCoordinator()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
                    NoSuchProviderException {
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                CoordinatorState.newBuilder()
                        .setSecretKey(encodedKeyPair.getSecretKey())
                        .setPublicKey(encodedKeyPair.getPublicKey())
                        .build();
        return coordinatorState;
    }

    private static List<String> queryHPointShareList(
            StorageExampleClient storageExampleClient, List<String> localHPointShareList)
            throws Exception {
        List<String> hPointShareList = new ArrayList<>(COUNTER_ID_LIST.size());
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
            String counterId = COUNTER_ID_LIST.get(i);
            List<String> counterPartShareList = storageExampleClient.queryHPointShare(counterId);
            assertTrue(
                    "Query counter " + counterId + " hPointShare is empty.",
                    counterPartShareList.size() == 1);
            String hPointShare = counterPartShareList.get(0);
            assertTrue(
                    "Query counter " + counterId + " hPointShare is error result.",
                    hPointShare.equals(localHPointShareList.get(i)));
            hPointShareList.add(hPointShare);
        }
        return hPointShareList;
    }

    private static List<String> uploadHPointShare(
            StorageExampleClient storageExampleClient,
            List<CounterState> counterStateList,
            CounterClient counterClient)
            throws WedprException, InvalidProtocolBufferException, Exception {
        List<String> localHPointShareList = new ArrayList<>(COUNTER_ID_LIST.size());
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
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
                    storageExampleClient.insertHPointShare(COUNTER_ID_LIST.get(i), hPointShare);
            if (!Utils.isTransactionSucceeded(insertHPointShareReceipt)) {
                throw new WedprException(Utils.getReceiptOutputError(insertHPointShareReceipt));
            }
            localHPointShareList.add(hPointShare);
        }
        return localHPointShareList;
    }

    public static List<CounterState> initCounter() throws PkeyGenException {
        List<CounterState> counterStateList = new ArrayList<>(COUNTER_ID_LIST.size());
        for (int i = 0; i < COUNTER_ID_LIST.size(); i++) {
            String counterId = COUNTER_ID_LIST.get(i);
            String counterShare = Utils.getSecretKey(Utils.getSecret()).getSecretKey();
            CounterState counterState =
                    CounterState.newBuilder()
                            .setCounterId(counterId)
                            .setSecretShare(counterShare)
                            .build();
            counterStateList.add(counterState);
        }
        return counterStateList;
    }

    private static StorageExampleClient initBlockchain()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
                    NoSuchProviderException, Exception {
        // blockchain init
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        AnonymousVotingExample anonymousVotingExample =
                AnonyousvotingUtils.deployContract(ecKeyPair, groupID);

        String voterTableName = "voter_example_" + anonymousVotingExample.getContractAddress();
        String counterTableName = "counter_example_" + anonymousVotingExample.getContractAddress();
        System.out.println("###voterTableName:" + voterTableName);
        System.out.println("###counterTableName:" + counterTableName);

        StorageExampleClient storageClient =
                new StorageExampleClient(anonymousVotingExample, voterTableName, counterTableName);

        storageClient.init();
        return storageClient;
    }

    public static List<VoterState> initVoterStateForUnboundedVoting(String hPoint)
            throws PkeyGenException {
        List<VoterState> voterStateList = new ArrayList<>(VOTER_COUNT);
        for (int i = 0; i < VOTER_COUNT; i++) {
            SecretKey secretKey = Utils.getSecretKey(Utils.getSecret());
            SecretKey secretZeroKey = Utils.getSecretKey(Utils.getSecret());
            String secretR = secretKey.getSecretKey();
            String secretZeroR = secretZeroKey.getSecretKey();
            VoterState voterState =
                    VoterState.newBuilder()
                            .setSecretR(secretR)
                            .setSecretZeroR(secretZeroR)
                            .setHPoint(hPoint)
                            .build();
            voterStateList.add(voterState);
        }
        return voterStateList;
    }

    public static List<VoterState> initVoterStateForBoundedVoting(String hPoint)
            throws PkeyGenException {
        List<VoterState> voterStateList = new ArrayList<>(VOTER_COUNT);
        for (int i = 0; i < VOTER_COUNT; i++) {
            SecretKey secretKey = Utils.getSecretKey(Utils.getSecret());
            String secretR = secretKey.getSecretKey();
            VoterState voterState =
                    VoterState.newBuilder().setSecretR(secretR).setHPoint(hPoint).build();
            voterStateList.add(voterState);
        }
        return voterStateList;
    }
}

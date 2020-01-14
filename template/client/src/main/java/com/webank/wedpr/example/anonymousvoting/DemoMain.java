package com.webank.wedpr.example.anonymousvoting;

import com.webank.wedpr.anonymousvoting.AnonymousvotingUtils;
import com.webank.wedpr.anonymousvoting.CoordinatorClient;
import com.webank.wedpr.anonymousvoting.CoordinatorResult;
import com.webank.wedpr.anonymousvoting.CounterClient;
import com.webank.wedpr.anonymousvoting.CounterResult;
import com.webank.wedpr.anonymousvoting.VerifierClient;
import com.webank.wedpr.anonymousvoting.VoterClient;
import com.webank.wedpr.anonymousvoting.VoterResult;
import com.webank.wedpr.anonymousvoting.proto.CoordinatorState;
import com.webank.wedpr.anonymousvoting.proto.CounterState;
import com.webank.wedpr.anonymousvoting.proto.RegistrationResponse;
import com.webank.wedpr.anonymousvoting.proto.RegulationInfo;
import com.webank.wedpr.anonymousvoting.proto.StringToInt64Pair;
import com.webank.wedpr.anonymousvoting.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousvoting.proto.VoteResultRequest;
import com.webank.wedpr.anonymousvoting.proto.VoteResultStorage;
import com.webank.wedpr.anonymousvoting.proto.VoterState;
import com.webank.wedpr.anonymousvoting.proto.VotingChoices;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * @author caryliao
 * @date 2019/10/17
 */
public class DemoMain {

    public static String voterTableName = "voter_";
    public static String voterAggregateTableName = "voter_aggregate_";
    public static String counterTableName = "counter_";
    public static String counterAggregateTableName = "counter_aggregate_";
    public static String regulationInfoTableName = "regulation_info_";

    public static List<String> candidate_list = Arrays.asList("Kitten", "Doge", "Bunny");
    public static List<String> counter_id_list = Arrays.asList("10086", "10010");

    public static int voter_count = 3;
    /**
     * Blank ballot count for bounded voting for example. Voter1 to voter3 is allocated 10, 20 and
     * 30 blank ballots respectively.
     */
    public static int[] blank_ballot_count = {10, 20, 30};

    /**
     * Voting ballot for bounded voting for example. Voter1 vote 1, 2, 3 for candidate1 to
     * candidate3 respectively. Voter2 vote 2, 3, 4 for candidate1 to candidate3 respectively.
     * Voter3 vote 3, 4, 5 for candidate1 to candidate3 respectively. Candidate1 to candidate3 will
     * get 6, 9 and 12 ballots respectively.
     */
    public static int[][] voting_ballot_count = {{1, 2, 3}, {2, 3, 4}, {3, 4, 5}};

    /**
     * Blank ballot count for unbounded voting for example. Voter1 to voter3 is allocated 10, 20 and
     * 30 weight for blank ballot respectively.
     */
    public static int[] blank_ballot_weight = {10, 20, 30};

    /**
     * Voting ballot for unbounded voting for example. Voter1 vote 10, 10, 10 for candidate1 to
     * candidate3 respectively. Voter2 vote 0, 0, 0 for candidate1 to candidate3 respectively.
     * Voter3 vote 30, 30, 30 for candidate1 to candidate3 respectively. Candidate1 to candidate3
     * will get 40, 40 and 40 ballots respectively.
     */
    public static int[][] voting_ballot_weight = {{10, 0, 10}, {0, 20, 20}, {30, 30, 30}};

    public static long max_vote_number = 61;

    // NOTICE:The regulator secret key should be saved by regulator.
    // In the example, set the variable just used to decrypt regulation information for users.
    public static byte[] regulatorSecretKey;
    public static byte[] regulatorPublicKey;
    public static PublicKeyCrypto publicKeyCrypto;

    public static void main(String[] args) throws Exception {
        // (Optional) Regulator init keypair.
        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();
        regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        publicKeyCrypto = new PublicKeyCryptoExample();

        if (args.length == 1) {
            if ("voteBounded".equals(args[0])) {
                doVoteBounded();
            } else if ("voteUnbounded".equals(args[0])) {
                doVoteUnbounded();
            } else {
                System.out.println(
                        "Please provide one parameter, such as 'voteBounded' or 'voteUnbounded'.");
                System.exit(-1);
            }
        } else {
            System.out.println(
                    "Please provide one parameter, such as 'voteBounded' or 'voteUnbounded'.");
            System.exit(-1);
        }
        System.exit(0);
    }

    public static void doVoteBounded() throws Exception {
        // 1 Coordinator or other organization deploy contract
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AnonymousvotingUtils.initContract(
                        ecKeyPair,
                        groupID,
                        voterTableName,
                        voterAggregateTableName,
                        counterTableName,
                        counterAggregateTableName,
                        regulationInfoTableName);

        // 2 Generate counter state
        List<CounterState> counterStateList = new ArrayList<>(counter_id_list.size());
        for (int i = 0; i < counter_id_list.size(); i++) {
            String counterId = counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }

        // 2.1 Counter make hPointShare and upload hPointShare
        AnonymousvotingUtils.uploadHPointShare(storageClient, counterStateList, counter_id_list);

        // 2.2 Query hPointShare from blockchain
        List<String> hPointShareList =
                AnonymousvotingUtils.queryHPointShareList(storageClient, counter_id_list);

        // 3 Generate coordinate state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 3.1 Save candidates on blockchain
        storageClient.setCandidates(candidate_list);

        // Contract state change from Initializing to Voting.
        storageClient.nextContractState();

        // 3.2 Query hPoint from blockchain
        String hPoint = storageClient.getHPoint();

        // 3.3 Query candidates from blockchain
        List<String> candidates = storageClient.getCandidates();

        // 4 Generate voter state
        List<VoterState> voterStateList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String secret = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteBounded(secret, hPoint);
            voterStateList.add(voterState);
        }

        // 4.1 Voter register
        List<String> registrationRequestList = new ArrayList<>(voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeBoundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 Coordinator certify
        List<String> registrationResponseList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyBoundedVoter(
                            coordinatorState,
                            blank_ballot_count[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);

            RegistrationResponse decodedRegistrationResponse =
                    RegistrationResponse.parseFrom(
                            Utils.stringToBytes(coordinatorResult.registrationResponse));
            System.out.println("registrationResponse:" + decodedRegistrationResponse);
        }

        // 4.3 Verifier verify blank ballot
        for (int i = 0; i < voter_count; i++) {
            VerifierClient.verifyBlankBallot(
                    registrationRequestList.get(i), registrationResponseList.get(i));
        }
        System.out.println("Verify voter blank ballot successful.\n");

        // 5 Voter vote
        List<String> votingRequestList = new ArrayList<>(voter_count);
        List<VotingChoices> votingChoicesList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            VotingChoices votingChoices =
                    AnonymousvotingUtils.makeVotingChoices(candidates, voting_ballot_count[i]);
            votingChoicesList.add(votingChoices);
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteBounded(voterState, votingChoices, registrationResponse);
            votingRequestList.add(voterResult.voteRequest);
        }

        List<RegulationInfo> regulationInfos = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            RegulationInfo regulationInfo =
                    AnonymousvotingUtils.makeRegulationInfos(votingChoicesList.get(i));
            regulationInfos.add(regulationInfo);
        }

        // 5.1 Blockchain verify vote request
        List<String> blankBallots = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyBoundedVoteRequest(voteRequest, systemParameters);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            String blankBallot =
                    (String)
                            Utils.getReceiptOutputResult(
                                            storageClient.getTransactionDecoder(),
                                            verifyVoteRequestReceipt)
                                    .get(0)
                                    .getData();
            blankBallots.add(blankBallot);
            System.out.println("Save the blankBallot:" + blankBallot);

            // (Optional) Upload regulation information to blockchain.
            byte[] regulationInfo = regulationInfos.get(i).toByteArray();
            AnonymousvotingUtils.uploadRegulationInfo(
                    storageClient,
                    publicKeyCrypto,
                    regulatorPublicKey,
                    blankBallot,
                    regulationInfo);
        }
        boolean aggregateVoteStorageResult = true;
        do {
            aggregateVoteStorageResult = storageClient.aggregateVoteStorage(systemParameters);
        } while (aggregateVoteStorageResult);

        // Contract state change from Voting to CountingStep1.
        storageClient.nextContractState();

        // 6 Counter counting
        String voteStorageSum = storageClient.getVoteStorageSum();
        List<String> decryptedResultPartRequestList = new ArrayList<>(counter_id_list.size());
        for (int i = 0; i < counter_id_list.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }

        // 6.1 Blockchain verify count request
        for (int i = 0; i < counter_id_list.size(); i++) {

            storageClient.verifyCountRequest(
                    hPointShareList.get(i),
                    decryptedResultPartRequestList.get(i),
                    voteStorageSum,
                    systemParameters);
        }

        boolean aggregateDecryptedPartResult = true;
        do {
            aggregateDecryptedPartResult = storageClient.aggregateDecryptedPart(systemParameters);
        } while (aggregateDecryptedPartResult);

        // Contract state change from CountingStep1 to CountingStep2.
        storageClient.nextContractState();

        // 7 Counter count ballot
        String decryptedResultPartStorageSumTotal =
                storageClient.getDecryptedResultPartStorageSumTotal();
        CounterResult counterResult =
                CounterClient.finalizeVoteResult(
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSumTotal,
                        max_vote_number);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(counterResult.voteResultRequest));

        // 8 Verify vote result and save vote result to blockchain
        storageClient.verifyVoteResult(voteResultRequest, systemParameters);

        // Contract state change from CountingStep2 to End.
        storageClient.nextContractState();

        // 9 Query vote result storage from blockchain.
        String encodedVoteResultStorage = storageClient.getVoteResultStorage();
        VoteResultStorage voteResultStorage =
                VoteResultStorage.parseFrom(Utils.stringToBytes(encodedVoteResultStorage));
        List<StringToInt64Pair> resultList = voteResultStorage.getResultList();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);

        // (Optional) Queries regulation information for example.
        AnonymousvotingUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, blankBallots);
    }

    public static void doVoteUnbounded() throws Exception {
        // 1 Coordinator or other organization deploy contract
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AnonymousvotingUtils.initContract(
                        ecKeyPair,
                        groupID,
                        voterTableName,
                        voterAggregateTableName,
                        counterTableName,
                        counterAggregateTableName,
                        regulationInfoTableName);

        // 2 Generate counter state
        List<CounterState> counterStateList = new ArrayList<>(counter_id_list.size());
        for (int i = 0; i < counter_id_list.size(); i++) {
            String counterId = counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }

        // 2.1 Counter make hPointShare and upload hPointShare
        AnonymousvotingUtils.uploadHPointShare(storageClient, counterStateList, counter_id_list);

        // 2.2 Query hPointShare from blockchain
        List<String> hPointShareList =
                AnonymousvotingUtils.queryHPointShareList(storageClient, counter_id_list);

        // 3 Generate coordinate state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 3.1 Save candidates on blockchain
        storageClient.setCandidates(candidate_list);

        // Contract state change from Initializing to Voting.
        storageClient.nextContractState();

        // 3.2 Query hPoint from blockchain
        String hPoint = storageClient.getHPoint();

        // 3.3 Query candidates from blockchain
        List<String> candidates = storageClient.getCandidates();

        // 4 Generate voter state
        List<VoterState> voterStateList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String secretR = Utils.getSecretString();
            String secretZeroR = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteUnbounded(
                            secretR, secretZeroR, hPoint);
            voterStateList.add(voterState);
        }

        // 4.1 Voter register
        List<String> registrationRequestList = new ArrayList<>(voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeUnboundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }

        // 4.2 Coordinator certify
        List<String> registrationResponseList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyUnboundedVoter(
                            coordinatorState,
                            blank_ballot_weight[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);

            RegistrationResponse decodedRegistrationResponse =
                    RegistrationResponse.parseFrom(
                            Utils.stringToBytes(coordinatorResult.registrationResponse));
            System.out.println("registrationResponse:" + decodedRegistrationResponse);
        }

        // 4.3 Verifier verify blank ballot
        for (int i = 0; i < voter_count; i++) {
            VerifierClient.verifyBlankBallot(
                    registrationRequestList.get(i), registrationResponseList.get(i));
        }
        System.out.println("Verify voter blank ballot successful.\n");

        // 5 Voter vote
        List<String> votingRequestList = new ArrayList<>(voter_count);
        List<VotingChoices> votingChoicesList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            VotingChoices votingChoices =
                    AnonymousvotingUtils.makeVotingChoices(candidates, voting_ballot_weight[i]);
            votingChoicesList.add(votingChoices);
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            VoterResult voterResult =
                    VoterClient.voteUnbounded(voterState, votingChoices, registrationResponse);
            Utils.checkWedprResult(voterResult);
            votingRequestList.add(voterResult.voteRequest);
        }

        List<RegulationInfo> regulationInfos = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            RegulationInfo regulationInfo =
                    AnonymousvotingUtils.makeRegulationInfos(votingChoicesList.get(i));
            regulationInfos.add(regulationInfo);
        }

        // 5.1 Blockchain verify vote request
        List<String> blankBallots = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyUnboundedVoteRequest(voteRequest, systemParameters);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            String blankBallot =
                    (String)
                            Utils.getReceiptOutputResult(
                                            storageClient.getTransactionDecoder(),
                                            verifyVoteRequestReceipt)
                                    .get(0)
                                    .getData();
            blankBallots.add(blankBallot);
            System.out.println("Save the blankBallot:" + blankBallot);

            // (Optional) Upload regulation information to blockchain.
            byte[] regulationInfo = regulationInfos.get(i).toByteArray();
            AnonymousvotingUtils.uploadRegulationInfo(
                    storageClient,
                    publicKeyCrypto,
                    regulatorPublicKey,
                    blankBallot,
                    regulationInfo);
        }

        boolean aggregateVoteStorageResult = true;
        do {
            aggregateVoteStorageResult = storageClient.aggregateVoteStorage(systemParameters);
        } while (aggregateVoteStorageResult);

        // Contract state change from Voting to CountingStep1.
        storageClient.nextContractState();

        // 6 Counter counting
        String voteStorageSum = storageClient.getVoteStorageSum();
        List<String> decryptedResultPartRequestList = new ArrayList<>(counter_id_list.size());
        for (int i = 0; i < counter_id_list.size(); i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }

        // 6.1 Blockchain verify count request
        for (int i = 0; i < counter_id_list.size(); i++) {

            storageClient.verifyCountRequest(
                    hPointShareList.get(i),
                    decryptedResultPartRequestList.get(i),
                    voteStorageSum,
                    systemParameters);
        }

        boolean aggregateDecryptedPartResult = true;
        do {
            aggregateDecryptedPartResult = storageClient.aggregateDecryptedPart(systemParameters);
        } while (aggregateDecryptedPartResult);

        // Contract state change from CountingStep1 to CountingStep2.
        storageClient.nextContractState();

        // 7 Counter count ballot
        String decryptedResultPartStorageSumTotal =
                storageClient.getDecryptedResultPartStorageSumTotal();
        CounterResult counterResult =
                CounterClient.finalizeVoteResult(
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSumTotal,
                        max_vote_number);
        VoteResultRequest voteResultRequest =
                VoteResultRequest.parseFrom(Utils.stringToBytes(counterResult.voteResultRequest));

        // 8 Verify vote result and save vote result to blockchain
        storageClient.verifyVoteResult(voteResultRequest, systemParameters);

        // Contract state change from CountingStep2 to End.
        storageClient.nextContractState();

        // 9 Query vote result storage from blockchain.
        String encodedVoteResultStorage = storageClient.getVoteResultStorage();
        VoteResultStorage voteResultStorage =
                VoteResultStorage.parseFrom(Utils.stringToBytes(encodedVoteResultStorage));
        List<StringToInt64Pair> resultList = voteResultStorage.getResultList();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);

        // (Optional) Queries regulation information for example.
        AnonymousvotingUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, blankBallots);
    }
}

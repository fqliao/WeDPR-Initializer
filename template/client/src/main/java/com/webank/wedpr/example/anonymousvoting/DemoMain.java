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
    public static int[] bounded_blank_ballot_value = {10, 20, 30};

    /**
     * Voting ballot for bounded voting for example. Voter1 vote 1, 2, 3 for candidate1 to
     * candidate3 respectively. Voter2 vote 2, 3, 4 for candidate1 to candidate3 respectively.
     * Voter3 vote 3, 4, 5 for candidate1 to candidate3 respectively. Candidate1 to candidate3 will
     * get 6, 9 and 12 ballots respectively.
     */
    public static int[][] bounded_voting_ballot_value = {{1, 2, 3}, {2, 3, 4}, {3, 4, 5}};

    /**
     * Blank ballot count for unbounded voting for example. Voter1 to voter3 is allocated 10, 20 and
     * 30 weight for blank ballot respectively.
     */
    public static int[] unbounded_blank_ballot_value = {10, 20, 30};

    /**
     * Voting ballot for unbounded voting for example. Voter1 vote 10, 10, 10 for candidate1 to
     * candidate3 respectively. Voter2 vote 0, 0, 0 for candidate1 to candidate3 respectively.
     * Voter3 vote 30, 30, 30 for candidate1 to candidate3 respectively. Candidate1 to candidate3
     * will get 40, 40 and 40 ballots respectively.
     */
    public static int[][] unbounded_voting_ballot_value = {{10, 0, 10}, {0, 20, 20}, {30, 30, 30}};

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

        //////////////////////////
        // 1 Coordinator settings
        /////////////////////////

        // 1.1 Coordinator makes state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 1.2 Coordinator deploys contract and create anonymous voting table
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

        // 1.3 Coordinator uploads candidates on blockchain
        storageClient.uploadCandidates(candidate_list);

        //////////////////////
        // 2 Counter settings
        /////////////////////

        // 2.1 Counter makes counter state
        int size = counter_id_list.size();
        List<CounterState> counterStateList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String counterId = counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }
        // 2.2 Counter uploads hPointShare
        List<String> hPointShareList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String hPointShare = AnonymousvotingUtils.getHPointShare(counterStateList.get(i));
            storageClient.uploadHPointShare(counter_id_list.get(i), hPointShare);
            hPointShareList.add(hPointShare);
        }

        // Contract state:Initializing -> Voting
        storageClient.nextContractState();

        ////////////////////
        // 3 Voter settings
        ////////////////////

        // 3.1 Voter queries hPoint from blockchain
        String hPoint = storageClient.queryHPoint();
        // 3.2 Voter makes state
        List<VoterState> voterStateList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String secret = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteBounded(secret, hPoint);
            voterStateList.add(voterState);
        }

        ////////////////////
        // 4 Voter registers
        ////////////////////

        // 4.1 Voter queries candidates from blockchain
        List<String> candidates = storageClient.queryCandidates();
        // 4.2 Voter makes register request
        List<String> registrationRequestList = new ArrayList<>(voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeBoundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }
        // 4.3 Coordinator certifies
        List<String> registrationResponseList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyBoundedVoter(
                            coordinatorState,
                            bounded_blank_ballot_value[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }
        List<String> blankBallots = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            // 4.4 Voter gets blank ballot value from registrationResponse
            String encodedRegistrationResponse = registrationResponseList.get(i);
            RegistrationResponse registrationResponse =
                    RegistrationResponse.parseFrom(
                            Utils.stringToBytes(encodedRegistrationResponse));
            int blankBallotValue = registrationResponse.getVoterWeight();
            System.out.println("Blank ballot value:" + blankBallotValue);
            // 4.5 Voter gets blank ballot ciphertext from registrationResponse
            String blankBallot = Utils.protoToEncodedString(registrationResponse.getBallot());
            blankBallots.add(blankBallot);
            System.out.println("Blank ballot ciphertext:" + blankBallot);
            // 4.6 Voter calls verifier's api to verify blank ballot
            VerifierClient.verifyBlankBallot(
                    registrationRequestList.get(i), encodedRegistrationResponse);
        }
        System.out.println("Verify voter blank ballot successful.\n");

        ////////////////////
        // 5 Voter votes
        ////////////////////

        List<String> votingRequestList = new ArrayList<>(voter_count);
        List<VotingChoices> votingChoicesList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            // 5.1 Voter sets candidates and voting ballot value
            VotingChoices votingChoices =
                    AnonymousvotingUtils.makeVotingChoices(
                            candidates, bounded_voting_ballot_value[i]);
            votingChoicesList.add(votingChoices);
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            // 5.2 Voter sends voting request
            VoterResult voterResult =
                    VoterClient.voteBounded(voterState, votingChoices, registrationResponse);
            votingRequestList.add(voterResult.voteRequest);
        }
        // 5.3 (Optional) Voter makes regulationInfo
        List<RegulationInfo> regulationInfos = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            RegulationInfo regulationInfo =
                    AnonymousvotingUtils.makeRegulationInfos(votingChoicesList.get(i));
            regulationInfos.add(regulationInfo);
        }
        for (int i = 0; i < voter_count; i++) {
            // 5.4 Voter verifies vote request and then uploading voting ciphertext on blockchain if
            // the
            // request passed the validation.
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyBoundedVoteRequest(voteRequest, systemParameters);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            // 5.5 (Optional) Voter uploads regulation information to blockchain.
            AnonymousvotingUtils.uploadRegulationInfo(
                    storageClient,
                    publicKeyCrypto,
                    regulatorPublicKey,
                    blankBallots.get(i),
                    regulationInfos.get(i));
        }

        ////////////////////
        // 6 Counter counts
        ////////////////////
        // 6.1 Counter aggregates voteStorage
        storageClient.aggregateVoteStorage(systemParameters);

        // Contract state:Voting -> CountingStep1
        storageClient.nextContractState();

        // 6.2 Counter queries voteStorageSum from blockchain.
        String voteStorageSum = storageClient.queryVoteStorageSum();
        // 6.3 Counter sends counting request
        List<String> decryptedResultPartRequestList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }
        // 6.4 Counter verifies counting request and then uploading counting ciphertext on
        // blockchain if the
        // request passed the validation.
        for (int i = 0; i < size; i++) {
            storageClient.verifyCountRequest(
                    hPointShareList.get(i),
                    decryptedResultPartRequestList.get(i),
                    voteStorageSum,
                    systemParameters);
        }
        // 6.5 Counter aggregates decryptedPartResult.
        storageClient.aggregateDecryptedPart(systemParameters);

        // Contract state:CountingStep1 -> CountingStep2
        storageClient.nextContractState();

        // 6.6 Counter queries decryptedResultPartStorageSumTotal from blockchain
        String decryptedResultPartStorageSumTotal =
                storageClient.queryDecryptedResultPartStorageSumTotal();
        // 6.7 Counter counts ballot finally.
        CounterResult counterResult =
                CounterClient.finalizeVoteResult(
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSumTotal,
                        max_vote_number);

        // 6.8 Counter verifies vote result and then uploading vote result on blockchain if the
        // request passed the validation.
        String voteResultRequest = counterResult.voteResultRequest;
        storageClient.verifyVoteResult(voteResultRequest, systemParameters);

        // Contract state:CountingStep2 -> End
        storageClient.nextContractState();

        ////////////////////////////////////////
        // 7 Blockchain publishes voting results
        ////////////////////////////////////////
        // 7.1 Voter and other role can query vote result from blockchain
        List<StringToInt64Pair> resultList = storageClient.queryVoteResult();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);

        // 7.2 (Optional) Regulator can query regulation information
        AnonymousvotingUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, blankBallots);
    }

    public static void doVoteUnbounded() throws Exception {

        //////////////////////////
        // 1 Coordinator settings
        /////////////////////////

        // 1.1 Coordinator makes state
        EncodedKeyPair encodedKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState =
                AnonymousvotingUtils.makeCoordinatorState(encodedKeyPair);

        // 1.2 Coordinator deploys contract
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

        // 1.3 Coordinator uploads candidates on blockchain
        storageClient.uploadCandidates(candidate_list);

        //////////////////////
        // 2 Counter settings
        /////////////////////

        // 2.1 Counter makes counter state
        int size = counter_id_list.size();
        List<CounterState> counterStateList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String counterId = counter_id_list.get(i);
            String counterShare = Utils.getSecretString();
            CounterState counterState =
                    AnonymousvotingUtils.makeCounterState(counterId, counterShare);
            counterStateList.add(counterState);
        }
        // 2.2 Counter uploads hPointShare
        List<String> hPointShareList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String hPointShare = AnonymousvotingUtils.getHPointShare(counterStateList.get(i));
            storageClient.uploadHPointShare(counter_id_list.get(i), hPointShare);
            hPointShareList.add(hPointShare);
        }

        // Contract state:Initializing -> Voting
        storageClient.nextContractState();

        ////////////////////
        // 3 Voter settings
        ////////////////////

        // 3.1 Voter queries hPoint from blockchain
        String hPoint = storageClient.queryHPoint();
        // 3.2 Voter makes state
        List<VoterState> voterStateList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            String secret = Utils.getSecretString();
            String secretZeroR = Utils.getSecretString();
            VoterState voterState =
                    AnonymousvotingUtils.makeVoterStateForVoteUnbounded(
                            secret, secretZeroR, hPoint);
            voterStateList.add(voterState);
        }

        ////////////////////
        // 4 Voter registers
        ////////////////////

        // 4.1 Voter queries candidates from blockchain
        List<String> candidates = storageClient.queryCandidates();
        // 4.2 Voter registers
        List<String> registrationRequestList = new ArrayList<>(voter_count);
        SystemParametersStorage systemParameters =
                AnonymousvotingUtils.makeSystemParameters(hPoint, candidates);
        for (int i = 0; i < voter_count; i++) {
            VoterState voterState = voterStateList.get(i);
            VoterResult voterResult =
                    VoterClient.makeUnboundedRegistrationRequest(voterState, systemParameters);
            registrationRequestList.add(voterResult.registrationRequest);
        }
        // 4.3 Coordinator certifies
        List<String> registrationResponseList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            CoordinatorResult coordinatorResult =
                    CoordinatorClient.certifyUnboundedVoter(
                            coordinatorState,
                            unbounded_blank_ballot_value[i],
                            registrationRequestList.get(i));
            registrationResponseList.add(coordinatorResult.registrationResponse);
        }
        List<String> blankBallots = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            // 4.4 Voter gets blank ballot value from registrationResponse
            String encodedRegistrationResponse = registrationResponseList.get(i);
            RegistrationResponse registrationResponse =
                    RegistrationResponse.parseFrom(
                            Utils.stringToBytes(encodedRegistrationResponse));
            int blankBallotValue = registrationResponse.getVoterWeight();
            System.out.println("Blank ballot value:" + blankBallotValue);
            // 4.5 Voter gets blank ballot ciphertext from registrationResponse
            String blankBallot = Utils.protoToEncodedString(registrationResponse.getBallot());
            blankBallots.add(blankBallot);
            System.out.println("Blank ballot ciphertext:" + blankBallot);
            // 4.6 Voter calls verifier's api to verify blank ballot
            VerifierClient.verifyBlankBallot(
                    registrationRequestList.get(i), encodedRegistrationResponse);
        }
        System.out.println("Verify voter blank ballot successful.\n");

        ////////////////////
        // 5 Voter votes
        ////////////////////

        List<String> votingRequestList = new ArrayList<>(voter_count);
        List<VotingChoices> votingChoicesList = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            // 5.1 Voter sets candidates and voting ballot value
            VotingChoices votingChoices =
                    AnonymousvotingUtils.makeVotingChoices(
                            candidates, unbounded_voting_ballot_value[i]);
            votingChoicesList.add(votingChoices);
            VoterState voterState = voterStateList.get(i);
            String registrationResponse = registrationResponseList.get(i);
            // 5.2 Voter sends voting request
            VoterResult voterResult =
                    VoterClient.voteUnbounded(voterState, votingChoices, registrationResponse);
            votingRequestList.add(voterResult.voteRequest);
        }
        // 5.3 (Optional) Voter makes regulationInfo
        List<RegulationInfo> regulationInfos = new ArrayList<>(voter_count);
        for (int i = 0; i < voter_count; i++) {
            RegulationInfo regulationInfo =
                    AnonymousvotingUtils.makeRegulationInfos(votingChoicesList.get(i));
            regulationInfos.add(regulationInfo);
        }
        for (int i = 0; i < voter_count; i++) {
            // 5.4 Voter verifies vote request and then uploading voting ciphertext on blockchain if
            // the
            // request passed the validation.
            String voteRequest = votingRequestList.get(i);
            TransactionReceipt verifyVoteRequestReceipt =
                    storageClient.verifyUnboundedVoteRequest(voteRequest, systemParameters);
            if (!Utils.isTransactionSucceeded(verifyVoteRequestReceipt)) {
                throw new WedprException("Blockchain verify vote request" + (i + 1) + " failed!");
            }
            // 5.5 (Optional) Voter uploads regulation information to blockchain.
            AnonymousvotingUtils.uploadRegulationInfo(
                    storageClient,
                    publicKeyCrypto,
                    regulatorPublicKey,
                    blankBallots.get(i),
                    regulationInfos.get(i));
        }

        ////////////////////
        // 6 Counter counts
        ////////////////////
        // 6.1 Counter aggregates voteStorage
        storageClient.aggregateVoteStorage(systemParameters);

        // Contract state:Voting -> CountingStep1
        storageClient.nextContractState();

        // 6.2 Counter queries voteStorageSum from blockchain.
        String voteStorageSum = storageClient.queryVoteStorageSum();
        // 6.3 Counter sends counting request
        List<String> decryptedResultPartRequestList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            CounterResult counterResult =
                    CounterClient.count(counterStateList.get(i), voteStorageSum);
            decryptedResultPartRequestList.add(counterResult.decryptedResultPartRequest);
        }
        // 6.4 Counter verifies counting request and then uploading counting ciphertext on
        // blockchain if the
        // request passed the validation.
        for (int i = 0; i < size; i++) {
            storageClient.verifyCountRequest(
                    hPointShareList.get(i),
                    decryptedResultPartRequestList.get(i),
                    voteStorageSum,
                    systemParameters);
        }
        // 6.5 Counter aggregates decryptedPartResult.
        storageClient.aggregateDecryptedPart(systemParameters);

        // Contract state:CountingStep1 -> CountingStep2
        storageClient.nextContractState();

        // 6.6 Counter queries decryptedResultPartStorageSumTotal from blockchain
        String decryptedResultPartStorageSumTotal =
                storageClient.queryDecryptedResultPartStorageSumTotal();
        // 6.7 Counter counts ballot finally.
        CounterResult counterResult =
                CounterClient.finalizeVoteResult(
                        systemParameters,
                        voteStorageSum,
                        decryptedResultPartStorageSumTotal,
                        max_vote_number);

        // 6.8 Counter verifies vote result and then uploading vote result on blockchain if the
        // request passed the validation.
        String voteResultRequest = counterResult.voteResultRequest;
        storageClient.verifyVoteResult(voteResultRequest, systemParameters);

        // Contract state:CountingStep2 -> End
        storageClient.nextContractState();

        ////////////////////////////////////////
        // 7 Blockchain publishes voting results
        /////////////////////////////////////////
        // 7.1 Voter and other role can query vote result from blockchain
        List<StringToInt64Pair> resultList = storageClient.queryVoteResult();
        System.out.println("Vote result:");
        resultList.stream().forEach(System.out::println);

        // 7.2 (Optional) Regulator can query regulation information
        AnonymousvotingUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, blankBallots);
    }
}

package com.webank.wedpr.example.anonymousauction;

import com.webank.wedpr.anonymousauction.AuctionUtils;
import com.webank.wedpr.anonymousauction.BidType;
import com.webank.wedpr.anonymousauction.BidderClient;
import com.webank.wedpr.anonymousauction.BidderKeyPair;
import com.webank.wedpr.anonymousauction.BidderResult;
import com.webank.wedpr.anonymousauction.CoordinatorClient;
import com.webank.wedpr.anonymousauction.proto.AllBidStorageRequest;
import com.webank.wedpr.anonymousauction.proto.AuctionItem;
import com.webank.wedpr.anonymousauction.proto.BidComparisonResponse;
import com.webank.wedpr.anonymousauction.proto.BidderState;
import com.webank.wedpr.anonymousauction.proto.CoordinatorState;
import com.webank.wedpr.anonymousauction.proto.Credential;
import com.webank.wedpr.anonymousauction.proto.RegistrationResponse;
import com.webank.wedpr.anonymousauction.proto.SystemParametersStorage;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.fisco.bcos.web3j.crypto.ECKeyPair;

public class DemoMain {
    public static String bidderTableName = "bidder_";
    public static String bidderIdTableName = "bidderId_";
    public static String regulationInfoTableName = "regulation_info_";

    public static String title = "car";
    public static String description = "BMW X5";
    public static int bidderNum = 3;
    public static int[] bidValues = {50, 60, 70};

    public static byte[] regulatorSecretKey;
    public static byte[] regulatorPublicKey;
    public static PublicKeyCrypto publicKeyCrypto;

    // Test n bidder
    //  public static int[] bidValues = new int[bidderNum];
    //        static {
    //            for (int i = 1; i <= bidderNum; i++) {
    //                bidValues[i - 1] = i;
    //            }
    //        }

    public static void main(String[] args) throws Exception {
        // (Optional) Regulator init keypair.
        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();
        regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        publicKeyCrypto = new PublicKeyCryptoExample();

        if (args.length == 1) {
            if ("highest".equals(args[0])) {
                doAuction(BidType.HighestPriceBid);
            } else if ("lowest".equals(args[0])) {
                doAuction(BidType.LowestPriceBid);
            } else {
                System.out.println("Please provide one parameter, such as 'highest' or 'lowest'.");
                System.exit(-1);
            }
        } else {
            System.out.println("Please provide one parameter, such as 'highest' or 'lowest'.");
            System.exit(-1);
        }

        System.exit(0);
    }

    public static void doAuction(BidType bidType) throws Exception {

        //////////////////////////
        // 1 Coordinator settings
        /////////////////////////

        // 1.1 Coordinator makes state
        EncodedKeyPair coordinatorKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState = AuctionUtils.makeCoordinatorState(coordinatorKeyPair);

        // 1.2 Coordinator deploys contract and create anonymous auction table
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AuctionUtils.initContract(
                        ecKeyPair,
                        groupID,
                        bidderTableName,
                        bidderIdTableName,
                        regulationInfoTableName);

        // 1.3 Coordinator upload auction info to blockchain
        AuctionItem auctionItem = AuctionUtils.makeAuctionItem(title, description);
        storageClient.uploadAuctionInfo(bidType, auctionItem);
        System.out.println();
        System.out.println("Bid type:" + bidType);
        System.out.println(
                "Auction Item {title:"
                        + DemoMain.title
                        + ", description:"
                        + DemoMain.description
                        + "}");
        System.out.println();

        ////////////////////
        // 2 Bidder settings
        ////////////////////

        List<BidderState> bidderStateList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderKeyPair bidderKeyPair = AuctionUtils.generateBidderKeyPair();
            BidderState bidderState = AuctionUtils.makeBidderState(bidderKeyPair, bidValues[i]);
            bidderStateList.add(bidderState);
        }

        /////////////////////
        // 3 Bidder registers
        /////////////////////

        // 3.1 Bidder makes register request.
        List<String> registrationRequestList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult = BidderClient.register(bidderStateList.get(i));
            registrationRequestList.add(bidderResult.registrationRequest);
        }

        // 3.2 Coordinator certifies
        List<Credential> credentialList = new ArrayList<>(bidderNum);
        for (int i = 0; i < bidderNum; i++) {
            RegistrationResponse registrationResponse =
                    CoordinatorClient.certify(coordinatorState, registrationRequestList.get(i));
            Credential credential = registrationResponse.getCredential();
            credentialList.add(credential);
        }

        // Contract state:Initializing -> Bidding
        storageClient.nextContractState();

        /////////////////
        // 4 Bidder bids
        ////////////////

        SystemParametersStorage systemParameters = storageClient.querySystemParameters();
        List<String> bidRequestList = new ArrayList<>();
        List<String> bidderIdList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            // 4.1 Bidder makes bid request
            BidderResult bidderResult =
                    BidderClient.bid(
                            bidderStateList.get(i), credentialList.get(i), systemParameters);
            String bidRequest = bidderResult.bidRequest;
            bidRequestList.add(bidRequest);

            // 4.2 Bidder uploads bid storage
            String bidderId = storageClient.uploadBidStorage(bidRequest);
            bidderIdList.add(bidderId);

            // 4.3 (Optional) Bidder uploads regulation information on blockchain
            String bidderPublicKey = bidderStateList.get(i).getPublicKey();
            String encryptedRegulationInfo =
                    AuctionUtils.makeRegulationInfo(
                            publicKeyCrypto, regulatorPublicKey, bidValues[i]);
            storageClient.uploadRegulationInfo(bidderPublicKey, encryptedRegulationInfo);
        }

        /////////////////////
        // 5 Bidder compares
        ////////////////////

        // 5.1 Bidder queries bidStorage
        AllBidStorageRequest allBidStorageRequest = storageClient.queryAllBidStorage();

        // 5.2 Bidder compares
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.compare(
                            bidderStateList.get(i),
                            credentialList.get(i),
                            systemParameters,
                            allBidStorageRequest);
            storageClient.uploadBidComparisonStorage(
                    bidderIdList.get(i), bidderResult.bidComparisonRequest);
        }

        // Contract state: Bidding -> Claiming
        storageClient.nextContractState();

        /////////////////////////
        // 6 Bidder claim winner
        /////////////////////////

        // 6.1 Bidder queries bidComparisonStorage
        BidComparisonResponse bidComparisonResponse = storageClient.queryBidComparisonStorage();

        // 6.2 Bidder claim winner
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.claimWinner(
                            bidderStateList.get(i),
                            Utils.protoToEncodedString(bidComparisonResponse));
            long rank = bidderResult.rank;
            String winnerClaimRequest = bidderResult.winnerClaimRequest;
            System.out.println("rank:" + rank + " bidValue:" + bidValues[i]);
            storageClient.verifyWinner(winnerClaimRequest, allBidStorageRequest);
        }

        // Contract state: Claiming -> End
        storageClient.nextContractState();

        //////////////////////////////////////
        // 7 Blockchain publishes bid results
        //////////////////////////////////////
        // 7.1 Bidder and other role can query bid result from blockchain
        BidWinner bidWinner = storageClient.queryBidWinner();
        System.out.println("Winner public key:" + bidWinner.getPublicKey());
        System.out.println("Winner bid value:" + bidWinner.getBidValue());

        // 7.2 (Optional) Regulator queries regulation information for example
        List<String> publicKeyList =
                bidderStateList.stream().map(x -> x.getPublicKey()).collect(Collectors.toList());
        System.out.println("\nDecrypted the regulation information about auction is:\n");
        AuctionUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, publicKeyList);
    }
}

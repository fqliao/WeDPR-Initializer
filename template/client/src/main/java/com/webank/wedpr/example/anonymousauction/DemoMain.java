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
import com.webank.wedpr.anonymousauction.proto.BidStorage;
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

        // 1 Coordinator or other organization deploy contract
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AuctionUtils.initContract(
                        ecKeyPair,
                        groupID,
                        bidderTableName,
                        bidderIdTableName,
                        regulationInfoTableName);

        // 2 Generate coordinator state
        EncodedKeyPair coordinatorKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState = AuctionUtils.makeCoordinatorState(coordinatorKeyPair);

        // Generate bidder state
        List<BidderState> bidderStateList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderKeyPair bidderKeyPair = AuctionUtils.generateBidderKeyPair();
            BidderState bidderState = AuctionUtils.makeBidderState(bidderKeyPair, bidValues[i]);
            bidderStateList.add(bidderState);
        }

        // 3. Coordinator upload auction info to blockchain
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
        // 4 Bidder register
        List<String> registrationRequestList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult = BidderClient.register(bidderStateList.get(i));
            registrationRequestList.add(bidderResult.registrationRequest);
        }

        // 5 Coordinator certify
        List<Credential> credentialList = new ArrayList<>(bidderNum);
        for (int i = 0; i < bidderNum; i++) {
            RegistrationResponse registrationResponse =
                    CoordinatorClient.certify(coordinatorState, registrationRequestList.get(i));
            Credential credential = registrationResponse.getCredential();
            credentialList.add(credential);
        }

        // Contract State: Initializing -> Bidding
        storageClient.nextContractState();

        // 6 Bidder bid
        SystemParametersStorage systemParameters = storageClient.querySystemParameters();
        List<String> bidRequestList = new ArrayList<>();
        List<String> bidderIdList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.bid(
                            bidderStateList.get(i), credentialList.get(i), systemParameters);
            String bidRequest = bidderResult.bidRequest;
            bidRequestList.add(bidRequest);
            String bidderId = storageClient.uploadBidStorage(bidRequest);
            bidderIdList.add(bidderId);

            // (Optional) Upload regulation information to blockchain.
            String bidderPublicKey = bidderStateList.get(i).getPublicKey();
            String encryptedRegulationInfo =
                    AuctionUtils.makeRegulationInfo(
                            publicKeyCrypto, regulatorPublicKey, bidValues[i]);
            storageClient.insertRegulationInfo(bidderPublicKey, encryptedRegulationInfo);
        }

        // 7 Query bidStorage
        List<String> bidderIds = storageClient.queryAllBidderId();
        int size = bidderIds.size();
        List<BidStorage> bidStorageList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BidStorage bidStorage = storageClient.queryBidStorageByBidderId(bidderIds.get(i));
            bidStorageList.add(bidStorage);
        }
        AllBidStorageRequest allBidStorageRequest =
                AuctionUtils.makeAllBidStorageRequest(bidStorageList);

        // 8 Bidder compare
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

        // Contract State: Bidding -> Claiming
        storageClient.nextContractState();

        // 9 Bidder claim winner
        List<String> bidComparisonStorageList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String bidComparisonStorage =
                    storageClient.queryBidComparisonStorageByBidderId(bidderIds.get(i));
            bidComparisonStorageList.add(bidComparisonStorage);
        }
        BidComparisonResponse bidComparisonResponse =
                AuctionUtils.makeBidComparisonResponse(bidComparisonStorageList);
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

        // Contract State: Claiming -> End
        storageClient.nextContractState();

        BidWinner bidWinner = storageClient.queryBidWinner();
        System.out.println("Winner public key:" + bidWinner.getPublicKey());
        System.out.println("Winner bid value:" + bidWinner.getBidValue());

        // (Optional) Queries regulation information for example.
        List<String> publicKeyList =
                bidderStateList.stream().map(x -> x.getPublicKey()).collect(Collectors.toList());
        System.out.println("\nDecrypted the regulation information about auction is:\n");
        AuctionUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, publicKeyList);
    }
}

package com.webank.wedpr.example.anonymousauction;

import com.webank.wedpr.anonymousauction.AuctionUtils;
import com.webank.wedpr.anonymousauction.AuctioneerClient;
import com.webank.wedpr.anonymousauction.BidderClient;
import com.webank.wedpr.anonymousauction.BidderKeyPair;
import com.webank.wedpr.anonymousauction.BidderResult;
import com.webank.wedpr.anonymousauction.proto.AuctioneerState;
import com.webank.wedpr.anonymousauction.proto.BidComparisonResponse;
import com.webank.wedpr.anonymousauction.proto.BidResponse;
import com.webank.wedpr.anonymousauction.proto.BidStorage;
import com.webank.wedpr.anonymousauction.proto.BidderState;
import com.webank.wedpr.anonymousauction.proto.Credential;
import com.webank.wedpr.anonymousauction.proto.RegistrationResponse;
import com.webank.wedpr.anonymousauction.proto.SystemParametersStorage;
import com.webank.wedpr.anonymousauction.proto.WinnerClaimRequest;
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
    public static String regulationInfoTableName = "regulation_info_";

    public static int bidderNum = 3;
    //    public static int[] bidValue = {100, 101, 102};
    public static int[] bidValue = {10, 20, 30};
    public static int bidLength = 8;
    public static int soundness = 40;

    public static byte[] regulatorSecretKey;
    public static byte[] regulatorPublicKey;
    public static PublicKeyCrypto publicKeyCrypto;

    public static void main(String[] args) throws Exception {
        // (Optional) Regulator init keypair.
        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();
        regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        publicKeyCrypto = new PublicKeyCryptoExample();

        doAuction();
        System.exit(0);
    }

    public static void doAuction() throws Exception {

        // 1 Auctioneer or other organization deploy contract
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AuctionUtils.initContract(
                        ecKeyPair, groupID, bidderTableName, regulationInfoTableName);

        // 2 Generate auctioneer state
        EncodedKeyPair auctioneerKeyPair = Utils.getEncodedKeyPair();
        AuctioneerState auctioneerState = AuctionUtils.makeAuctioneerState(auctioneerKeyPair);

        // Generate bidder state
        List<BidderState> bidderStateList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            BidderKeyPair bidderKeyPair = AuctionUtils.generateBidderKeyPair();
            BidderState bidderState = AuctionUtils.makeBidderState(bidderKeyPair);
            bidderStateList.add(bidderState);
        }

        // 3. Auctioneer upload SystemParameter to blockchain
        SystemParametersStorage systemParameters =
                AuctionUtils.makeSystemParameters(
                        auctioneerState.getPublicKey(), soundness, bidLength);
        storageClient.uploadSystemParameters(systemParameters);

        // 4 Bidder register
        List<String> registrationRequestList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            String registrationRequest = BidderClient.register(bidderStateList.get(i));
            registrationRequestList.add(registrationRequest);
        }

        // 5 Auctioneer certify
        List<Credential> credentialList = new ArrayList<>(bidderNum);
        for (int i = 0; i < bidderNum; i++) {
            RegistrationResponse registrationResponse =
                    AuctioneerClient.certify(auctioneerState, registrationRequestList.get(i));
            Credential credential = registrationResponse.getCredential();
            credentialList.add(credential);
        }

        // Contract State: Initializing -> Bidding
        storageClient.nextContractState();

        // 6 Bidder bid
        List<String> bidRequestList = new ArrayList<>();
        for (int i = 0; i < bidderNum; i++) {
            String bidRequest =
                    BidderClient.bid(
                            bidderStateList.get(i),
                            credentialList.get(i),
                            systemParameters,
                            bidValue[i]);
            bidRequestList.add(bidRequest);
            storageClient.verifyBidSignature(bidRequest);

            // (Optional) Upload regulation information to blockchain.
            String bidderPublicKey = bidderStateList.get(i).getPublicKey();
            String encryptedRegulationInfo =
                    AuctionUtils.makeRegulationInfo(
                            publicKeyCrypto, regulatorPublicKey, bidValue[i]);
            storageClient.insertRegulationInfo(bidderPublicKey, encryptedRegulationInfo);
        }

        // 7 Query bidStorage
        List<BidStorage> bidStorageList = storageClient.queryAllBidStorage();
        BidResponse bidResponse = AuctionUtils.makeBidResponse(bidStorageList);

        // 8 Bidder compare
        for (int i = 0; i < bidderNum; i++) {
            String bidComparisonRequest =
                    BidderClient.compare(
                            bidderStateList.get(i), systemParameters, bidResponse, bidValue[i]);
            storageClient.updateBidInfo(
                    Utils.protoToEncodedString(bidStorageList.get(i)), bidComparisonRequest);
        }

        // 9 Bidder claim winner
        List<String> bidComparisonStorageList = storageClient.queryAllBidComparisonStorage();
        BidComparisonResponse bidComparisonResponse =
                AuctionUtils.makeBidComparisonResponse(bidComparisonStorageList);
        String winnerClaimRequest = "";
        int winnerValue = 0;
        for (int i = 0; i < bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.claimWinner(
                            bidderStateList.get(i),
                            Utils.protoToEncodedString(bidComparisonResponse),
                            bidValue[i]);
            if (Utils.hasWedprError(bidderResult)) {
                System.out.println("bid: " + bidValue[i] + ", " + bidderResult.wedprErrorMessage);
            } else {
                winnerClaimRequest = bidderResult.winnerRequest;
                winnerValue = bidValue[i];
                System.out.println("bid: " + bidValue[i]);
            }
        }

        // Contract State: Bidding -> Claiming
        storageClient.nextContractState();

        // 10 Verify winner
        // TODO reconstruct verifyWinner(replace bidRequestList to bidResponse?)
        storageClient.verifyWinner(winnerClaimRequest, bidRequestList);

        String winnerPublicKey =
                WinnerClaimRequest.parseFrom(Utils.stringToBytes(winnerClaimRequest))
                        .getWinnerPublicKey();
        System.out.println("winner public key:" + winnerPublicKey);
        System.out.println("winner bid value:" + winnerValue);

        // Contract State: Claiming -> End
        storageClient.nextContractState();

        // (Optional) Queries regulation information for example.
        // TODO Need a common key to query all regulationInfo?
        List<String> publicKeyList =
                bidderStateList.stream().map(x -> x.getPublicKey()).collect(Collectors.toList());
        System.out.println("\nDecrypted the regulation information about auction is:\n");
        AuctionUtils.queryRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, publicKeyList);
    }
}

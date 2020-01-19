package com.webank.wedpr.anonymousauction;

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
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.anonymousauction.DemoMain;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tx.gas.StaticGasProvider;

class UploadBidStorageParams {
    public StorageExampleClientPerf storageClient;
    public String bidRequest;
    public List<BidderState> bidderStateList;
    public List<Credential> credentialList;
    public SystemParametersStorage systemParameters;
    public List<String> bidRequestList;
}

class UploadBidComparisonStorageParams {
    public StorageExampleClientPerf storageClient;
    public String bidStorage;
    public String bidComparisonRequest;
    public String allBidStorageRequest;
    public List<BidderState> bidderStateList;
    public List<String> bidderIdList;
    public List<BidStorage> bidStorageList;
    public List<String> bidComparisonRequestList;
}

class VerifyWinnerParams {
    public StorageExampleClientPerf storageClient;
    public String winnerClaimRequest;
    public String allBidStorageRequest;
}

public class PerfAnonymousAuctionUtils {
    public static UploadBidStorageParams getUploadBidStorageParams() throws Exception {
        // 1 Coordinator or other organization deploy contract
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClientPerf storageClient =
                initContract(
                        ecKeyPair,
                        groupID,
                        DemoMain.bidderTableName,
                        "uuid_",
                        DemoMain.regulationInfoTableName);

        // 2 Generate coordinator state
        EncodedKeyPair coordinatorKeyPair = Utils.getEncodedKeyPair();
        CoordinatorState coordinatorState = AuctionUtils.makeCoordinatorState(coordinatorKeyPair);

        // Generate bidder state
        List<BidderState> bidderStateList = new ArrayList<>();
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            BidderKeyPair bidderKeyPair = AuctionUtils.generateBidderKeyPair();
            BidderState bidderState =
                    AuctionUtils.makeBidderState(bidderKeyPair, DemoMain.bidValues[i]);
            bidderStateList.add(bidderState);
        }

        // 3. Coordinator upload auction info to blockchain
        AuctionItem auctionItem =
                AuctionUtils.makeAuctionItem(DemoMain.title, DemoMain.description);
        storageClient.uploadAuctionInfo(BidType.HighestPriceBid, auctionItem);

        // 4 Bidder register
        List<String> registrationRequestList = new ArrayList<>();
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            BidderResult bidderResult = BidderClient.register(bidderStateList.get(i));
            registrationRequestList.add(bidderResult.registrationRequest);
        }

        // 5 Coordinator certify
        List<Credential> credentialList = new ArrayList<>(DemoMain.bidderNum);
        for (int i = 0; i < DemoMain.bidderNum; i++) {
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
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.bid(
                            bidderStateList.get(i), credentialList.get(i), systemParameters);
            String bidRequest = bidderResult.bidRequest;
            bidRequestList.add(bidRequest);
        }
        UploadBidStorageParams uploadBidStorageParams = new UploadBidStorageParams();
        uploadBidStorageParams.storageClient = storageClient;
        uploadBidStorageParams.bidRequestList = bidRequestList;
        uploadBidStorageParams.bidRequest = bidRequestList.get(0);
        uploadBidStorageParams.bidderStateList = bidderStateList;
        uploadBidStorageParams.credentialList = credentialList;
        uploadBidStorageParams.systemParameters = systemParameters;
        return uploadBidStorageParams;
    }

    public static UploadBidComparisonStorageParams getUploadBidComparisonStorageParams()
            throws Exception {
        UploadBidStorageParams uploadBidStorageParams = getUploadBidStorageParams();
        StorageExampleClientPerf storageClient = uploadBidStorageParams.storageClient;
        List<BidderState> bidderStateList = uploadBidStorageParams.bidderStateList;
        List<Credential> credentialList = uploadBidStorageParams.credentialList;
        SystemParametersStorage systemParameters = uploadBidStorageParams.systemParameters;
        List<String> bidRequestList = uploadBidStorageParams.bidRequestList;

        List<String> bidderIdList = new ArrayList<>(DemoMain.bidderNum);
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            String bidderId = storageClient.uploadBidStorage(bidRequestList.get(i));
            bidderIdList.add(bidderId);
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
        List<String> bidComparisonRequestList = new ArrayList<>(DemoMain.bidderNum);
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.compare(
                            bidderStateList.get(i),
                            credentialList.get(i),
                            systemParameters,
                            allBidStorageRequest);
            bidComparisonRequestList.add(bidderResult.bidComparisonRequest);
        }
        UploadBidComparisonStorageParams uploadBidComparisonStorageParams =
                new UploadBidComparisonStorageParams();
        uploadBidComparisonStorageParams.storageClient = storageClient;
        uploadBidComparisonStorageParams.bidStorage =
                Utils.protoToEncodedString(bidStorageList.get(0));
        uploadBidComparisonStorageParams.bidStorageList = bidStorageList;
        uploadBidComparisonStorageParams.bidComparisonRequest = bidComparisonRequestList.get(0);
        uploadBidComparisonStorageParams.bidComparisonRequestList = bidComparisonRequestList;
        uploadBidComparisonStorageParams.allBidStorageRequest =
                Utils.protoToEncodedString(allBidStorageRequest);
        uploadBidComparisonStorageParams.bidderStateList = bidderStateList;
        uploadBidComparisonStorageParams.bidderIdList = bidderIdList;

        return uploadBidComparisonStorageParams;
    }

    public static VerifyWinnerParams getVerifyWinnerParams() throws Exception {
        UploadBidComparisonStorageParams uploadBidComparisonStorageParams =
                getUploadBidComparisonStorageParams();
        StorageExampleClientPerf storageClient = uploadBidComparisonStorageParams.storageClient;
        String allBidStorageRequest = uploadBidComparisonStorageParams.allBidStorageRequest;
        List<BidderState> bidderStateList = uploadBidComparisonStorageParams.bidderStateList;
        List<String> bidComparisonRequestList =
                uploadBidComparisonStorageParams.bidComparisonRequestList;
        List<String> bidderIdList = uploadBidComparisonStorageParams.bidderIdList;

        for (int i = 0; i < DemoMain.bidderNum; i++) {
            storageClient.uploadBidComparisonStorage(
                    bidderIdList.get(i), bidComparisonRequestList.get(i));
        }
        // Contract State: Bidding -> Claiming
        storageClient.nextContractState();

        // 9 Bidder claim winner
        int size = bidderIdList.size();
        List<String> bidComparisonStorageList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String bidComparisonStorage =
                    storageClient.queryBidComparisonStorageByBidderId(bidderIdList.get(i));
            bidComparisonStorageList.add(bidComparisonStorage);
        }
        BidComparisonResponse bidComparisonResponse =
                AuctionUtils.makeBidComparisonResponse(bidComparisonStorageList);
        String winnerClaimRequest = null;
        for (int i = 0; i < DemoMain.bidderNum; i++) {
            BidderResult bidderResult =
                    BidderClient.claimWinner(
                            bidderStateList.get(i),
                            Utils.protoToEncodedString(bidComparisonResponse));
            long rank = bidderResult.rank;
            if (rank == 1) {
                winnerClaimRequest = bidderResult.winnerClaimRequest;
            }
        }
        VerifyWinnerParams verifyWinnerParams = new VerifyWinnerParams();
        verifyWinnerParams.storageClient = storageClient;
        verifyWinnerParams.winnerClaimRequest = winnerClaimRequest;
        verifyWinnerParams.allBidStorageRequest = allBidStorageRequest;
        return verifyWinnerParams;
    }

    private static StorageExampleClientPerf initContract(
            ECKeyPair ecKeyPair,
            int groupID,
            String bidderTableName,
            String uuidTableName,
            String regulationInfoTableName)
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException,
                    NoSuchProviderException, Exception {
        AnonymousAuctionExamplePerf anonymousAuctionExample =
                AnonymousAuctionExamplePerf.deploy(
                                Utils.getWeb3j(groupID),
                                Utils.getCredentials(ecKeyPair),
                                new StaticGasProvider(Utils.GASPRICE, Utils.GASLIMIT))
                        .send();
        System.out.println("###address:" + anonymousAuctionExample.getContractAddress());

        // Enable parallel
        TransactionReceipt enableParallelTransactionReceipt =
                anonymousAuctionExample.enableParallel().send();
        Utils.checkTranactionReceipt(enableParallelTransactionReceipt);

        String truncateAddress = Utils.truncateAddress(anonymousAuctionExample);
        bidderTableName = bidderTableName + truncateAddress;
        uuidTableName = uuidTableName + truncateAddress;
        regulationInfoTableName = regulationInfoTableName + truncateAddress;
        System.out.println("bidderTableName:" + bidderTableName);
        System.out.println("uuidTableName:" + uuidTableName);
        System.out.println("regulationInfoTableName:" + regulationInfoTableName);

        StorageExampleClientPerf storageClient =
                new StorageExampleClientPerf(
                        anonymousAuctionExample,
                        bidderTableName,
                        uuidTableName,
                        regulationInfoTableName);

        storageClient.init();
        return storageClient;
    }
}

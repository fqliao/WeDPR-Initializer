package com.webank.wedpr.example.anonymousauction;

import com.webank.wedpr.anonymousauction.proto.AllBidRequest;
import com.webank.wedpr.anonymousauction.proto.BidRequest;
import com.webank.wedpr.anonymousauction.proto.BidStorage;
import com.webank.wedpr.anonymousauction.proto.SystemParametersStorage;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tuples.generated.Tuple3;

public class StorageExampleClient {

    private AnonymousAuctionExample anonymousAuction;
    private String bidderTableName;
    private String regulationInfoTableName;

    public StorageExampleClient(
            AnonymousAuctionExample anonymousAuction,
            String bidderTableName,
            String regulationInfoTableName) {
        this.anonymousAuction = anonymousAuction;
        this.bidderTableName = bidderTableName;
        this.regulationInfoTableName = regulationInfoTableName;
    }

    /**
     * Contract init.
     *
     * @throws Exception
     */
    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction.init(bidderTableName, regulationInfoTableName).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Enable parallel for contract.
     *
     * @throws Exception
     */
    public void enableParallel() throws Exception {
        TransactionReceipt transactionReceipt = anonymousAuction.enableParallel().send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Disable parallel for contract.
     *
     * @throws Exception
     */
    public void disableParallel() throws Exception {
        TransactionReceipt transactionReceipt = anonymousAuction.disableParallel().send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Verify bid signature.
     *
     * @param bidRequest
     * @throws Exception
     */
    public void verifyBidSignature(String bidRequest) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction.verifyBidSignature(bidRequest).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Verify Winner.
     *
     * @param winnerClaimRequest
     * @param bidRequests
     * @throws Exception
     */
    public void verifyWinner(String winnerClaimRequest, List<String> bidRequests) throws Exception {
        int size = bidRequests.size();
        List<BidRequest> bidRequestList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            bidRequestList.add(BidRequest.parseFrom(Utils.stringToBytes(bidRequests.get(i))));
        }
        AllBidRequest allBidRequest =
                AllBidRequest.newBuilder().addAllBidRequests(bidRequestList).build();
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .verifyWinner(winnerClaimRequest, Utils.protoToEncodedString(allBidRequest))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Upload system parameter.
     *
     * @param auctioneerPublicKey
     * @param soundness
     * @param bidValueBitLength
     * @throws Exception
     */
    public void uploadSystemParameters(SystemParametersStorage systemParameters) throws Exception {
        String auctioneerPublicKey = systemParameters.getAuctioneerPublicKey();
        long soundness = systemParameters.getSoundnessParameter();
        long bidValueBitLength = systemParameters.getBidValueBitLength();
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .uploadSystemParameters(
                                auctioneerPublicKey,
                                BigInteger.valueOf(soundness),
                                BigInteger.valueOf(bidValueBitLength))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Query system parameters.
     *
     * @param auctioneerPublicKey
     * @param soundness
     * @param bidValueBitLength
     * @return
     * @throws Exception
     */
    public SystemParametersStorage querySystemParameters(
            String auctioneerPublicKey, long soundness, long bidValueBitLength) throws Exception {
        Tuple3<String, BigInteger, BigInteger> systemParameters =
                anonymousAuction.querySystemParameters().send();
        return SystemParametersStorage.newBuilder()
                .setAuctioneerPublicKey(systemParameters.getValue1())
                .setSoundnessParameter(systemParameters.getValue2().longValue())
                .setBidValueBitLength(systemParameters.getValue3().longValue())
                .build();
    }

    /**
     * Update bid info.
     *
     * @param bidStorage
     * @param bidComparisonStorage
     * @throws Exception
     */
    public void updateBidInfo(String bidStorage, String bidComparisonStorage) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction.updateBidInfo(bidStorage, bidComparisonStorage).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Query bidComparisionStorage by bidStorage.
     *
     * @param bidStorage
     * @return
     * @throws Exception
     */
    public String queryBidComparisonStorage(String bidStorage) throws Exception {
        List<String> bidComparisonStorages =
                anonymousAuction.queryBidComparisonStorage(bidStorage).send();
        if (bidComparisonStorages.isEmpty()) {
            throw new WedprException("Empty set.");
        }
        return bidComparisonStorages.get(0);
    }

    /**
     * Query all bidComparisionStorage.
     *
     * @return
     * @throws Exception
     */
    public List<String> queryAllBidComparisonStorage() throws Exception {
        List<String> bidComparisonStorages = anonymousAuction.queryAllBidComparisonStorage().send();
        if (bidComparisonStorages.isEmpty()) {
            throw new WedprException("Empty set.");
        }
        return bidComparisonStorages;
    }

    /**
     * Query all bid info.
     *
     * @return
     * @throws Exception
     */
    public List<BidStorage> queryAllBidStorage() throws Exception {
        List<String> allBidStorages = anonymousAuction.queryAllBidStorage().send();
        if (allBidStorages.isEmpty()) {
            throw new WedprException("Empty set.");
        }
        int size = allBidStorages.size();
        List<BidStorage> allBidStorageList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            allBidStorageList.add(BidStorage.parseFrom(Utils.stringToBytes(allBidStorages.get(i))));
        }
        return allBidStorageList;
    }

    /**
     * Inserts regulation information.
     *
     * @param publicKey
     * @param regulationInfo
     * @return
     * @throws Exception
     */
    public void insertRegulationInfo(String publicKey, String regulationInfo) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction.insertRegulationInfo(publicKey, regulationInfo).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Queries regulation information.
     *
     * @param publicKey
     * @return
     * @throws Exception
     */
    public String queryRegulationInfo(String publicKey) throws Exception {
        List<String> regulationInfoList = anonymousAuction.queryRegulationInfo(publicKey).send();
        if (regulationInfoList.isEmpty()) {
            throw new WedprException("Empty set.");
        }
        return regulationInfoList.get(0);
    }

    /**
     * Moves to the next contract state.
     *
     * @return
     * @throws Exception
     */
    public void nextContractState() throws Exception {
        TransactionReceipt transactionReceipt = anonymousAuction.nextContractState().send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Query contract state.
     *
     * @return
     * @throws Exception
     */
    public BigInteger queryContractState() throws Exception {
        return anonymousAuction.contractState().send();
    }
}

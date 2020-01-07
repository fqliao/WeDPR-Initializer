package com.webank.wedpr.anonymousauction;

import com.webank.wedpr.anonymousauction.proto.AllBidStorageRequest;
import com.webank.wedpr.anonymousauction.proto.AuctionItem;
import com.webank.wedpr.anonymousauction.proto.BidStorage;
import com.webank.wedpr.anonymousauction.proto.SystemParametersStorage;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.anonymousauction.BidWinner;
import java.math.BigInteger;
import java.util.List;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.fisco.bcos.web3j.tuples.generated.Tuple2;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoder;
import org.fisco.bcos.web3j.tx.txdecode.TransactionDecoderFactory;

public class StorageExampleClientPerf {

    private AnonymousAuctionExamplePerf anonymousAuction;
    private String bidderTableName;
    private String bidderIdTableName;
    private String regulationInfoTableName;
    private TransactionDecoder transactionDecoder;

    public StorageExampleClientPerf(
            AnonymousAuctionExamplePerf anonymousAuction,
            String bidderTableName,
            String bidderIdTableName,
            String regulationInfoTableName) {
        this.anonymousAuction = anonymousAuction;
        this.bidderTableName = bidderTableName;
        this.bidderIdTableName = bidderIdTableName;
        this.regulationInfoTableName = regulationInfoTableName;
        transactionDecoder =
                TransactionDecoderFactory.buildTransactionDecoder(
                        AnonymousAuctionExamplePerf.ABI, AnonymousAuctionExamplePerf.BINARY);
    }

    public AnonymousAuctionExamplePerf getAnonymousAuction() {
        return anonymousAuction;
    }

    public TransactionDecoder getTransactionDecoder() {
        return transactionDecoder;
    }

    /**
     * Contract init.
     *
     * @throws Exception
     */
    public void init() throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .init(bidderTableName, bidderIdTableName, regulationInfoTableName)
                        .send();
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
     * Uploads bidStorage.
     *
     * @param bidRequest
     * @throws Exception
     */
    public String uploadBidStorage(String bidRequest) throws Exception {
        String bidderId = Utils.getUuid();
        TransactionReceipt transactionReceipt =
                anonymousAuction.uploadBidStorage(bidderId, bidRequest).send();
        Utils.checkTranactionReceipt(transactionReceipt);
        return bidderId;
    }

    /**
     * Uploads bidComparisonStorage.
     *
     * @param bidStorage
     * @param bidComparisonStorage
     * @throws Exception
     */
    public void uploadBidComparisonStorage(String bidCounter, String bidComparisonRequest)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .uploadBidComparisonStorage(bidCounter, bidComparisonRequest)
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Verifies Winner.
     *
     * @param winnerClaimRequest
     * @param bidRequests
     * @throws Exception
     */
    public void verifyWinner(String winnerClaimRequest, AllBidStorageRequest allBidStorageRequest)
            throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .verifyWinner(
                                winnerClaimRequest,
                                Utils.protoToEncodedString(allBidStorageRequest))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public BidWinner queryBidWinner() throws Exception {
        Tuple2<String, BigInteger> result = anonymousAuction.queryBidWinner().send();
        BidWinner bidWinner = new BidWinner();
        bidWinner.setPublicKey(result.getValue1());
        bidWinner.setBidValue(result.getValue2().longValue());
        return bidWinner;
    }

    /**
     * Uploads auction info
     *
     * @param bidType
     * @param auctionInfo
     * @throws Exception
     */
    public void uploadAuctionInfo(BidType bidType, AuctionItem auctionInfo) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction
                        .uploadAuctionInfo(
                                BigInteger.valueOf(bidType.ordinal()),
                                Utils.protoToEncodedString(auctionInfo))
                        .send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    /**
     * Queries bidType
     *
     * @return
     * @throws Exception
     */
    public BidType queryBidType() throws Exception {
        BigInteger bidType = anonymousAuction.bidType().send();
        return BidType.getBidType(bidType);
    }

    /**
     * Queries auctionItem
     *
     * @return
     * @throws Exception
     */
    public AuctionItem queryAutionItem() throws Exception {
        String auctionItem = anonymousAuction.autionItem().send();
        return AuctionItem.parseFrom(Utils.stringToBytes(auctionItem));
    }

    /**
     * Queries system parameters.
     *
     * @return
     * @throws Exception
     */
    public SystemParametersStorage querySystemParameters() throws Exception {
        Tuple2<BigInteger, BigInteger> systemParameters =
                anonymousAuction.querySystemParameters().send();
        return SystemParametersStorage.newBuilder()
                .setSoundnessParameter(systemParameters.getValue1().longValue())
                .setBidValueBitLength(systemParameters.getValue2().longValue())
                .build();
    }

    /**
     * Queries bidComparisonStorage by bidderId
     *
     * @param bidderId
     * @return
     * @throws Exception
     */
    public String queryBidComparisonStorageByBidderId(String bidderId) throws Exception {
        return anonymousAuction.queryBidComparisonStorageByBidderId(bidderId).send();
    }

    /**
     * Queries bidStorage by bidderId
     *
     * @param bidderId
     * @return
     * @throws Exception
     */
    public BidStorage queryBidStorageByBidderId(String bidderId) throws Exception {
        return BidStorage.parseFrom(
                Utils.stringToBytes(anonymousAuction.queryBidStorageByBidderId(bidderId).send()));
    }

    /**
     * Queries all BidderId
     *
     * @return
     * @throws Exception
     */
    public List<String> queryAllBidderId() throws Exception {
        List<String> bidderIds = anonymousAuction.queryAllBidderId().send();
        if (bidderIds.isEmpty()) {
            throw new WedprException("Empty set.");
        }
        return bidderIds;
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
     * Queries contract state.
     *
     * @return
     * @throws Exception
     */
    public BigInteger queryContractState() throws Exception {
        return anonymousAuction.contractState().send();
    }
}

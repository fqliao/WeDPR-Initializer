package com.webank.wedpr.anonymousauction;

import com.webank.wedpr.anonymousauction.proto.AllBidStorageRequest;
import com.webank.wedpr.anonymousauction.proto.BidStorage;
import com.webank.wedpr.anonymousauction.proto.SystemParametersStorage;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.anonymousauction.BidWinner;
import java.math.BigInteger;
import java.util.ArrayList;
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
     * Uploads BidType
     *
     * @param bidType
     * @throws Exception
     */
    public void uploadBidType(BidType bidType) throws Exception {
        TransactionReceipt transactionReceipt =
                anonymousAuction.uploadBidType(BigInteger.valueOf(bidType.ordinal())).send();
        Utils.checkTranactionReceipt(transactionReceipt);
    }

    public BidType queryBidType() throws Exception {
        BigInteger bidType = anonymousAuction.bidType().send();
        return BidType.getBidType(bidType);
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
     * Queries bidComparisionStorage by bidStorage.
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
     * Queries all bidComparisionStorage.
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
     * Queries all bid info.
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
     * Queries contract state.
     *
     * @return
     * @throws Exception
     */
    public BigInteger queryContractState() throws Exception {
        return anonymousAuction.contractState().send();
    }
}

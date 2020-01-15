pragma solidity ^0.4.24;
pragma experimental ABIEncoderV2;

import "./Table.sol";
import "./WedprPrecompiled.sol";
import "./ParallelContract.sol";

// Example application contract implementing anonymous auction suite.
// Please feel free to modify it according to your business demands.
contract AnonymousAuctionExample is ParallelContract {
    WedprPrecompiled wedpr;
    TableFactory tableFactory;
    BidType public bidType;
    string public autionItem;
    BidWinner bidWinner;
    int public claimWinnerCounter;
    ContractState public contractState;
    address owner;

    int constant private soundness = 40; 
    int constant private bidValueBitLength = 8;
    address constant private TABLE_FACTORY_PRECOMPILED_ADDRESS = 0x1001;
    address constant private WEDPR_PRECOMPILED_ADDRESS = 0x5018;

    // anonymous auction table
    string bidderTableName;
    string constant private BID_INDEX_FIELD = "bidderId";
    string constant private BID_DATA1_FIELD = "bidStorage";
    string constant private BID_DATA2_FIELD = "bidComparisonStorage";
    string constant private BID_DATA_FIELD = "bidStorage,bidComparisonStorage";

    // anonymous auction table
    string bidderIdTableName;
    string constant private UUID_INDEX_FIELD = "bid";
    string constant private UUID_INDEX_VALUE = "bid";
    string constant private UUID_DATA1_FIELD = "bidderId";
    string constant private UUID_DATA_FIELD = "bidderId";

    // regulationInfo table
    string regulationInfoTableName;
    string constant private REGULATOR_INDEX_FIELD = "publicKey";
    string constant private REGULATOR_DATA_FIELD = "regulationInfo";

    string constant private ERROR_VERIFY_BID_SIGNATURE = "verify bid signature failed";
    string constant private ERROR_VERIFY_WINNER = "verify winner failed";
    string constant private ERROR_BIDDER_REBID = "Bidder is already bidded.";
    string constant private ERROR_REGULATOR_DATA_EXIST = "the regulation info already exists";
    string constant private ERROR_OTHER = "no permission or other errors";
    int constant private TABLE_EXSIST = -50001;

    modifier onlyOwner {
        require(msg.sender == owner);
        _;
    }

    struct BidWinner{
        int bidValue;
        string publicKey;
    }

    enum ContractState{
        Initializing,
        Bidding,
        Claiming,
        End
    }

    enum BidType{
        HighestPriceBid,
        LowestPriceBid
    }

    // Constructor.
    constructor() public {
        owner = msg.sender;
        wedpr = WedprPrecompiled(WEDPR_PRECOMPILED_ADDRESS);
        tableFactory = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        contractState = ContractState.Initializing;
    }

    // Moves to the next contract state.
    function nextContractState() public onlyOwner {
        if (contractState == ContractState.Initializing) {
          contractState = ContractState.Bidding;
        } else if (contractState == ContractState.Bidding) {
          contractState = ContractState.Claiming;
        } else {
          contractState = ContractState.End;
        }
    }

    // Initializes the data tables.
    function init(string _bidderTableName, string _bidderIdTableName, string _regulationInfoTableName) public onlyOwner{
        require(contractState == ContractState.Initializing, "Bidding has started.");
        bidderTableName = _bidderTableName;
        regulationInfoTableName = _regulationInfoTableName;
        bidderIdTableName = _bidderIdTableName;

        int result1 = tableFactory.createTable(bidderTableName, BID_INDEX_FIELD, BID_DATA_FIELD);
        int result2 = tableFactory.createTable(bidderIdTableName, UUID_INDEX_FIELD, UUID_DATA_FIELD);
        int result3 = tableFactory.createTable(regulationInfoTableName, REGULATOR_INDEX_FIELD, REGULATOR_DATA_FIELD);

        require(result1 == 0 || result1 == TABLE_EXSIST, ERROR_OTHER);
        require(result2 == 0 || result2 == TABLE_EXSIST, ERROR_OTHER);
        require(result3 == 0 || result3 == TABLE_EXSIST, ERROR_OTHER);
    }

    // Registers parallel api
    function enableParallel() public
    {
        registerParallelFunction("uploadBidStorage(string,string)", 1);
        registerParallelFunction("uploadBidComparisonStorage(string,string)", 1);
        registerParallelFunction("verifyWinner(string,string)", 1);
    } 

    // Disables parallel api
    function disableParallel() public
    {
        unregisterParallelFunction("uploadBidStorage(string,string)");
        unregisterParallelFunction("uploadBidComparisonStorage(string,string)");
        unregisterParallelFunction("verifyWinner(string,string)");
    }

    // Uploads auction info
    function uploadAuctionInfo(BidType _bidType, string _autionItem) public onlyOwner {
        bidType = _bidType;
        autionItem = _autionItem;
    }

    // Queries SystemParameters.
    function querySystemParameters() public view returns(int, int) {
        require(contractState == ContractState.Bidding, "Bidding is not open now.");
        return (soundness, bidValueBitLength);
    }

    // Uploads bidStorage.
    function uploadBidStorage(string bidderId, string bidRequest) public {
        require(contractState == ContractState.Bidding, "Bidding is not open now.");
        string memory bidStorage = wedpr.anonymousAuctionVerifyBidSignatureFromBidRequest(bidRequest);
        string[] memory bidComparisonStorages = queryBidComparisonStorageByBidStorage(bidStorage);
        require(bidComparisonStorages.length == 0, ERROR_BIDDER_REBID);
        insertUuid(bidderId);
        insertBidStorage(bidderId, bidStorage);
    }

    // Uploads bidComparisonStorage.
    function uploadBidComparisonStorage(string bidderId, string bidComparisonRequest) public {
        require(contractState == ContractState.Bidding, "Bidding is not open now.");
        wedpr.anonymousAuctionVerifyBidSignatureFromBidComparisonRequest(bidComparisonRequest);
        insertBidComparisonRequest(bidderId, bidComparisonRequest);
    }

    // Verifies bid winner.
    function verifyWinner(string winnerClaimRequest, string allBidStorageRequest) public {
        require(contractState == ContractState.Claiming, "Claiming is not open now.");
        int bidValue = 0;
        string memory publicKey;
        if (claimWinnerCounter == 0) {
            (bidValue, publicKey) = wedpr.anonymousAuctionVerifyWinner(winnerClaimRequest, allBidStorageRequest);
            bidWinner.bidValue = bidValue;
            bidWinner.publicKey = publicKey;
            claimWinnerCounter++;
        } else {
            (bidValue, publicKey) = wedpr.anonymousAuctionVerifyWinner(winnerClaimRequest, allBidStorageRequest);
            if(bidType == BidType.HighestPriceBid) {
                if(bidValue > bidWinner.bidValue){
                  bidWinner.bidValue = bidValue;
                  bidWinner.publicKey = publicKey;
                }
            } else if(bidType == BidType.LowestPriceBid) {
                if(bidValue < bidWinner.bidValue){
                  bidWinner.bidValue = bidValue;
                  bidWinner.publicKey = publicKey;
                }
            }
            claimWinnerCounter++;
        }
    }

    // Queries BidWinner.
    function queryBidWinner() public view returns(string, int){
        return (bidWinner.publicKey, bidWinner.bidValue);
    }

    // Queries all bidderId.
    function queryAllBidderId() public view returns(string[]){
        Table bidderIdTable = tableFactory.openTable(bidderIdTableName);
        Entries bidderIdEntries = bidderIdTable.select(UUID_INDEX_VALUE, bidderIdTable.newCondition());
        uint size = uint(bidderIdEntries.size());
        string[] memory bidderIds = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = bidderIdEntries.get(int(i));
            string memory bidderId = entry.getString(UUID_DATA1_FIELD);
            bidderIds[i] = bidderId;
        }
        return bidderIds;
    }

    // Queries bidComparisonStorage by bidderId.
    function queryBidComparisonStorageByBidderId(string bidderId) public view returns(string){
        Table bidderTable = tableFactory.openTable(bidderTableName);
        Entries bidEntries = bidderTable.select(bidderId, bidderTable.newCondition());
        require(bidEntries.size() == 1, "Bid comparison storage is empty.");
        string memory bidComparisonStorages = bidEntries.get(0).getString(BID_DATA2_FIELD);
        return bidComparisonStorages;
    }

    // Queries bidStorage by bidderId.
    function queryBidStorageByBidderId(string bidderId) public view returns(string){
        Table table = tableFactory.openTable(bidderTableName);
        Entries bidEntries = table.select(bidderId, table.newCondition());
        require(bidEntries.size() == 1, "Bid storage is empty.");
        string memory bidStorage = bidEntries.get(0).getString(BID_DATA1_FIELD);
        return bidStorage;
    }
    
    // Inserts regulation information.
    function insertRegulationInfo(string publicKey, string regulationInfo) public {
        string[] memory regulationInfos = queryRegulationInfo(publicKey);
        require(regulationInfos.length == 0, ERROR_REGULATOR_DATA_EXIST);

        Table table = tableFactory.openTable(regulationInfoTableName);
        Entry entry = table.newEntry();
        entry.set(REGULATOR_DATA_FIELD, regulationInfo);
        int result = table.insert(publicKey, entry);
        require(result == 1, ERROR_OTHER);
    }

    // Queries regulation information.
    function queryRegulationInfo(string publicKey) public view returns(string[]){
        Table table = tableFactory.openTable(regulationInfoTableName);
        Entries entries = table.select(publicKey, table.newCondition());
        uint size = uint(entries.size());
        string[] memory regulationInfos = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            regulationInfos[i] = entry.getString(REGULATOR_DATA_FIELD);
        }
        return regulationInfos;
    }

    // Utility function section.

    // Queries bidComparisonStorage by bidStorage.
    function queryBidComparisonStorageByBidStorage(string bidStorage) private view returns(string[]){
        Table table = tableFactory.openTable(bidderTableName);
        Condition condition = table.newCondition();
        Entries entries = table.select(bidStorage, condition);
        uint size = uint(entries.size());
        string[] memory bidComparisonStorages = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            bidComparisonStorages[i] = entry.getString(BID_DATA2_FIELD);
        }
        return bidComparisonStorages;
    }

    // Inserts bidderId.
    function insertUuid(string bidderId) private {
        Table table = tableFactory.openTable(bidderIdTableName);
        Entry entry = table.newEntry();
        entry.set(UUID_DATA1_FIELD, bidderId);
        int records = table.insert(UUID_INDEX_VALUE, entry);
        require(records == 1, ERROR_OTHER);
    }

    // Inserts bidStorage.
    function insertBidStorage(string bidCounter, string bidStorage) private {
        Table table = tableFactory.openTable(bidderTableName);
        Entry entry = table.newEntry();
        entry.set(BID_DATA1_FIELD, bidStorage);
        entry.set(BID_DATA2_FIELD, "");
        int records = table.insert(bidCounter, entry);
        require(records == 1, ERROR_OTHER);
    }

    // Inserts bidComparisonRequest.
    function insertBidComparisonRequest(string bidderId, string bidComparisonRequest) private {
        Table table = tableFactory.openTable(bidderTableName);
        Condition condition = table.newCondition();
        condition.EQ(BID_INDEX_FIELD, bidderId);
        Entry entry = table.newEntry();
        entry.set(BID_DATA2_FIELD, bidComparisonRequest);
        int records = table.update(bidderId, entry, condition);
        require(records == 1, ERROR_OTHER);
    }
}

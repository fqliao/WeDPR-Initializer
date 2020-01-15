pragma solidity ^0.4.24;
pragma experimental ABIEncoderV2;
import "./Table.sol";
import "./WedprPrecompiled.sol";
import "./ParallelContract.sol";

// Example application contract implementing anonymous voting suite.
// Please feel free to modify it according to your business demands.
contract AnonymousVotingExample is ParallelContract {
    WedprPrecompiled wedpr;
    TableFactory tableFactory;
    SystemParameters systemParametersStruct;
    ContractState public contractState;
    string voteResultStorage;
    uint counterNumber;
    address owner;

    int voterAggregateNumber = 10;
    int counterAggregateNumber = 5;
    int voterAggregateRecords = 0;
    int counterAggregateRecords = 0;
    string voteStorageSum;
    string decryptedResultPartStorageSum;

    // voter table fields
    address constant private TABLE_FACTORY_PRECOMPILED_ADDRESS = 0x1001;
    address constant private WEDPR_PRECOMPILED_ADDRESS = 0x5018;

    // counter table fields
    string voterTableName;
    string constant private VOTER_INDEX_FIELD = "blankBallot";
    string constant private VOTER_DATA_FIELD = "voteStorage";

    // voting aggregate table fields
    string voterAggregateTableName;
    string constant private VOTER_AGGREGATE_INDEX_FIELD = "voter";
    string constant private VOTER_AGGREGATE_DATA_FIELD = "blankBallot";

    // counting aggregate table fields
    string counterTableName;
    string counterAggregateTableName;
    string constant private COUNTER_AGGREGATE_INDEX_FIELD = "counter";
    string constant private COUNTER_AGGREGATE_DATA_FIELD = "counterId";

    // regulation table fields
    string regulationInfoTableName;
    string constant private REGULATOR_DATA_FIELD = "regulationInfo";

    string constant private COUNTER_INDEX_FIELD = "counterId";
    string constant private COUNTER_DATA1_FIELD = "hPointShare";
    string constant private COUNTER_DATA2_FIELD = "decryptedResultPartStorage";
    string constant private COUNTER_DATA_FIELD = "hPointShare,decryptedResultPartStorage";

    string constant private ERROR_VOTER_REVOTE = "Voter is already voted.";
    string constant private ERROR_VOTER_REQUEST = "Verifies vote request failed.";
    string constant private ERROR_COUNTER_EXIST = "Counters is already exist.";
    string constant private ERROR_COUNTER_REQUEST = "Verifies count request failed.";
    string constant private ERROR_VERIFY_VOTE_RESULT = "Verifies vote result failed.";
    string constant private ERROR_REGULATOR_DATA_EXIST = "the regulation info already exists";
    string constant private ERROR_OTHER = "No permission or other errors.";
    int constant private TABLE_EXSIST = -50001;

    uint constant private MIN_COUNTER_NUMBER = 2;
        modifier onlyOwner {
        require(msg.sender == owner);
        _;
    }

    struct SystemParameters{
      string[] candidates;
      string hPoint;
    }
    
    enum ContractState{
      Initializing,
      Voting,
      CountingStep1,
      CountingStep2,
      End
    }

    // Constructor.
    constructor() public {
        owner = msg.sender;
        contractState = ContractState.Initializing;
        wedpr = WedprPrecompiled(WEDPR_PRECOMPILED_ADDRESS);
        tableFactory = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
    }

    // Moves to the next contract state.
    function nextContractState() public onlyOwner {
        if (contractState == ContractState.Initializing) {
          require(counterNumber >= MIN_COUNTER_NUMBER, "The number of counters is less than the minimum number.");
          contractState = ContractState.Voting;
        } else if (contractState == ContractState.Voting) {
          contractState = ContractState.CountingStep1;
        } else if (contractState == ContractState.CountingStep1) {
          contractState = ContractState.CountingStep2;
        } else {
          contractState = ContractState.End;
        }
    }

    // Initializes the data tables.
    function init(string _voterTableName, string _counterTableName, string _regulationInfoTableName,
                  string _voterAggregateTableName, string _counterAggregateTableName) public {
        require(contractState == ContractState.Initializing, "Voting has started.");

        voterTableName = _voterTableName;
        counterTableName = _counterTableName;
        regulationInfoTableName = _regulationInfoTableName;
        voterAggregateTableName = _voterAggregateTableName;
        counterAggregateTableName = _counterAggregateTableName;

        int result1 = tableFactory.createTable(voterTableName, VOTER_INDEX_FIELD, VOTER_DATA_FIELD);
        int result2 = tableFactory.createTable(counterTableName, COUNTER_INDEX_FIELD, COUNTER_DATA_FIELD);
        int result3 = tableFactory.createTable(regulationInfoTableName, VOTER_INDEX_FIELD, REGULATOR_DATA_FIELD);
        int result4 = tableFactory.createTable(voterAggregateTableName, VOTER_AGGREGATE_INDEX_FIELD, VOTER_AGGREGATE_DATA_FIELD);
        int result5 = tableFactory.createTable(counterAggregateTableName, COUNTER_AGGREGATE_INDEX_FIELD, COUNTER_AGGREGATE_DATA_FIELD);

        require(result1 == 0 || result1 == TABLE_EXSIST, ERROR_OTHER);
        require(result2 == 0 || result2 == TABLE_EXSIST, ERROR_OTHER);
        require(result3 == 0 || result3 == TABLE_EXSIST, ERROR_OTHER);
        require(result4 == 0 || result4 == TABLE_EXSIST, ERROR_OTHER);
        require(result5 == 0 || result5 == TABLE_EXSIST, ERROR_OTHER);
    }

    // Registers parallel api
    function enableParallel() public
    {
        registerParallelFunction("verifyBoundedVoteRequest(string,string)", 1);
        registerParallelFunction("verifyUnboundedVoteRequest(string,string)", 1);
        registerParallelFunction("verifyCountRequest(string,string,string,string)", 1);
        registerParallelFunction("verifyVoteResult(string,string)", 1);
    } 

    // Disable parallel api
    function disableParallel() public
    {
        unregisterParallelFunction("verifyBoundedVoteRequest(string,string)");
        unregisterParallelFunction("verifyUnboundedVoteRequest(string,string)"); 
        unregisterParallelFunction("verifyCountRequest(string,string,string,string)"); 
        unregisterParallelFunction("verifyVoteResult(string,string)"); 
    }

    // Voter votes ballots if the request passed the validation.
    function verifyBoundedVoteRequest(string voteRequest, string systemParameters) public returns(string) {
        require(contractState == ContractState.Voting, "Voting is not open now.");
        string memory blankBallot = "";
        string memory voteStoragePart = "";
        (blankBallot, voteStoragePart) = wedpr.anonymousVotingVerifyBoundedVoteRequest(systemParameters, voteRequest);

        string[] memory voteStorageParts = queryVoteStoragePart(blankBallot);
        require(voteStorageParts.length == 0, ERROR_VOTER_REVOTE);
        insertVoteBallot(blankBallot, voteStoragePart);

        return blankBallot;
    }

    // Voter votes ballots if the request passed the validation.
    function verifyUnboundedVoteRequest(string voteRequest, string systemParameters) public returns(string){
        require(contractState == ContractState.Voting, "Voting is not open now.");
        string memory blankBallot = "";
        string memory voteStoragePart = "";
        (blankBallot, voteStoragePart) = wedpr.anonymousVotingVerifyUnboundedVoteRequest(systemParameters, voteRequest);

        string[] memory voteStorageParts = queryVoteStoragePart(blankBallot);
        require(voteStorageParts.length == 0, ERROR_VOTER_REVOTE);
        insertVoteBallot(blankBallot, voteStoragePart);

        return blankBallot;
    }

    // Aggregate vote storage
    function aggregateVoteStorage(string systemParameters) public returns(bool) {
        require(contractState == ContractState.Voting, "Voting is not open now.");

        Table voterAggregateTable = tableFactory.openTable(voterAggregateTableName);
        Table voterTable = tableFactory.openTable(voterTableName);

        Condition condition1 = voterAggregateTable.newCondition();
        Condition condition2 = voterTable.newCondition();

        string memory blankBallot = "";
        string memory voteStoragePart = "";
        condition1.limit(voterAggregateRecords, voterAggregateRecords + voterAggregateNumber);
        Entries entries = voterAggregateTable.select(VOTER_AGGREGATE_INDEX_FIELD, condition1);
        int size = entries.size();
        for(int i = 0; i < size; ++i) {
            blankBallot = entries.get(i).getString(VOTER_AGGREGATE_DATA_FIELD);
            Entries entries2 = voterTable.select(blankBallot, condition2);
            require(entries2.size() != 0, "query voter table entries is empty");
            voteStoragePart = entries2.get(0).getString(VOTER_DATA_FIELD);
            voteStorageSum = wedpr.anonymousVotingAggregateVoteSumResponse(systemParameters, voteStoragePart, voteStorageSum);
        }
        
        voterAggregateRecords = voterAggregateRecords + size;
        if(size < voterAggregateNumber) { // aggregate finished
          return false;
        } else {  // aggregate unfinished
          return true;
        }
    }

    // Queries a voter storage part.
    function queryVoteStoragePart(string blankBallot) public view returns(string[]) {
        Table table = tableFactory.openTable(voterTableName);
        Entries entries = table.select(blankBallot, table.newCondition());
        uint size = uint(entries.size());
        string[] memory voteStorageParts = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            voteStorageParts[i] = entry.getString(VOTER_DATA_FIELD);
        }
        return voteStorageParts;
    }

    // Gets voteStorageSum.
    function getVoteStorageSum() public view returns(string){
        require(uint(contractState) > uint(ContractState.Voting), "Voting is still open.");
        return voteStorageSum;
    }

    // Updates counter decryptedResultPartStoragePart if the request passed the validation.
    function verifyCountRequest(string hPointShare, string decryptedRequest, string voteStorage, string systemParameters) public {
        require(contractState == ContractState.CountingStep1, "It should be called during the CountingStep1 state.");
        string memory counterId = "";
        string memory decryptedResultPartStoragePart = "";
        (counterId, decryptedResultPartStoragePart) = wedpr.anonymousVotingVerifyCountRequest(systemParameters, voteStorage, hPointShare, decryptedRequest);

        updateCountBallot(counterId, decryptedResultPartStoragePart);
    }

    // Aggregate decryptedPart
    function aggregateDecryptedPart(string systemParameters) public returns(bool){
        require(contractState == ContractState.CountingStep1, "It should be called during the CountingStep1 state.");

        Table counterAggregateTable = tableFactory.openTable(counterAggregateTableName);
        Table counterTable = tableFactory.openTable(counterTableName);

        Condition condition1 = counterAggregateTable.newCondition();
        Condition condition2 = counterTable.newCondition();

        string memory counterId = "";
        string memory decryptedResultPartStoragePart = "";
        condition1.limit(counterAggregateRecords, counterAggregateRecords + counterAggregateNumber);
        Entries entries = counterAggregateTable.select(COUNTER_AGGREGATE_INDEX_FIELD, condition1);
        int size = entries.size();
        for(int i = 0; i < size; ++i) {
            counterId = entries.get(i).getString(COUNTER_AGGREGATE_DATA_FIELD);
            Entries entries2 = counterTable.select(counterId, condition2);
            require(entries2.size() != 0, "query counter table entries is empty");
            decryptedResultPartStoragePart = entries2.get(0).getString(COUNTER_DATA2_FIELD);
            decryptedResultPartStorageSum = wedpr.anonymousVotingAggregateDecryptedPartSum(systemParameters,
                                            decryptedResultPartStoragePart, decryptedResultPartStorageSum);
        }

        counterAggregateRecords = counterAggregateRecords + size;
        if(size < counterAggregateNumber) { // aggregate finished
          return false;
        } else {  // aggregate unfinished
          return true;
        }

    }

    // Any one verifies vote result.
    function verifyVoteResult(string voteResultRequest, string systemParameters) public {
        require(contractState == ContractState.CountingStep2, "It should be called during the CountingStep2 state.");
        int result = wedpr.anonymousVotingVerifyVoteResult(systemParameters, voteStorageSum, decryptedResultPartStorageSum, voteResultRequest);
        require(result == 0, ERROR_VERIFY_VOTE_RESULT);

        // save vote result to blockchain
        string memory newVoteResultStorage = wedpr.anonymousVotingGetVoteResultFromRequest(voteResultRequest);
        voteResultStorage = newVoteResultStorage;
    }
        // Sets candidates list.
    function setCandidates(string[] candidates) public {
        require(contractState == ContractState.Initializing, "Voting has started.");
        systemParametersStruct.candidates = candidates;
    }

    // Gets candidates.
    function getCandidates() public view returns(string[]){
        require(contractState != ContractState.Initializing, "Voting has not yet started.");
        return systemParametersStruct.candidates;
    }

    // Inserts a counter hPoint share.
    function insertHPointShare(string counterId, string hPointShare) public returns(int) {
        require(contractState == ContractState.Initializing, "Voting has started.");
        Table table = tableFactory.openTable(counterTableName);
        Entry entry = table.newEntry();
        entry.set(COUNTER_DATA1_FIELD, hPointShare);
        entry.set(COUNTER_DATA2_FIELD, "");

        string[] memory hPointShares = queryHPointShare(counterId);
        require(hPointShares.length == 0, ERROR_COUNTER_EXIST);
        int records = table.insert(counterId, entry);
        require(records == 1, ERROR_OTHER);
        counterNumber = counterNumber + 1;

        // Accumulates hPoint share
        string memory newHPointSum = wedpr.anonymousVotingAggregateHPoint(hPointShare, systemParametersStruct.hPoint);
        systemParametersStruct.hPoint = newHPointSum;

        Table counterAggregateTable = tableFactory.openTable(counterAggregateTableName);
        Entry entry1 = counterAggregateTable.newEntry();
        entry1.set(COUNTER_AGGREGATE_DATA_FIELD, counterId);
        int result = counterAggregateTable.insert(COUNTER_AGGREGATE_INDEX_FIELD, entry1);
        require(records == 1, ERROR_OTHER);

        return records;
    }

    // Queries a counter hPoint share.
    function queryHPointShare(string counterId) public view returns(string[]) {
        Table table = tableFactory.openTable(counterTableName);
        Entries entries = table.select(counterId, table.newCondition());
        uint size = uint(entries.size());
        string[] memory hPointShares = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            hPointShares[i] = entry.getString(COUNTER_DATA1_FIELD);
        }
        return hPointShares;
    }

    // Gets hPoint.
    function getHPoint() public view returns(string){
        require(contractState != ContractState.Initializing, "Voting has not yet started.");
        return systemParametersStruct.hPoint;
    }

    // Gets decryptedResultPartStorageSum.
    function getDecryptedResultPartStorageSum() public view returns(string){
        require(uint(contractState) > uint(ContractState.CountingStep1), "It should be called during the CountingStep2 or End state.");
        return decryptedResultPartStorageSum;
    }

    // Gets vote result storage.
    function getVoteResultStorage() public view returns(string) {
        require(contractState == ContractState.End, "Counting has not yet finished.");
        return voteResultStorage;
    }

    // Inserts regulation information.
    function insertRegulationInfo(string blankBallot, string regulationInfoPb) public {
        Table table = tableFactory.openTable(regulationInfoTableName);
        string[] memory regulationInfos;
        regulationInfos = queryRegulationInfo(blankBallot);
        require(regulationInfos.length == 0, ERROR_REGULATOR_DATA_EXIST);

        Entry entry = table.newEntry();
        entry.set(REGULATOR_DATA_FIELD, regulationInfoPb);
        int result = table.insert(blankBallot, entry);
        require(result == 1, ERROR_OTHER);
    }

    // Queries regulation information.
    function queryRegulationInfo(string blankBallot) public view returns(string[]){
        Table table = tableFactory.openTable(regulationInfoTableName);
        Condition condition = table.newCondition();
        Entries entries = table.select(blankBallot, condition);

        uint size = uint(entries.size());
        string[] memory regulationInfos = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            regulationInfos[i] = entry.getString(REGULATOR_DATA_FIELD);
        }

        return regulationInfos;
    }

    // Utility function section.

    // Inserts a voter ballot.
    function insertVoteBallot(string blankBallot, string voteStorage) private {
        Table voterAggregateTable = tableFactory.openTable(voterAggregateTableName);
        Entry entry1 = voterAggregateTable.newEntry();
        entry1.set(VOTER_AGGREGATE_DATA_FIELD, blankBallot);
        int result1 = voterAggregateTable.insert(VOTER_AGGREGATE_INDEX_FIELD, entry1);
        require(result1 == 1, ERROR_OTHER);
        
        Table voterTable = tableFactory.openTable(voterTableName);
        Entry entry2 = voterTable.newEntry();
        entry2.set(VOTER_DATA_FIELD, voteStorage);
        int result2 = voterTable.insert(blankBallot, entry2);
        require(result2 == 1, ERROR_OTHER);
    }

    // Updates a counter ballot.
    function updateCountBallot(string counterId, string decryptedResultPartStoragePart) private {
        Table table = tableFactory.openTable(counterTableName);
        Entry entry = table.newEntry();
        entry.set(COUNTER_DATA2_FIELD, decryptedResultPartStoragePart);
        int result = table.update(counterId, entry, table.newCondition());
        require(result == 1, ERROR_OTHER);
    }
}

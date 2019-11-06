pragma solidity ^0.4.24;
pragma experimental ABIEncoderV2;
import "./Table.sol";
import "./WedprPrecompiled.sol";

// Example application contract implementing anonymous voting suite.
// Please feel free to modify it according to your business demands.
contract AnonymousVotingExample {
    WedprPrecompiled wedpr;
    SystemParameters systemParametersStruct;
    ContractState public contractState;
    string voteStorageSumTotal;
    string decryptedResultPartStorageSumTotal;
    string voteResultStorage;
    uint counterNumber;
    address owner;

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

    address constant private TABLE_FACTORY_PRECOMPILED_ADDRESS = 0x1001;
    address constant private WEDPR_PRECOMPILED_ADDRESS = 0x5018;

    string constant private VOTER_INDEX_FIELD = "blankBallot";
    string constant private VOTER_DATA_FIELD = "voteStorage";

    string constant private COUNTER_INDEX_FIELD = "counterId";
    string constant private COUNTER_DATA1_FIELD = "hPointShare";
    string constant private COUNTER_DATA2_FIELD = "decryptedResultPartStorage";
    string constant private COUNTER_DATA_FIELD = "hPointShare, decryptedResultPartStorage";

    string constant private ERROR_VOTER_REVOTE = "Voter is already voted.";
    string constant private ERROR_VOTER_REQUEST = "Verifies vote request failed.";
    string constant private ERROR_COUNTER_EXIST = "Counters is already exist.";
    string constant private ERROR_COUNTER_REQUEST = "Verifies count request failed.";
    string constant private ERROR_VERIFY_VOTE_RESULT = "Verifies vote result failed.";
    string constant private ERROR_OTHER = "No permission or other errors.";
    uint constant private MIN_COUNTER_NUMBER = 2;

    // Constructor.
    constructor() public {
        owner = msg.sender;
        contractState = ContractState.Initializing;
        wedpr = WedprPrecompiled(WEDPR_PRECOMPILED_ADDRESS);
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
    function init(string voterTableName, string counterTableName) public returns(int, int) {
        require(contractState == ContractState.Initializing, "Voting has started.");
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        int voterTableResult = tf.createTable(voterTableName, VOTER_INDEX_FIELD, VOTER_DATA_FIELD);
        int counterTableNameResult = tf.createTable(counterTableName, COUNTER_INDEX_FIELD, COUNTER_DATA_FIELD);

        return (voterTableResult, counterTableNameResult);
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
    function insertHPointShare(string counterTableName, string counterId, string hPointShare) public returns(int) {
        require(contractState == ContractState.Initializing, "Voting has started.");
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        Table table = tf.openTable(counterTableName);
        Entry entry = table.newEntry();
        entry.set(COUNTER_DATA1_FIELD, hPointShare);

        string[] memory hPointShares = queryHPointShare(counterTableName, counterId);
        require(hPointShares.length == 0, ERROR_COUNTER_EXIST);
        int records = table.insert(counterId, entry);
        require(records == 1, ERROR_OTHER);
        counterNumber = counterNumber + 1;

        // Accumulates hPoint share
        string memory newHPointSum = wedpr.anonymousVotingAggregateHPoint(hPointShare, systemParametersStruct.hPoint);
        systemParametersStruct.hPoint = newHPointSum;

        return records;
    }

    // Queries a counter hPoint share.
    function queryHPointShare(string counterTableName, string counterId) public view returns(string[]) {
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        Table table = tf.openTable(counterTableName);
        Entries entries = table.select(counterId, table.newCondition());
        string[] memory hPointShares = new string[](uint256(entries.size()));
        for(int i=0; i<entries.size(); ++i) {
            Entry entry = entries.get(i);
            hPointShares[uint256(i)] = entry.getString(COUNTER_DATA1_FIELD);
        }
        return hPointShares;
    }

    // Gets hPoint.
    function getHPoint() public view returns(string){
        require(contractState != ContractState.Initializing, "Voting has not yet started.");
        return systemParametersStruct.hPoint;
    }

    // Voter votes ballots if the request passed the validation.
    function verifyBoundedVoteRequest(string voterTableName, string systemParameters, string voteRequest) public returns(string) {
        require(contractState == ContractState.Voting, "Voting is not open now.");
        int result = wedpr.anonymousVotingVerifyBoundedVoteRequest(systemParameters, voteRequest);
        require(result == 0, ERROR_VOTER_REQUEST);
        
        string memory blankBallot = "";
        string memory voteStoragePart = "";
        (blankBallot, voteStoragePart, voteStorageSumTotal) = wedpr.anonymousVotingAggregateVoteSumResponse(systemParameters, voteRequest, voteStorageSumTotal);
        
        Table table = openTable(voterTableName);
        string[] memory voteStorageParts = queryVoteStoragePart(voterTableName, blankBallot);
        require(voteStorageParts.length == 0, ERROR_VOTER_REVOTE);
        int records = insertVoteBallot(table, blankBallot, voteStoragePart);
        require(records == 1, ERROR_OTHER);

        return blankBallot;
    }

    // Voter votes ballots if the request passed the validation.
    function verifyUnboundedVoteRequest(string voterTableName, string systemParameters, string voteRequest) public returns(string){
        require(contractState == ContractState.Voting, "Voting is not open now.");
        int result = wedpr.anonymousVotingVerifyUnboundedVoteRequest(systemParameters, voteRequest);
        require(result == 0, ERROR_VOTER_REQUEST);

        string memory blankBallot = "";
        string memory voteStoragePart = "";
        (blankBallot, voteStoragePart, voteStorageSumTotal) = wedpr.anonymousVotingAggregateVoteSumResponse(systemParameters, voteRequest, voteStorageSumTotal);
        
        Table table = openTable(voterTableName);
        string[] memory voteStorageParts = queryVoteStoragePart(voterTableName, blankBallot);
        require(voteStorageParts.length == 0, ERROR_VOTER_REVOTE);
        int records = insertVoteBallot(table, blankBallot, voteStoragePart);
        require(records == 1, ERROR_OTHER);

        return blankBallot;
    }

    // Queries a voter storage part.
    function queryVoteStoragePart(string voterTableName, string blankBallot) public view returns(string[]) {
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        Table table = tf.openTable(voterTableName);
        Entries entries = table.select(blankBallot, table.newCondition());
        string[] memory voteStorageParts = new string[](uint256(entries.size()));
        for(int i=0; i<entries.size(); ++i) {
            Entry entry = entries.get(i);
            voteStorageParts[uint256(i)] = entry.getString(VOTER_DATA_FIELD);
        }
        return voteStorageParts;
    }

    // Gets voteStorageSumTotal.
    function getVoteStorageSumTotal() public view returns(string){
        require(uint(contractState) > uint(ContractState.Voting), "Voting is still open.");
        return voteStorageSumTotal;
    }

    // Updates counter decryptedResultPartStoragePart if the request passed the validation.
    function verifyCountRequest(string counterTableName, string systemParameters, string voteStorage, 
        string hPointShare, string decryptedRequest) public {
        require(contractState == ContractState.CountingStep1, "It should be called during the CountingStep1 state.");
        int result = wedpr.anonymousVotingVerifyCountRequest(systemParameters, voteStorage, hPointShare, decryptedRequest);
        require(result == 0, ERROR_COUNTER_REQUEST);
        
        string memory counterId = "";
        string memory decryptedResultPartStoragePart = "";
        (counterId, decryptedResultPartStoragePart, decryptedResultPartStorageSumTotal) = wedpr.anonymousVotingAggregateDecryptedPartSum(systemParameters, 
                decryptedRequest, decryptedResultPartStorageSumTotal);

        Table table = openTable(counterTableName);
        int records = updateCountBallot(table, counterId, decryptedResultPartStoragePart);
        require(records == 1, ERROR_OTHER);
    }

    // Any one verifies vote result.
    function verifyVoteResult(string systemParameters, string voteStorageSum, string decryptedResultPartStorageSum, 
        string voteResultRequest) public {
        require(contractState == ContractState.CountingStep2, "It should be called during the CountingStep2 state.");
        int result = wedpr.anonymousVotingVerifyVoteResult(systemParameters, voteStorageSum, decryptedResultPartStorageSum, voteResultRequest);
        require(result == 0, ERROR_VERIFY_VOTE_RESULT);

        // save vote result to blockchain
        string memory newVoteResultStorage = wedpr.anonymousVotingGetVoteResultFromRequest(voteResultRequest);
        voteResultStorage = newVoteResultStorage;
    }

    // Gets decryptedResultPartStorageSumTotal.
    function getDecryptedResultPartStorageSumTotal() public view returns(string){
        require(uint(contractState) > uint(ContractState.CountingStep1), "It should be called during the CountingStep2 or End state.");
        return decryptedResultPartStorageSumTotal;
    }

    // Gets vote result storage.
    function getVoteResultStorage() public view returns(string) {
        require(contractState == ContractState.End, "Counting has not yet finished.");
        return voteResultStorage;
    }

    // Utility function section.

    // Gets a table handler by opening it.
    function openTable(string tableName) private returns(Table) {
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        Table table = tf.openTable(tableName);
        return table;
    }

    // Inserts a voter ballot.
    function insertVoteBallot(Table table, string blankBallot, string voteStorage) private returns(int) {
        Entry entry = table.newEntry();
        entry.set(VOTER_DATA_FIELD, voteStorage);
        return table.insert(blankBallot, entry);
    }

    // Updates a counter ballot.
    function updateCountBallot(Table table, string counterId, string decryptedResultPartStoragePart) private returns(int) {
        Entry entry = table.newEntry();
        entry.set(COUNTER_DATA2_FIELD, decryptedResultPartStoragePart);
        return table.update(counterId, entry, table.newCondition());
    }
}

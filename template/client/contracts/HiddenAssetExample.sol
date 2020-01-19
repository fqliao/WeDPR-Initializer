pragma solidity ^0.4.24;
pragma experimental ABIEncoderV2;

import "./Table.sol";
import "./WedprPrecompiled.sol";
import "./ParallelContract.sol";

// Example application contract implementing hidden asset suite.
// Please feel free to modify it according to your business demands.
contract HiddenAssetExample is ParallelContract {
    WedprPrecompiled wedpr;
    TableFactory tableFactory;

    address constant private TABLE_FACTORY_PRECOMPILED_ADDRESS = 0x1001;
    address constant private WEDPR_PRECOMPILED_ADDRESS = 0x5018;

    // hidden asset table
    string hiddenAssetTableName;
    string constant private CREDIT_INDEX_FIELD = "currentCredit";
    string constant private CREDIT_DATA_FIELD = "creditStorage";

    // regulationInfo table
    string regulationInfoTableName;
    string constant private REGULATOR_DATA1_FIELD = "spentCredit";
    string constant private REGULATOR_DATA2_FIELD = "regulationInfo";
    string constant private REGULATOR_DATA_FIELD = "spentCredit,regulationInfo";

    string constant private ERROR_CREDIT_SPENT = "the credit already spent";
    string constant private ERROR_CREDIT_EXIST = "the credit already exists";
    string constant private ERROR_REGULATOR_DATA_EXIST = "the regulation info already exists";
    string constant private ERROR_OTHER = "no permission or other errors";
    int constant private TABLE_EXSIST = -50001;

    // Variable struct used by credit transferring action.
    // It is used to overcome solidity's restriction that the number of
    // the total number of local variables cannot exceed a limit.
    struct TransferVars {
        string spentCurrentCredit;
        string spentCreditStorage;
        string newCurrentCredit;
        string newCreditStorage;
        string unused;
    }

    // Variable struct used by credit splitting action.
    // It is used to overcome solidity's restriction that the number of
    // the total number of local variables cannot exceed a limit.
    struct SplitVars {
        string spentCurrentCredit;
        string spentCreditStorage;
        string newCurrentCredit1;
        string newCreditStorage1;
        string newCurrentCredit2;
        string newCreditStorage2;
        string unused;
    }

    // Constructor.
    constructor() public {
        wedpr = WedprPrecompiled(WEDPR_PRECOMPILED_ADDRESS);
        tableFactory = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
    }

    // Initializes the data tables.
    function init(string _hiddenAssetTableName, string _regulationInfoTableName) public {
        hiddenAssetTableName = _hiddenAssetTableName;
        regulationInfoTableName = _regulationInfoTableName;

        int result1 = tableFactory.createTable(hiddenAssetTableName, CREDIT_INDEX_FIELD, CREDIT_DATA_FIELD);
        int result2 = tableFactory.createTable(regulationInfoTableName, CREDIT_INDEX_FIELD, REGULATOR_DATA_FIELD);

        require(result1 == 0 || result1 == TABLE_EXSIST, ERROR_OTHER);
        require(result2 == 0 || result2 == TABLE_EXSIST, ERROR_OTHER);
    }

    // Registers parallel api
    function enableParallel() public
    {
        registerParallelFunction("issueCredit(string)", 1);
        registerParallelFunction("fulfillCredit(string)", 1);
        registerParallelFunction("transferCredit(string)", 1);
        registerParallelFunction("splitCredit(string)", 1);
    } 

    // Disable parallel api
    function disableParallel() public
    {
        unregisterParallelFunction("issueCredit(string)");
        unregisterParallelFunction("fulfillCredit(string)"); 
        unregisterParallelFunction("transferCredit(string)"); 
        unregisterParallelFunction("splitCredit(string)"); 
    }

    // Issues a new credit if the request passed the validation.
    function issueCredit(string issueArgument) public {
        // verify the issued credit
        string memory currentCredit = "";
        string memory creditStorage = "";
        (currentCredit, creditStorage) = wedpr.hiddenAssetVerifyIssuedCredit(issueArgument);

        // query the issued credit
        int errorCode = 0;
        string memory unused = "";
        (errorCode, unused) = queryCredit(currentCredit);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // save the issued credit on the blockchain
        int records = insertUnspentCredit(currentCredit, creditStorage);
        require(records == 1, ERROR_OTHER);
    }

    // Fulfills an existing credit if the request passed the validation.
    function fulfillCredit(string fulfillArgument) public {
        // verify the fulfilled credit
        string memory currentCredit = "";
        string memory creditStorage = "";
        (currentCredit, creditStorage) = wedpr.hiddenAssetVerifyFulfilledCredit(fulfillArgument);

        // query the fulfilled credit
        int errorCode = 0;
        string memory unused = "";
        (errorCode, unused) = queryCredit(currentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // remove the fulfilled credit
        int records = removeUnspentCredit(currentCredit);
        require(records == 1, ERROR_OTHER);
    }

    // Transfers an existing credit if the request passed the validation.
    function transferCredit(string transferRequest) public {
        // verify the transfer credit
        TransferVars memory transferVars = TransferVars("", "", "", "", "");

        (transferVars.spentCurrentCredit, transferVars.spentCreditStorage,
         transferVars.newCurrentCredit, transferVars.newCreditStorage)
            = wedpr.hiddenAssetVerifyTransferredCredit(transferRequest);

        // query the spent current credit
        int errorCode = 0;
        (errorCode, transferVars.unused) = queryCredit(transferVars.spentCurrentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // query the new current credit
        (errorCode, transferVars.unused) = queryCredit(transferVars.newCurrentCredit);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // update the transfer credit
        int records = removeUnspentCredit(transferVars.spentCurrentCredit);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(transferVars.newCurrentCredit,
            transferVars.newCreditStorage);
        require(records == 1, ERROR_OTHER);
    }

    // Splits an existing credit if the request passed the validation.
    function splitCredit(string splitRequest) public {
        // verify the split credit
        SplitVars memory splitVars = SplitVars("", "", "", "", "", "", "");
        (splitVars.spentCurrentCredit, splitVars.spentCreditStorage,
         splitVars.newCurrentCredit1, splitVars.newCreditStorage1,
         splitVars.newCurrentCredit2, splitVars.newCreditStorage2)
            = wedpr.hiddenAssetVerifySplitCredit(splitRequest);

        // query the spent current credit
        int errorCode = 0;
        (errorCode, splitVars.unused) = queryCredit(splitVars.spentCurrentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // query the new current credit1
        (errorCode, splitVars.unused) = queryCredit(splitVars.newCurrentCredit1);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // query the new current credit2
        (errorCode, splitVars.unused) = queryCredit(splitVars.newCurrentCredit2);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // update the transfer credit
        int records = removeUnspentCredit(splitVars.spentCurrentCredit);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(splitVars.newCurrentCredit1, splitVars.newCreditStorage1);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(splitVars.newCurrentCredit2, splitVars.newCreditStorage2);
        require(records == 1, ERROR_OTHER);
    }

    // Inserts regulation information.
    function insertRegulationInfo(string currentCredit, string spentCredit, string regulationInfo) public {
        string[] memory currentCredits;
        string[] memory spentCredits;
        string[] memory regulationInfos;
        (currentCredits, spentCredits, regulationInfos) = queryRegulationInfo(currentCredit);
        require(currentCredits.length == 0 && spentCredits.length == 0 && regulationInfos.length == 0, ERROR_REGULATOR_DATA_EXIST);

        Table table = tableFactory.openTable(regulationInfoTableName);
        Entry entry = table.newEntry();
        entry.set(REGULATOR_DATA1_FIELD, spentCredit);
        entry.set(REGULATOR_DATA2_FIELD, regulationInfo);
        int result = table.insert(currentCredit, entry);
        require(result == 1, ERROR_OTHER);
    }

    // Queries whether an unspent credit exists.
    function queryCredit(string currentCredit)
        public view returns(int, string) {
        Table table = tableFactory.openTable(hiddenAssetTableName);
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCredit);

        Entries entries = table.select(currentCredit, condition);
        if (0 == uint(entries.size())) {
            return (-1, "");
        } else {
            Entry entry = entries.get(0);
            return (0, entry.getString(CREDIT_DATA_FIELD));
        }
    }

    // Queries regulation information.
    function queryRegulationInfo(string currentCredit) public view returns(string[], string[], string[]){
        Table table = tableFactory.openTable(regulationInfoTableName);
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCredit);
        Entries entries = table.select(currentCredit, condition);

        uint size = uint(entries.size());
        string[] memory currentCredits = new string[](size);
        string[] memory spentCredits = new string[](size);
        string[] memory regulationInfos = new string[](size);
        for(uint i = 0; i < size; ++i) {
            Entry entry = entries.get(int(i));
            currentCredits[i] = entry.getString(CREDIT_INDEX_FIELD);
            spentCredits[i] = entry.getString(REGULATOR_DATA1_FIELD);
            regulationInfos[i] = entry.getString(REGULATOR_DATA2_FIELD);
        }

        return (currentCredits, spentCredits, regulationInfos);
    }

    // Utility function section.

    // Inserts a credit record.
    function insertUnspentCredit(string currentCredit, string creditStorage)
        private returns(int) {
        Table table = tableFactory.openTable(hiddenAssetTableName);
        Entry entry = table.newEntry();
        entry.set(CREDIT_DATA_FIELD, creditStorage);
        return table.insert(currentCredit, entry);
    }

    // Removes a credit record.
    function removeUnspentCredit(string currentCredit) private returns(int) {
        Table table = tableFactory.openTable(hiddenAssetTableName);
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCredit);
        return table.remove(currentCredit, condition);
    }
}

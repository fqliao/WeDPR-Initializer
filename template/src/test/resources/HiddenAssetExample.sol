pragma solidity ^0.4.24;
pragma experimental ABIEncoderV2;

import "./Table.sol";
import "./WedprPrecompiled.sol";

// Example application contract implementing hidden asset suite.
// Please feel free to modify it according to your business demands.
contract HiddenAssetExample {
    WedprPrecompiled wedpr;
    string constant private ERROR_CREDIT_SPENT = "the credit already spent";
    string constant private ERROR_CREDIT_EXIST = "the credit already exists";
    string constant private ERROR_REGULATOR_DATA_EXIST = "the regulation info already exists";
    string constant private ERROR_OTHER = "no permission or other errors";
    int constant private TABLE_EXSIST = -50001;

    string constant private CREDIT_INDEX_FIELD = "currentCredit";
    string constant private CREDIT_DATA_FIELD = "creditStorage";
    string constant private REGULATOR_DATA1_FIELD = "spentCredit";
    string constant private REGULATOR_DATA2_FIELD = "regulationInfo";
    string constant private REGULATOR_DATA_FIELD = "spentCredit,regulationInfo";

    address constant private TABLE_FACTORY_PRECOMPILED_ADDRESS = 0x1001;
    address constant private WEDPR_PRECOMPILED_ADDRESS = 0x5018;

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
    }

    // Initializes the data tables.
    function init(string hiddenAssetTable, string regulationInfoTable) public {
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        int result1 = tf.createTable(hiddenAssetTable, CREDIT_INDEX_FIELD, CREDIT_DATA_FIELD);
        int result2 = tf.createTable(regulationInfoTable, CREDIT_INDEX_FIELD, REGULATOR_DATA_FIELD);
        require(result1 == 0 || result1 == TABLE_EXSIST, ERROR_OTHER);
        require(result2 == 0 || result2 == TABLE_EXSIST, ERROR_OTHER);
    }

    // Queries whether an unspent credit exists.
    function queryCredit(string hiddenAssetTable, string currentCreditPb)
        public view returns(int, string) {
        Table table = openTable(hiddenAssetTable);
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCreditPb);

        Entries entries = table.select(currentCreditPb, condition);
        if (0 == uint(entries.size())) {
            return (-1, "");
        } else {
            Entry entry = entries.get(0);
            return (0, entry.getString(CREDIT_DATA_FIELD));
        }
    }

    // Issues a new credit if the request passed the validation.
    function issueCredit(
        string hiddenAssetTable, string issueArgumentPb) public {
        // verify the issued credit
        string memory currentCredit = "";
        string memory creditStorage = "";
        (currentCredit, creditStorage) =
            wedpr.hiddenAssetVerifyIssuedCredit(issueArgumentPb);

        // query the issued credit
        int errorCode = 0;
        string memory unused = "";
        (errorCode, unused) = queryCredit(hiddenAssetTable, currentCredit);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // save the issued credit on the blockchain
        Table table = openTable(hiddenAssetTable);
        int records = insertUnspentCredit(table, currentCredit, creditStorage);
        require(records == 1, ERROR_OTHER);
    }

    // Fulfills an existing credit if the request passed the validation.
    function fulfillCredit(
        string hiddenAssetTable, string fulfillArgumentPb) public {
        // verify the fulfilled credit
        string memory currentCredit = "";
        string memory creditStorage = "";
        (currentCredit, creditStorage) =
            wedpr.hiddenAssetVerifyFulfilledCredit(fulfillArgumentPb);

        // query the fulfilled credit
        int errorCode = 0;
        string memory unused = "";
        (errorCode, unused) = queryCredit(hiddenAssetTable, currentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // remove the fulfilled credit
        Table table = openTable(hiddenAssetTable);
        int records = removeUnspentCredit(table, currentCredit);
        require(records == 1, ERROR_OTHER);
    }

    // Transfers an existing credit if the request passed the validation.
    function transferredCredit(
        string hiddenAssetTable, string transferRequestPb) public {
        // verify the transfer credit
        TransferVars memory transferVars = TransferVars("", "", "", "", "");

        (transferVars.spentCurrentCredit, transferVars.spentCreditStorage,
         transferVars.newCurrentCredit, transferVars.newCreditStorage)
            = wedpr.hiddenAssetVerifyTransferredCredit(transferRequestPb);

        // query the spent current credit
        int errorCode = 0;
        (errorCode, transferVars.unused) =
            queryCredit(hiddenAssetTable, transferVars.spentCurrentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // query the new current credit
        (errorCode, transferVars.unused) =
            queryCredit(hiddenAssetTable, transferVars.newCurrentCredit);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // update the transfer credit
        Table table = openTable(hiddenAssetTable);
        int records = removeUnspentCredit(
            table, transferVars.spentCurrentCredit);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(
            table, transferVars.newCurrentCredit,
            transferVars.newCreditStorage);
        require(records == 1, ERROR_OTHER);
    }

    // Splits an existing credit if the request passed the validation.
    function splitCredit(string hiddenAssetTable, string splitRequestPb) public {
        // verify the split credit
        SplitVars memory splitVars = SplitVars("", "", "", "", "", "", "");
        (splitVars.spentCurrentCredit, splitVars.spentCreditStorage,
         splitVars.newCurrentCredit1, splitVars.newCreditStorage1,
         splitVars.newCurrentCredit2, splitVars.newCreditStorage2)
            = wedpr.hiddenAssetVerifySplitCredit(splitRequestPb);

        // query the spent current credit
        int errorCode = 0;
        (errorCode, splitVars.unused) =
            queryCredit(hiddenAssetTable, splitVars.spentCurrentCredit);
        require(errorCode == 0, ERROR_CREDIT_SPENT);

        // query the new current credit1
        (errorCode, splitVars.unused) =
            queryCredit(hiddenAssetTable, splitVars.newCurrentCredit1);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // query the new current credit2
        (errorCode, splitVars.unused) =
            queryCredit(hiddenAssetTable, splitVars.newCurrentCredit2);
        require(errorCode != 0, ERROR_CREDIT_EXIST);

        // update the transfer credit
        Table table = openTable(hiddenAssetTable);
        int records = removeUnspentCredit(table, splitVars.spentCurrentCredit);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(
            table, splitVars.newCurrentCredit1, splitVars.newCreditStorage1);
        require(records == 1, ERROR_OTHER);

        records = insertUnspentCredit(
            table, splitVars.newCurrentCredit2, splitVars.newCreditStorage2);
        require(records == 1, ERROR_OTHER);
    }

    // Inserts regulation information.
    function insertRegulationInfo(string regulationInfoTable, string currentCreditPb, string spentCreditPb, string regulationInfoPb) public {
        Table table = openTable(regulationInfoTable);
        string[] memory currentCredits;
        string[] memory spentCredits;
        string[] memory regulationInfos;
        (currentCredits, spentCredits, regulationInfos) = queryRegulationInfo(regulationInfoTable, currentCreditPb);
        require(currentCredits.length == 0 && spentCredits.length == 0 && regulationInfos.length == 0, ERROR_REGULATOR_DATA_EXIST);

        Entry entry = table.newEntry();
        entry.set(REGULATOR_DATA1_FIELD, spentCreditPb);
        entry.set(REGULATOR_DATA2_FIELD, regulationInfoPb);
        int result = table.insert(currentCreditPb, entry);
        require(result == 1, ERROR_OTHER);
    }

    // Queries regulation information.
    function queryRegulationInfo(string regulationInfoTable, string currentCreditPb) public view returns(string[], string[], string[]){
        Table table = openTable(regulationInfoTable);
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCreditPb);
        Entries entries = table.select(currentCreditPb, condition);

        string[] memory currentCredits = new string[](uint256(entries.size()));
        string[] memory spentCredits = new string[](uint256(entries.size()));
        string[] memory regulationInfos = new string[](uint256(entries.size()));
        for(int i = 0; i < entries.size(); ++i) {
            Entry entry = entries.get(i);
            currentCredits[uint256(i)] = entry.getString(CREDIT_INDEX_FIELD);
            spentCredits[uint256(i)] = entry.getString(REGULATOR_DATA1_FIELD);
            regulationInfos[uint256(i)] = entry.getString(REGULATOR_DATA2_FIELD);
        }

        return (currentCredits, spentCredits, regulationInfos);
    }

    // Utility function section.

    // Gets a table handler by opening it.
    function openTable(string hiddenAssetTable) private returns(Table) {
        TableFactory tf = TableFactory(TABLE_FACTORY_PRECOMPILED_ADDRESS);
        Table table = tf.openTable(hiddenAssetTable);
        return table;
    }

    // Inserts a credit record.
    function insertUnspentCredit(
        Table table, string currentCreditPb, string creditStoragePb)
        private returns(int) {
        Entry entry = table.newEntry();
        entry.set(CREDIT_DATA_FIELD, creditStoragePb);
        return table.insert(currentCreditPb, entry);
    }

    // Removes a credit record.
    function removeUnspentCredit(
        Table table, string currentCreditPb) private returns(int) {
        Condition condition = table.newCondition();
        condition.EQ(CREDIT_INDEX_FIELD, currentCreditPb);
        return table.remove(currentCreditPb, condition);
    }
}

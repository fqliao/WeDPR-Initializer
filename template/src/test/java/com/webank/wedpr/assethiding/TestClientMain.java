package com.webank.wedpr.assethiding;

import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.CommandUtils;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.assethiding.DemoMain;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;
import com.webank.wedpr.example.assethiding.FulfillCreditExampleProtocol;
import com.webank.wedpr.example.assethiding.IssueCreditExampleProtocol;
import com.webank.wedpr.example.assethiding.SplitCreditExampleProtocol;
import com.webank.wedpr.example.assethiding.StorageExampleClient;
import com.webank.wedpr.example.assethiding.TransferCreditExampleProtocol;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;

class UserWallet {
    public byte[] masterSecret;
    public List<CreditCredential> creditCredentials;
    public BigInteger numbericBalanceTotal;
    public List<BigInteger> numbericBalance;
    public List<String> stringBalance;

    public UserWallet() {
        creditCredentials = new ArrayList<>();
        numbericBalanceTotal = BigInteger.ZERO;
        numbericBalance = new ArrayList<>();
        stringBalance = new ArrayList<>();
    }
}

public class TestClientMain {

    public static void main(String[] args) throws Exception {
        OwnerClient ownerClient = new OwnerClient();
        RedeemerClient redeemerClient = new RedeemerClient();
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();

        StorageExampleClient storageClient = DemoMain.initBlockchain();

        System.out.println("Welcome to test hidden asset!");
        CommandUtils.help();
        System.out.println("hiddenAssetTableName:" + DemoMain.hiddenAssetTableName);
        System.out.println("regulationInfoTableName:" + DemoMain.regulationInfoTableName);
        System.out.println();

        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        byte[] regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();
        byte[] regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();

        HashMap<String, UserWallet> userWallets = new HashMap<>();
        LineReader lineReader = CommandUtils.getLineReader();
        KeyMap<Binding> keymap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keymap.bind(new Reference("beginning-of-line"), "\033[1~");
        keymap.bind(new Reference("end-of-line"), "\033[4~");

        while (true) {
            String request = lineReader.readLine("> ");
            String[] params = CommandUtils.tokenizeCommand(request);
            if (params.length < 1 || "".equals(params[0].trim())) {
                System.out.print("");
                continue;
            }
            String userName = "";
            UserWallet userWallet = null;
            List<CreditCredential> creditCredentials = null;
            switch (params[0]) {
                case "issueCredit":
                    if (params.length < 3) {
                        System.out.println("Error: please provide 3 parameters.");
                        System.out.println("For example1: issueCredit A 100");
                        System.out.println("For example2: issueCredit A \"a movie ticket\"\n");
                        break;
                    }
                    userName = params[1];
                    byte[] masterSecret = null;
                    BigInteger oldNumbericBalance = BigInteger.ZERO;
                    if (!userWallets.containsKey(userName)) {
                        masterSecret = getMasterSecret();
                        userWallet = new UserWallet();
                        userWallet.masterSecret = masterSecret;
                    } else {
                        userWallet = userWallets.get(userName);
                        masterSecret = userWallet.masterSecret;
                        oldNumbericBalance = userWallet.numbericBalanceTotal;
                    }
                    CreditValue creditValue = null;
                    long issueNumbericValue = 0;
                    String issueStringValue = "";
                    // issue string credit
                    if (params[2].startsWith("\"") && params[2].endsWith("\"")) {
                        issueStringValue = params[2].substring(1, params[2].length() - 1);
                        creditValue =
                                CreditValue.newBuilder().setStringValue(issueStringValue).build();
                        CreditCredential creditCredential =
                                IssueCreditExampleProtocol.issueCredit(
                                        TransferType.NonNumberic,
                                        redeemerClient,
                                        redeemerKeyPair,
                                        creditValue,
                                        storageClient,
                                        ownerClient,
                                        masterSecret,
                                        regulatorPublicKey);

                        DemoMain.printIssueCreditInfo(
                                storageClient,
                                publicKeyCrypto,
                                regulatorSecretKey,
                                creditCredential);

                        creditCredentials = userWallet.creditCredentials;
                        creditCredentials.add(creditCredential);
                        List<String> stringBalance = userWallet.stringBalance;
                        stringBalance.add(
                                creditCredential
                                        .getCreditSecret()
                                        .getCreditValue()
                                        .getStringValue());
                        userWallets.put(userName, userWallet);
                        System.out.println(
                                userName
                                        + " +'"
                                        + issueStringValue
                                        + "', stringBalance is "
                                        + stringBalance
                                        + ".\n");
                    } else {
                        // issue numbric credit
                        try {
                            issueNumbericValue = Long.parseLong(params[2]);
                        } catch (NumberFormatException e1) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        if (issueNumbericValue <= 0) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        creditValue =
                                CreditValue.newBuilder()
                                        .setNumericalValue(issueNumbericValue)
                                        .build();
                        CreditCredential creditCredential =
                                IssueCreditExampleProtocol.issueCredit(
                                        TransferType.Numberic,
                                        redeemerClient,
                                        redeemerKeyPair,
                                        creditValue,
                                        storageClient,
                                        ownerClient,
                                        masterSecret,
                                        regulatorPublicKey);

                        DemoMain.printIssueCreditInfo(
                                storageClient,
                                publicKeyCrypto,
                                regulatorSecretKey,
                                creditCredential);

                        userWallet.creditCredentials.add(creditCredential);
                        long issuedValue =
                                creditCredential
                                        .getCreditSecret()
                                        .getCreditValue()
                                        .getNumericalValue();
                        userWallet.numbericBalance.add(BigInteger.valueOf(issuedValue));
                        BigInteger newNumbericBalanceTotal =
                                oldNumbericBalance.add(BigInteger.valueOf(issuedValue));
                        userWallet.numbericBalanceTotal = newNumbericBalanceTotal;
                        userWallets.put(userName, userWallet);
                        System.out.println(
                                "\n"
                                        + userName
                                        + " +"
                                        + issuedValue
                                        + ", numbericBalance is "
                                        + userWallet.numbericBalance
                                        + ".");
                        System.out.println(
                                "numbericBalanceTotal is " + newNumbericBalanceTotal + ".\n");
                    }

                    break;
                case "fulfillCredit":
                    if (params.length < 3) {
                        System.out.println("Error: please provide 3 parameters.");
                        System.out.println("For example1: fulfillCredit A 100");
                        System.out.println("For example2: fulfillCredit A \"a movie ticket\"\n");
                        break;
                    }
                    userName = params[1];
                    if (!userWallets.containsKey(userName)) {
                        System.out.println("Error: " + userName + " does not have any credit.\n");
                        break;
                    }
                    userWallet = userWallets.get(userName);
                    creditCredentials = userWallet.creditCredentials;
                    // fulfill string credit
                    if (params[2].startsWith("\"") && params[2].endsWith("\"")) {
                        String fulfillStringValue = params[2].substring(1, params[2].length() - 1);
                        CreditCredential fulfillCreditCredentials;
                        try {
                            fulfillCreditCredentials =
                                    creditCredentials
                                            .stream()
                                            .filter(
                                                    x ->
                                                            x.getCreditSecret()
                                                                    .getCreditValue()
                                                                    .getStringValue()
                                                                    .equals(fulfillStringValue))
                                            .findAny()
                                            .get();
                        } catch (NoSuchElementException e) {
                            System.out.println(
                                    "Error: "
                                            + userName
                                            + " does not have '"
                                            + fulfillStringValue
                                            + "' credit.\n");
                            break;
                        }
                        FulfillCreditExampleProtocol.fulfillCredit(
                                TransferType.NonNumberic,
                                redeemerClient,
                                redeemerKeyPair,
                                fulfillCreditCredentials,
                                storageClient);
                        List<String> stringBalance = userWallet.stringBalance;
                        stringBalance.remove(fulfillStringValue);
                        userWallet.creditCredentials.remove(fulfillCreditCredentials);
                        userWallets.put(userName, userWallet);
                        System.out.println(
                                userName
                                        + " -'"
                                        + fulfillStringValue
                                        + "', stringBalance is "
                                        + stringBalance
                                        + ".\n");
                    } else {
                        // fulfill numberic credit
                        BigInteger fulfillNumbericValue = BigInteger.ZERO;
                        try {
                            fulfillNumbericValue = BigInteger.valueOf(Long.parseLong(params[2]));
                        } catch (NumberFormatException e1) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        if (fulfillNumbericValue.compareTo(BigInteger.ZERO) <= 0) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        final long longValue = fulfillNumbericValue.longValue();
                        CreditCredential fulfillCreditCredentials;
                        try {
                            fulfillCreditCredentials =
                                    creditCredentials
                                            .stream()
                                            .filter(
                                                    x ->
                                                            x.getCreditSecret()
                                                                            .getCreditValue()
                                                                            .getNumericalValue()
                                                                    == longValue)
                                            .findAny()
                                            .get();
                        } catch (NoSuchElementException e) {
                            System.out.println(
                                    "Error: "
                                            + userName
                                            + " does not have "
                                            + fulfillNumbericValue
                                            + " credit.\n");
                            break;
                        }
                        FulfillCreditExampleProtocol.fulfillCredit(
                                TransferType.Numberic,
                                redeemerClient,
                                redeemerKeyPair,
                                fulfillCreditCredentials,
                                storageClient);
                        oldNumbericBalance = userWallet.numbericBalanceTotal;
                        BigInteger newValue = oldNumbericBalance.subtract(fulfillNumbericValue);
                        userWallet.numbericBalanceTotal = newValue;
                        userWallet.creditCredentials.remove(fulfillCreditCredentials);
                        userWallet.numbericBalance.remove(fulfillNumbericValue);
                        userWallets.put(userName, userWallet);
                        System.out.println(
                                userName
                                        + " -"
                                        + fulfillNumbericValue
                                        + ", numbericBalance is "
                                        + userWallet.numbericBalance
                                        + ".");
                        System.out.println(
                                "numbericBalanceTotal is "
                                        + userWallet.numbericBalanceTotal
                                        + ".\n");
                    }
                    break;
                case "transferCredit":
                    if (params.length < 4) {
                        System.out.println("Error: please provide at least 4 parameters.");
                        System.out.println("For example1: transferCredit A B 100");
                        System.out.println("For example2: transferCredit A B 100 \"computer\"\n");
                        break;
                    }
                    String senderName = params[1];
                    String receiverName = params[2];
                    String value = params[3];
                    String message = "";
                    if (params.length == 5) {
                        message = params[4];
                    }
                    if (!userWallets.containsKey(senderName)) {
                        System.out.println("Error: " + userName + " does not have any credit.\n");
                        break;
                    }
                    byte[] receiverMasterSecret = null;
                    UserWallet receiverWallet = null;
                    if (!userWallets.containsKey(receiverName)) {
                        receiverMasterSecret = getMasterSecret();
                        receiverWallet = new UserWallet();
                        receiverWallet.masterSecret = receiverMasterSecret;
                    } else {
                        receiverWallet = userWallets.get(receiverName);
                        receiverMasterSecret = receiverWallet.masterSecret;
                    }
                    UserWallet senderWallet = userWallets.get(senderName);
                    List<CreditCredential> senderCreditCredentials = senderWallet.creditCredentials;
                    // transfer string credit
                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        String transferStringValue = value.substring(1, value.length() - 1);
                        CreditCredential transferCreditCredentials;
                        try {
                            transferCreditCredentials =
                                    senderCreditCredentials
                                            .stream()
                                            .filter(
                                                    x ->
                                                            x.getCreditSecret()
                                                                    .getCreditValue()
                                                                    .getStringValue()
                                                                    .equals(transferStringValue))
                                            .findAny()
                                            .get();
                        } catch (NoSuchElementException e) {
                            System.out.println(
                                    "Error: "
                                            + senderName
                                            + " does not have '"
                                            + transferStringValue
                                            + "' credit.\n");
                            break;
                        }
                        // init OwnerState
                        OwnerState senderOwnerState =
                                AssethidingUtils.getSenderOwnerStateForTransfer(
                                        transferCreditCredentials);
                        OwnerState receiverOwnerState =
                                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);
                        CreditValue transferCreditValue =
                                CreditValue.newBuilder()
                                        .setStringValue(transferStringValue)
                                        .build();
                        // set TransactionInfo
                        TransactionInfo transactionInfo =
                                TransactionInfo.newBuilder()
                                        .setCreditValue(transferCreditValue)
                                        .setTransactionMessage(message)
                                        .build();
                        // execute transfer
                        CreditCredential receiverCreditCredential =
                                TransferCreditExampleProtocol.transferCredit(
                                        TransferType.NonNumberic,
                                        ownerClient,
                                        senderOwnerState,
                                        receiverOwnerState,
                                        transactionInfo,
                                        storageClient,
                                        regulatorPublicKey);

                        DemoMain.printTransferCreditInfo(
                                storageClient,
                                publicKeyCrypto,
                                regulatorSecretKey,
                                receiverCreditCredential);

                        // set sender data
                        senderWallet.creditCredentials.remove(transferCreditCredentials);
                        senderWallet.stringBalance.remove(transferStringValue);
                        userWallets.put(senderName, senderWallet);
                        // set receiver data
                        receiverWallet.creditCredentials.add(receiverCreditCredential);
                        receiverWallet.stringBalance.add(transferStringValue);
                        userWallets.put(receiverName, receiverWallet);
                        System.out.println(
                                "\n"
                                        + senderName
                                        + " -'"
                                        + transferStringValue
                                        + "', stringBalance is "
                                        + senderWallet.stringBalance
                                        + ".");
                        System.out.println(
                                receiverName
                                        + " +"
                                        + transferStringValue
                                        + ", stringBalance is "
                                        + receiverWallet.stringBalance
                                        + ".\n");
                    } else {
                        // transfer numberic credit
                        BigInteger transferNumbericValue = BigInteger.ZERO;
                        try {
                            transferNumbericValue = BigInteger.valueOf(Long.parseLong(value));
                        } catch (NumberFormatException e1) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        if (transferNumbericValue.compareTo(BigInteger.ZERO) <= 0) {
                            System.out.println(
                                    "Error: please provide value by integer mode from 0 to "
                                            + Long.MAX_VALUE
                                            + ".\n");
                            break;
                        }
                        final long longTransferValue = transferNumbericValue.longValue();
                        CreditCredential transferCreditCredentials;
                        try {
                            transferCreditCredentials =
                                    senderCreditCredentials
                                            .stream()
                                            .filter(
                                                    x ->
                                                            x.getCreditSecret()
                                                                            .getCreditValue()
                                                                            .getNumericalValue()
                                                                    == longTransferValue)
                                            .findAny()
                                            .get();
                        } catch (NoSuchElementException e) {
                            System.out.println(
                                    "Error: "
                                            + senderName
                                            + " does not have "
                                            + longTransferValue
                                            + " credit.\n");
                            break;
                        }
                        // init OwnerState
                        OwnerState senderOwnerState =
                                AssethidingUtils.getSenderOwnerStateForTransfer(
                                        transferCreditCredentials);
                        OwnerState receiverOwnerState =
                                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);
                        CreditValue transferCreditValue =
                                CreditValue.newBuilder()
                                        .setNumericalValue(longTransferValue)
                                        .build();
                        // set TransactionInfo
                        TransactionInfo transactionInfo =
                                TransactionInfo.newBuilder()
                                        .setCreditValue(transferCreditValue)
                                        .setTransactionMessage(message)
                                        .build();
                        // execute transfer
                        CreditCredential receiverCreditCredential =
                                TransferCreditExampleProtocol.transferCredit(
                                        TransferType.Numberic,
                                        ownerClient,
                                        senderOwnerState,
                                        receiverOwnerState,
                                        transactionInfo,
                                        storageClient,
                                        regulatorPublicKey);

                        DemoMain.printTransferCreditInfo(
                                storageClient,
                                publicKeyCrypto,
                                regulatorSecretKey,
                                receiverCreditCredential);

                        // set sender data
                        senderWallet.creditCredentials.remove(transferCreditCredentials);
                        senderWallet.numbericBalance.remove(transferNumbericValue);
                        senderWallet.numbericBalanceTotal =
                                senderWallet.numbericBalanceTotal.subtract(transferNumbericValue);
                        userWallets.put(senderName, senderWallet);
                        // set receiver data
                        receiverWallet.creditCredentials.add(receiverCreditCredential);
                        receiverWallet.numbericBalance.add(transferNumbericValue);
                        receiverWallet.numbericBalanceTotal =
                                receiverWallet.numbericBalanceTotal.add(transferNumbericValue);
                        userWallets.put(receiverName, receiverWallet);
                        System.out.println(
                                "\n"
                                        + senderName
                                        + " -"
                                        + longTransferValue
                                        + ", numbericBalance is "
                                        + senderWallet.numbericBalance
                                        + ".");
                        System.out.println(
                                "numbericBalanceTotal is "
                                        + senderWallet.numbericBalanceTotal
                                        + ".\n");
                        System.out.println(
                                receiverName
                                        + " +"
                                        + longTransferValue
                                        + ", numbericBalance is "
                                        + receiverWallet.numbericBalance
                                        + ".");
                        System.out.println(
                                "numbericBalanceTotal is "
                                        + receiverWallet.numbericBalanceTotal
                                        + ".\n");
                    }

                    break;
                case "splitCredit":
                    if (params.length < 4) {
                        System.out.println("Error: please provide at least 4 parameters.");
                        System.out.println("For example1: splitCredit A B 60");
                        System.out.println("For example2: splitCredit A B 60 \"blockchain\"\n");
                        break;
                    }
                    senderName = params[1];
                    receiverName = params[2];
                    value = params[3];
                    message = "";
                    if (params.length == 5) {
                        message = params[4];
                    }
                    if (!userWallets.containsKey(senderName)) {
                        System.out.println("Error: " + userName + " does not have any credit.\n");
                        break;
                    }
                    if (!userWallets.containsKey(receiverName)) {
                        receiverMasterSecret = getMasterSecret();
                        receiverWallet = new UserWallet();
                        receiverWallet.masterSecret = receiverMasterSecret;
                    } else {
                        receiverWallet = userWallets.get(receiverName);
                        receiverMasterSecret = receiverWallet.masterSecret;
                    }
                    senderWallet = userWallets.get(senderName);
                    senderCreditCredentials = senderWallet.creditCredentials;
                    BigInteger splitNumbericValue = BigInteger.ZERO;
                    try {
                        splitNumbericValue = BigInteger.valueOf(Long.parseLong(value));
                    } catch (NumberFormatException e1) {
                        System.out.println(
                                "Error: please provide value by integer mode from 0 to "
                                        + Long.MAX_VALUE
                                        + ".\n");
                        break;
                    }
                    if (splitNumbericValue.compareTo(BigInteger.ZERO) <= 0) {
                        System.out.println(
                                "Error: please provide value by integer mode from 0 to "
                                        + Long.MAX_VALUE
                                        + ".\n");
                        break;
                    }
                    final long longSplitValue = splitNumbericValue.longValue();
                    CreditCredential splitCreditCredentials;
                    try {
                        splitCreditCredentials =
                                senderCreditCredentials
                                        .stream()
                                        .filter(
                                                x ->
                                                        x.getCreditSecret()
                                                                        .getCreditValue()
                                                                        .getNumericalValue()
                                                                > longSplitValue)
                                        .findAny()
                                        .get();
                    } catch (NoSuchElementException e) {
                        System.out.println(
                                "Error: "
                                        + senderName
                                        + " does not have greater than "
                                        + longSplitValue
                                        + " credit.\n");
                        break;
                    }
                    // init OwnerState
                    OwnerState senderOwnerState =
                            AssethidingUtils.getSenderOwnerStateForSplit(
                                    senderWallet.masterSecret, splitCreditCredentials);
                    OwnerState receiverOwnerState =
                            AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);
                    CreditValue splitCreditValue =
                            CreditValue.newBuilder().setNumericalValue(longSplitValue).build();
                    // set TransactionInfo
                    TransactionInfo transactionInfo =
                            TransactionInfo.newBuilder()
                                    .setCreditValue(splitCreditValue)
                                    .setTransactionMessage(message)
                                    .build();
                    // execute split
                    List<CreditCredential> creditCredentialResult =
                            SplitCreditExampleProtocol.splitCredit(
                                    ownerClient,
                                    senderOwnerState,
                                    receiverOwnerState,
                                    transactionInfo,
                                    storageClient,
                                    regulatorPublicKey);

                    CreditCredential senderReturnCreditCredential = creditCredentialResult.get(0);
                    CreditCredential receiverCreditCredential = creditCredentialResult.get(1);

                    DemoMain.printSplitCreditInfo(
                            storageClient,
                            publicKeyCrypto,
                            regulatorSecretKey,
                            senderReturnCreditCredential,
                            receiverCreditCredential);

                    // set sender data
                    senderWallet.creditCredentials.remove(splitCreditCredentials);
                    senderWallet.creditCredentials.add(senderReturnCreditCredential);
                    BigInteger senderSpentNumbericValue =
                            BigInteger.valueOf(
                                    splitCreditCredentials
                                            .getCreditSecret()
                                            .getCreditValue()
                                            .getNumericalValue());
                    senderWallet.numbericBalance.remove(senderSpentNumbericValue);
                    BigInteger senderReturNumbericValue =
                            BigInteger.valueOf(
                                    senderReturnCreditCredential
                                            .getCreditSecret()
                                            .getCreditValue()
                                            .getNumericalValue());
                    senderWallet.numbericBalance.add(senderReturNumbericValue);
                    senderWallet.numbericBalanceTotal =
                            senderWallet.numbericBalanceTotal.subtract(splitNumbericValue);
                    userWallets.put(senderName, senderWallet);
                    // set receiver data
                    receiverWallet.creditCredentials.add(receiverCreditCredential);
                    receiverWallet.numbericBalance.add(splitNumbericValue);
                    receiverWallet.numbericBalanceTotal =
                            receiverWallet.numbericBalanceTotal.add(splitNumbericValue);
                    userWallets.put(receiverName, receiverWallet);
                    System.out.println(
                            "\n"
                                    + senderName
                                    + " -"
                                    + longSplitValue
                                    + ", numbericBalance is "
                                    + senderWallet.numbericBalance
                                    + ".");
                    System.out.println(
                            "numbericBalanceTotal is " + senderWallet.numbericBalanceTotal + ".\n");
                    System.out.println(
                            receiverName
                                    + " +"
                                    + longSplitValue
                                    + ", numbericBalance is "
                                    + receiverWallet.numbericBalance
                                    + ".");
                    System.out.println(
                            "numbericBalanceTotal is "
                                    + receiverWallet.numbericBalanceTotal
                                    + ".\n");
                    break;
                case "queryCredit":
                    if (params.length < 2) {
                        System.out.println("Error: please provide 2 parameters.");
                        System.out.println("For example: queryCredit A\n");
                        break;
                    }
                    userName = params[1];
                    if (!userWallets.containsKey(userName)) {
                        System.out.println("Error: " + userName + " does not have any credit.\n");
                        break;
                    }
                    userWallet = userWallets.get(userName);
                    System.out.println("numbericBalance is " + userWallet.numbericBalance + ".");
                    System.out.println(
                            "numbericBalanceTotal is " + userWallet.numbericBalanceTotal + ".\n");
                    System.out.println("stringBalance is " + userWallet.stringBalance + ".\n");
                    break;
                case "help":
                    CommandUtils.help();
                    break;
                case "quit":
                case "q":
                case "exit":
                    System.exit(0);
                default:
                    System.out.println("Error: the command " + params[0] + " does not exist.\n");
                    break;
            }
        }
    }

    private static byte[] getMasterSecret() throws URISyntaxException, IOException, Exception {
        byte[] masterSecret;
        Path path = Paths.get(ClassLoader.getSystemResource(DemoMain.SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        masterSecret = Utils.decryptSecret(encryptedSecret, "example123");
        return masterSecret;
    }
}

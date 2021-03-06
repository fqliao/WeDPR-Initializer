package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;

public class DemoMain {

    public static String hiddenAssetTableName = "hidden_asset_example";
    public static String regulationInfoTableName = "hidden_asset_regulation_info_example";

    // NOTICE: Decrypts master secret from encrypted secret file for example.
    // This 2019_1024_06_17_26.secret file which is generated
    // by create_secret.sh and password is example123.
    public static final String SECRET_PATH = "2019_1024_06_17_26.secret";
    public static final String SECRET_PASSWORD = "example123";

    // NOTICE:The regulator secret key should be saved by regulator.
    // In the example, set the variable just used to decrypt regulation information for users.
    public static byte[] regulatorSecretKey;
    public static byte[] regulatorPublicKey;
    public static PublicKeyCrypto publicKeyCrypto;

    public enum TransferType {
        Numberic,
        NonNumberic
    }

    public static void main(String[] args) throws Exception {
        // (Optional) Regulator init keypair.
        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();
        regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        publicKeyCrypto = new PublicKeyCryptoExample();

        if (args.length == 1) {
            if ("transferNumbericAsset".equals(args[0])) {
                transferNumbericAsset();
            } else if ("transferNonnumericalAsset".equals(args[0])) {
                transferNonnumericalAsset();
            } else if ("splitNumbericAsset".equals(args[0])) {
                splitNumbericAsset();
            } else {
                System.out.println(
                        "Please provide one parameter, such as 'transferNumbericAsset' or 'transferNonnumericalAsset' or 'transferNonnumericalAsset'.");
                System.exit(-1);
            }
        } else {
            System.out.println(
                    "Please provide one parameter, such as 'transferNumbericAsset' or 'transferNonnumericalAsset' or 'transferNonnumericalAsset'.");
            System.exit(-1);
        }
        System.exit(0);
    }

    public static void transferNumbericAsset() throws Exception {
        // redeemer set value
        int value = 100;
        CreditValue creditValue = AssethidingUtils.makeCreditValue(value);
        doTransfer(TransferType.Numberic, creditValue);
    }

    public static void transferNonnumericalAsset() throws Exception {
        // redeemer set value
        String value = "a movie ticket";
        CreditValue creditValue = AssethidingUtils.makeCreditValue(value);
        doTransfer(TransferType.NonNumberic, creditValue);
    }

    private static void doTransfer(TransferType transferType, CreditValue creditValue)
            throws Exception {
        // Deploy contract and create hidden asset table.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AssethidingUtils.initContract(
                        ecKeyPair, groupID, hiddenAssetTableName, regulationInfoTableName);

        //////////////////////////////
        // 1 Issues credit credential.
        //////////////////////////////

        // 1.1 Owner settings.
        byte[] masterSecret = AssethidingUtils.getMasterSecret(SECRET_PATH, SECRET_PASSWORD);

        // 1.2 Redeemer settings.
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();

        ///////////////////////////////////////////////////////////////////////
        // 1.3 Issues credit credential by running issueCredit example program.
        ///////////////////////////////////////////////////////////////////////
        CreditCredential creditCredential =
                IssueCreditExample.issueCredit(
                        transferType,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);
        // 1.4 (Optional) Queries regulation information for example.
        AssethidingUtils.queryIssueCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

        //////////////////////////////////
        // 2 Transfers credit credential.
        //////////////////////////////////

        // 2.1 Sender makes state, sets creditCredential in state.
        OwnerState senderOwnerState =
                AssethidingUtils.makeSenderOwnerStateForTransfer(creditCredential);
        // 2.2 Receiver makes state.
        // Receiver gets master secret by getSecret api in Utils for example.
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.makeReceiverOwnerState(receiverMasterSecret);
        // 2.3 Sender makes transaction information.
        TransactionInfo transactionInfo =
                AssethidingUtils.makeTransactionInfo(creditValue, "transfer credit");

        ///////////////////////////////////////////////////////////////////////
        // 2.4 Transfers credit Credential by running transfer example program.
        ///////////////////////////////////////////////////////////////////////
        CreditCredential receiverCreditCredential =
                TransferCreditExample.transferCredit(
                        transferType,
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageClient,
                        regulatorPublicKey);
        // 2.5 Receiver saves creditCredential.
        String recevierEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        receiverCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Receiver saves the currentCredit:" + recevierEncodedCurrentCredit);
        System.out.println(
                "Receiver saves the creditSecret:" + receiverCreditCredential.getCreditSecret());
        System.out.println(
                "Sender transfers "
                        + receiverCreditCredential.getCreditSecret().getCreditValue()
                        + " to receiver susscessful!");

        // 2.6 (Optional) Queries regulation information for example.
        AssethidingUtils.queryTransferCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, receiverCreditCredential);

        ////////////////////////////////
        // 3 Fulfills credit credential.
        ////////////////////////////////
        FulfillCreditExample.fulfillCredit(
                transferType, redeemerKeyPair, receiverCreditCredential, storageClient);
    }

    public static void splitNumbericAsset() throws Exception {
        // Deploy contract and create hidden asset table.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AssethidingUtils.initContract(
                        ecKeyPair, groupID, hiddenAssetTableName, regulationInfoTableName);

        //////////////////////////////
        // 1 Issues credit credential.
        //////////////////////////////
        // 1.1 Owner settings.
        byte[] masterSecret = AssethidingUtils.getMasterSecret(SECRET_PATH, SECRET_PASSWORD);

        // 1.2 Redeemer settings.
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();

        ///////////////////////////////////////////////////////////////////////
        // 1.3 Issues credit credential by running issueCredit example program.
        ///////////////////////////////////////////////////////////////////////
        int value = 100;
        CreditValue creditValue = AssethidingUtils.makeCreditValue(value);
        CreditCredential creditCredential =
                IssueCreditExample.issueCredit(
                        TransferType.Numberic,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);
        // 1.4 (Optional) Queries regulation information for example.
        AssethidingUtils.queryIssueCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

        // 2.1 Sender makes state, sets creditCredential in state.
        OwnerState senderOwnerState =
                AssethidingUtils.getSenderOwnerStateForSplit(masterSecret, creditCredential);
        // 2.2 Receiver makes state.
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.makeReceiverOwnerState(receiverMasterSecret);
        // 2.3 Sender makes transaction information, splits 60 from sender to receiver, and 40 to
        // sender.
        TransactionInfo transactionInfo =
                AssethidingUtils.makeTransactionInfo(
                        AssethidingUtils.makeCreditValue(60), "split credit");

        ////////////////////////////////////////////////////////////////////
        // 2.4 Splits credit Credential by running transfer example program.
        ////////////////////////////////////////////////////////////////////
        List<CreditCredential> creditCredentialResult =
                SplitCreditExample.splitCredit(
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageClient,
                        regulatorPublicKey);
        // 2.5 Sender and receiver save creditCredential.
        CreditCredential senderCreditCredential = creditCredentialResult.get(0);
        CreditCredential receiverCreditCredential = creditCredentialResult.get(1);
        String senderEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        senderCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Sender save the currentCredit:" + senderEncodedCurrentCredit);
        System.out.println(
                "Sender save the creditSecret:" + senderCreditCredential.getCreditSecret());

        String receiverEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        receiverCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Receiver save the currentCredit:" + receiverEncodedCurrentCredit);
        System.out.println(
                "Receiver save the creditSecret:" + receiverCreditCredential.getCreditSecret());

        System.out.println(
                "Sender transfers "
                        + senderCreditCredential.getCreditSecret().getCreditValue()
                        + " to receiver and "
                        + receiverCreditCredential.getCreditSecret().getCreditValue()
                        + " to itself susscessful!");

        // 2.6 (Optional) Queries regulation information for example.
        AssethidingUtils.querySplitCreditRegulationInfo(
                storageClient,
                publicKeyCrypto,
                regulatorSecretKey,
                senderCreditCredential,
                receiverCreditCredential);

        ////////////////////////////////
        // 3 Fulfills credit credential.
        ////////////////////////////////
        FulfillCreditExample.fulfillCredit(
                TransferType.Numberic, redeemerKeyPair, senderCreditCredential, storageClient);
        FulfillCreditExample.fulfillCredit(
                TransferType.Numberic, redeemerKeyPair, receiverCreditCredential, storageClient);
    }
}

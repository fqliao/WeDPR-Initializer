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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;

public class DemoMain {

    public static String hiddenAssetTableName = "hidden_asset_example";
    public static String regulationInfoTableName = "hidden_asset_regulation_info_example";

    public static final String SECRET_PATH = "2019_1024_06_17_26.secret";

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
        CreditValue creditValue = CreditValue.newBuilder().setNumericalValue(value).build();
        doTransfer(TransferType.Numberic, creditValue);
    }

    public static void transferNonnumericalAsset() throws Exception {
        // redeemer set value
        String value = "a movie ticket";
        CreditValue creditValue = CreditValue.newBuilder().setStringValue(value).build();
        doTransfer(TransferType.NonNumberic, creditValue);
    }

    private static void doTransfer(TransferType transferType, CreditValue creditValue)
            throws Exception {
        // Deploy contract and create hidden asset table
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AssethidingUtils.initContract(
                        ecKeyPair, groupID, hiddenAssetTableName, regulationInfoTableName);

        /// 1 issue credit
        // owner init parameters
        // NOTICE: Decrypts secret from encrypted secret file and password for example.
        // This use 2019_1024_06_17_26.secret file which is generated
        // by create_secret.sh and password set example123.
        Path path = Paths.get(ClassLoader.getSystemResource(SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        byte[] masterSecret = Utils.decryptSecret(encryptedSecret, "example123");

        // redeemer init parameters
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();

        // Get CreditCredential by running issueCredit example program.
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        transferType,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);

        AssethidingUtils.queryIssueCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

        /// 2 start transfer from sender to receiver
        // sender init OwnerState
        OwnerState senderOwnerState =
                AssethidingUtils.getSenderOwnerStateForTransfer(creditCredential);

        // receiver init OwnerState
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);

        // set TransactionInfo
        TransactionInfo transactionInfo =
                TransactionInfo.newBuilder()
                        .setCreditValue(creditValue)
                        .setTransactionMessage("transfer")
                        .build();

        // get receiver CreditCredential by running transfer example program
        CreditCredential receiverCreditCredential =
                TransferCreditExampleProtocol.transferCredit(
                        transferType,
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageClient,
                        regulatorPublicKey);

        String recevierEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        receiverCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Receiver save the currentCredit:" + recevierEncodedCurrentCredit);
        System.out.println(
                "Owner save the creditSecret:" + receiverCreditCredential.getCreditSecret());
        System.out.println(
                "Sender transfers "
                        + receiverCreditCredential.getCreditSecret().getCreditValue()
                        + " to receiver susscessful!");

        // (Optional) Queries regulation information for example.
        AssethidingUtils.queryTransferCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, receiverCreditCredential);

        /// 3 fulfill credit
        FulfillCreditExampleProtocol.fulfillCredit(
                transferType, redeemerKeyPair, receiverCreditCredential, storageClient);
    }

    public static void splitNumbericAsset() throws Exception {
        // Deploy contract and create hidden asset table
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        StorageExampleClient storageClient =
                AssethidingUtils.initContract(
                        ecKeyPair, groupID, hiddenAssetTableName, regulationInfoTableName);

        /// 1 issue credit
        // owner init parameters
        // NOTICE: Decrypts secret from encrypted secret file and password for example.
        // This use 2019_1024_06_17_26.secret file which is generated
        // by create_secret.sh and password set example123.
        Path path = Paths.get(ClassLoader.getSystemResource(SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        byte[] masterSecret = Utils.decryptSecret(encryptedSecret, "example123");

        // redeemer init parameters
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();
        // redeemer set value
        int value = 100;
        CreditValue creditValue = CreditValue.newBuilder().setNumericalValue(value).build();

        // get CreditCredential by running issueCredit example program
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        TransferType.Numberic,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);
        AssethidingUtils.queryIssueCreditRegulationInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

        /// 2 start split 60 from sender to receiver, and 40 to sender
        // sender init OwnerState
        OwnerState senderOwnerState =
                AssethidingUtils.getSenderOwnerStateForSplit(masterSecret, creditCredential);

        // receiver init OwnerState
        byte[] receiverMasterSecret = Utils.getSecret();
        OwnerState receiverOwnerState =
                AssethidingUtils.getReceiverOwnerState(receiverMasterSecret);

        // set TransactionInfo
        TransactionInfo transactionInfo =
                TransactionInfo.newBuilder()
                        .setCreditValue(CreditValue.newBuilder().setNumericalValue(60).build())
                        .setTransactionMessage("split")
                        .build();

        // get creditCredential result by running split example program
        List<CreditCredential> creditCredentialResult =
                SplitCreditExampleProtocol.splitCredit(
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageClient,
                        regulatorPublicKey);

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

        // (Optional) Queries regulation information for example.
        AssethidingUtils.querySplitCreditRegulationInfo(
                storageClient,
                publicKeyCrypto,
                regulatorSecretKey,
                senderCreditCredential,
                receiverCreditCredential);

        /// 3 fulfill credit
        FulfillCreditExampleProtocol.fulfillCredit(
                TransferType.Numberic, redeemerKeyPair, senderCreditCredential, storageClient);
        FulfillCreditExampleProtocol.fulfillCredit(
                TransferType.Numberic, redeemerKeyPair, receiverCreditCredential, storageClient);
    }
}

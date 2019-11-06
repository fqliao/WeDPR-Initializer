package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.RedeemerClient;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.tuples.generated.Tuple3;

public class DemoMain {

    public static String hiddenAssetTable = "hidden_asset_example";
    public static String regulationInfoTable = "hidden_asset_regulation_info";
    public static final String SECRET_PATH = "2019_1024_06_17_26.secret";

    // NOTICE:The regulator secret key should be saved by regulator.
    // In the example, set the variable just used to decrypt regulation information for users.
    public static byte[] regulatorSecretKey;

    public enum TransferType {
        Numberic,
        NonNumberic
    }

    public static void main(String[] args) throws Exception {
        // (Optional) Regulator init keypair.
        ECKeyPair regulatorKeyPair = Utils.getEcKeyPair();
        regulatorSecretKey = regulatorKeyPair.getPrivateKey().toByteArray();

        byte[] regulatorPublicKey = regulatorKeyPair.getPublicKey().toByteArray();
        if (args.length == 1) {
            if ("transferNumbericAsset".equals(args[0])) {
                transferNumbericAsset(regulatorPublicKey);
            }
            if ("splitNumbericAsset".equals(args[0])) {
                splitNumbericAsset(regulatorPublicKey);
            }
            if ("transferNonnumericalAsset".equals(args[0])) {
                transferNonnumericalAsset(regulatorPublicKey);
            }
        } else {
            // By default, the demo will run the following examples.
            transferNumbericAsset(regulatorPublicKey);
            splitNumbericAsset(regulatorPublicKey);
        }
        System.exit(0);
    }

    public static void transferNumbericAsset(byte[] regulatorPublicKey) throws Exception {
        // redeemer set value
        int value = 100;
        CreditValue creditValue = CreditValue.newBuilder().setNumericalValue(value).build();
        doTransfer(TransferType.Numberic, creditValue, regulatorPublicKey);
    }

    public static void transferNonnumericalAsset(byte[] regulatorPublicKey) throws Exception {
        // redeemer set value
        String value = "a movie ticket";
        CreditValue creditValue = CreditValue.newBuilder().setStringValue(value).build();
        doTransfer(TransferType.NonNumberic, creditValue, regulatorPublicKey);
    }

    private static void doTransfer(
            TransferType transferType, CreditValue creditValue, byte[] regulatorPublicKey)
            throws Exception {
        /// 1 issue credit
        // owner init parameters
        // NOTICE: Decrypts secret from encrypted secret file and password for example.
        // This use 2019_1024_06_17_26.secret file which is generated
        // by create_secret.sh and password set example123.
        Path path = Paths.get(ClassLoader.getSystemResource(SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        byte[] masterSecret = Utils.decryptSecret(encryptedSecret, "example123");

        // redeemer init parameters
        RedeemerClient redeemerClient = new RedeemerClient();
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();

        // Blockchain init parameters.
        // Generate ECKeyPair to send transactions to blockchain.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        // For demo purpose, we call deployContract first, then call loadContract to load the
        // deployed contract.
        // If your contract has been deployed, you only need to call loadContract. Otherwise, you
        // only need to call deployContract.
        HiddenAssetExample hiddenAssetExample = AssethidingUtils.deployContract(ecKeyPair, groupID);
        hiddenAssetExample =
                AssethidingUtils.loadContract(
                        hiddenAssetExample.getContractAddress(), ecKeyPair, groupID);
        StorageExampleClient storageExampleClient =
                new StorageExampleClient(hiddenAssetExample, hiddenAssetTable, regulationInfoTable);

        // Get CreditCredential by running issueCredit example program.
        OwnerClient ownerClient = new OwnerClient();
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        transferType,
                        redeemerClient,
                        redeemerKeyPair,
                        creditValue,
                        storageExampleClient,
                        ownerClient,
                        masterSecret,
                        regulatorPublicKey);

        String encodedCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("owner save the currentCredit:" + encodedCurrentCredit);
        System.out.println("owner save the CreditSecret:" + creditCredential.getCreditSecret());
        System.out.println("owner issue credit " + creditValue + " from redeemer susscessful!");

        // (Optional) Queries regulation information for example.
        Tuple3<List<String>, List<String>, List<String>> RegulationInfos =
                storageExampleClient.queryRegulationInfo(encodedCurrentCredit);
        if (RegulationInfos.getValue3().isEmpty()) {
            throw new WedprException(
                    "Queries regulation information by currentCredit: "
                            + encodedCurrentCredit
                            + ", failed.");
        }
        byte[] encrypedRegulationInfo = Utils.stringToBytes(RegulationInfos.getValue3().get(0));
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        byte[] decryptRegulationInfo =
                publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        RegulationInfo regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the regulation information about issuing credit is:\n"
                        + regulationInfo);

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
        CreditCredential creditCredentialForReceiver =
                TransferCreditExampleProtocol.transferCredit(
                        transferType,
                        ownerClient,
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageExampleClient,
                        regulatorPublicKey);

        String encodedCurrentCreditForRecevier =
                Utils.protoToEncodedString(
                        creditCredentialForReceiver.getCreditStorage().getCurrentCredit());
        System.out.println("receiver save the currentCredit:" + encodedCurrentCreditForRecevier);
        System.out.println(
                "owner save the CreditSecret:" + creditCredentialForReceiver.getCreditSecret());
        System.out.println(
                "Sender transfers "
                        + creditCredentialForReceiver.getCreditSecret().getCreditValue()
                        + " to receiver susscessful!");

        // (Optional) Queries regulation information for example.
        RegulationInfos = storageExampleClient.queryRegulationInfo(encodedCurrentCreditForRecevier);
        if (RegulationInfos.getValue3().isEmpty()) {
            throw new WedprException(
                    "Queries regulation information by currentCredit: "
                            + encodedCurrentCreditForRecevier
                            + ", failed.");
        }
        encrypedRegulationInfo = Utils.stringToBytes(RegulationInfos.getValue3().get(0));
        decryptRegulationInfo = publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the regulation information about transferring credit is:\n"
                        + regulationInfo);

        /// 3 fulfill credit
        FulfillCreditExampleProtocol.fulfillCredit(
                transferType,
                redeemerClient,
                redeemerKeyPair,
                creditCredentialForReceiver,
                storageExampleClient);
    }

    public static void splitNumbericAsset(byte[] regulatorPublicKey) throws Exception {
        /// 1 issue credit
        // owner init parameters
        // NOTICE: Decrypts secret from encrypted secret file and password for example.
        // This use 2019_1024_06_17_26.secret file which is generated
        // by create_secret.sh and password set example123.
        Path path = Paths.get(ClassLoader.getSystemResource(SECRET_PATH).toURI());
        String encryptedSecret = new String(Files.readAllBytes(path));
        byte[] masterSecret = Utils.decryptSecret(encryptedSecret, "example123");

        // redeemer init parameters
        RedeemerClient redeemerClient = new RedeemerClient();
        EncodedKeyPair redeemerKeyPair = Utils.getEncodedKeyPair();
        // redeemer set value
        int value = 100;
        CreditValue creditValue = CreditValue.newBuilder().setNumericalValue(value).build();

        // blockchain init parameters
        // generate ECKeyPair to send transactions to blockchain
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        HiddenAssetExample hiddenAssetExample = AssethidingUtils.deployContract(ecKeyPair, groupID);
        StorageExampleClient storageExampleClient =
                new StorageExampleClient(hiddenAssetExample, hiddenAssetTable, regulationInfoTable);

        // get CreditCredential by running issueCredit example program
        OwnerClient ownerClient = new OwnerClient();
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        TransferType.Numberic,
                        redeemerClient,
                        redeemerKeyPair,
                        creditValue,
                        storageExampleClient,
                        ownerClient,
                        masterSecret,
                        regulatorPublicKey);
        String encodedCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("owner save the currentCredit:" + encodedCurrentCredit);
        System.out.println("owner save the CreditSecret:" + creditCredential.getCreditSecret());
        System.out.println(
                "owner issue credit "
                        + creditCredential.getCreditSecret().getCreditValue()
                        + " from redeemer susscessful!");

        // (Optional) Queries regulation information for example.
        Tuple3<List<String>, List<String>, List<String>> regulationInfos =
                storageExampleClient.queryRegulationInfo(encodedCurrentCredit);
        if (regulationInfos.getValue3().isEmpty()) {
            throw new WedprException(
                    "Queries regulation information by currentCredit: "
                            + encodedCurrentCredit
                            + ", failed.");
        }
        byte[] encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        byte[] decryptRegulationInfo =
                publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        RegulationInfo regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the regulation information about issuing credit is:\n"
                        + regulationInfo);

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
                        ownerClient,
                        senderOwnerState,
                        receiverOwnerState,
                        transactionInfo,
                        storageExampleClient,
                        regulatorPublicKey);

        CreditCredential creditCredentialSender = creditCredentialResult.get(0);
        CreditCredential creditCredentialReceiver = creditCredentialResult.get(1);
        String encodedCurrentCreditSender =
                Utils.protoToEncodedString(
                        creditCredentialSender.getCreditStorage().getCurrentCredit());
        System.out.println("sender save the currentCredit:" + encodedCurrentCreditSender);
        System.out.println(
                "sender save the CreditSecret:" + creditCredentialSender.getCreditSecret());

        String encodedCurrentCreditReceiver =
                Utils.protoToEncodedString(
                        creditCredentialReceiver.getCreditStorage().getCurrentCredit());
        System.out.println("receiver save the currentCredit:" + encodedCurrentCreditReceiver);
        System.out.println(
                "receiver save the CreditSecret:" + creditCredentialReceiver.getCreditSecret());

        System.out.println(
                "Sender transfers "
                        + creditCredentialSender.getCreditSecret().getCreditValue()
                        + " to receiver and "
                        + creditCredentialReceiver.getCreditSecret().getCreditValue()
                        + " to itself susscessful!");

        // (Optional) Queries regulation information for example.
        regulationInfos = storageExampleClient.queryRegulationInfo(encodedCurrentCreditSender);
        if (regulationInfos.getValue3().isEmpty()) {
            throw new WedprException(
                    "Queries regulation information by currentCredit: "
                            + encodedCurrentCreditSender
                            + ", failed.");
        }
        encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));
        decryptRegulationInfo = publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the sender regulation information about spliting credit is:\n"
                        + regulationInfo);

        regulationInfos = storageExampleClient.queryRegulationInfo(encodedCurrentCreditReceiver);
        if (regulationInfos.getValue3().isEmpty()) {
            throw new WedprException(
                    "Queries regulation information by currentCredit: "
                            + encodedCurrentCreditReceiver
                            + ", failed.");
        }
        encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));
        decryptRegulationInfo = publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "Decrypted the receiver regulation information about spliting credit is:\n"
                        + regulationInfo);

        /// 3 fulfill credit
        FulfillCreditExampleProtocol.fulfillCredit(
                TransferType.Numberic,
                redeemerClient,
                redeemerKeyPair,
                creditCredentialSender,
                storageExampleClient);
        FulfillCreditExampleProtocol.fulfillCredit(
                TransferType.Numberic,
                redeemerClient,
                redeemerKeyPair,
                creditCredentialReceiver,
                storageExampleClient);
    }
}

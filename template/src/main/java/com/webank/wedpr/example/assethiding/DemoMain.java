package com.webank.wedpr.example.assethiding;

import com.google.protobuf.InvalidProtocolBufferException;
import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.OwnerState;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.assethiding.proto.TransactionInfo;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.fisco.bcos.web3j.protocol.Web3j;
import org.fisco.bcos.web3j.protocol.channel.ChannelEthereumService;
import org.fisco.bcos.web3j.tuples.generated.Tuple3;
import org.springframework.context.support.ClassPathXmlApplicationContext;

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
            }
            if ("splitNumbericAsset".equals(args[0])) {
                splitNumbericAsset();
            }
            if ("transferNonnumericalAsset".equals(args[0])) {
                transferNonnumericalAsset();
            }
        } else {
            // By default, the demo will run the following examples.
            transferNumbericAsset();
            splitNumbericAsset();
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

        // Blockchain init parameters.
        StorageExampleClient storageClient = initBlockchain();
        System.out.println("hiddenAssetTableName:" + DemoMain.hiddenAssetTableName);
        System.out.println("regulationInfoTableName:" + DemoMain.regulationInfoTableName);

        // Get CreditCredential by running issueCredit example program.
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        transferType,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);

        printIssueCreditInfo(storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

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
        printTransferCreditInfo(
                storageClient, publicKeyCrypto, regulatorSecretKey, receiverCreditCredential);

        /// 3 fulfill credit
        FulfillCreditExampleProtocol.fulfillCredit(
                transferType, redeemerKeyPair, receiverCreditCredential, storageClient);
    }

    public static void splitNumbericAsset() throws Exception {
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

        // blockchain init parameters
        // Blockchain init parameters.
        StorageExampleClient storageClient = initBlockchain();
        System.out.println("hiddenAssetTableName:" + DemoMain.hiddenAssetTableName);
        System.out.println("regulationInfoTableName:" + DemoMain.regulationInfoTableName);

        // get CreditCredential by running issueCredit example program
        CreditCredential creditCredential =
                IssueCreditExampleProtocol.issueCredit(
                        TransferType.Numberic,
                        redeemerKeyPair,
                        creditValue,
                        storageClient,
                        masterSecret,
                        regulatorPublicKey);
        printIssueCreditInfo(storageClient, publicKeyCrypto, regulatorSecretKey, creditCredential);

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
        printSplitCreditInfo(
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

    public static StorageExampleClient initBlockchain() throws Exception {
        // Generate ECKeyPair to send transactions to blockchain.
        ECKeyPair ecKeyPair = Utils.getEcKeyPair();
        int groupID = 1;
        Web3j web3j = getWeb3j(groupID);
        // For demo purpose, we call deployContract first, then call loadContract to load the
        // deployed contract.
        // If your contract has been deployed, you only need to call loadContract. Otherwise, you
        // only need to call deployContract.
        HiddenAssetExample hiddenAssetExample = AssethidingUtils.deployContract(web3j, ecKeyPair);
        hiddenAssetExample =
                AssethidingUtils.loadContract(
                        web3j, hiddenAssetExample.getContractAddress(), ecKeyPair);
        StorageExampleClient storageClient =
                new StorageExampleClient(
                        hiddenAssetExample, hiddenAssetTableName, regulationInfoTableName);
        // Create table `hidden_asset_example` and `hidden_asset_regulation_info_example`.
        storageClient.init();

        return storageClient;
    }

    public static Web3j getWeb3j(int groupID) throws Exception {
        ClassPathXmlApplicationContext context = null;
        try {
            context = new ClassPathXmlApplicationContext("classpath:applicationContext.xml");
            Service service = context.getBean(Service.class);
            service.setGroupId(groupID);
            service.run();

            ChannelEthereumService channelEthereumService = new ChannelEthereumService();
            channelEthereumService.setChannelService(service);
            return Web3j.build(channelEthereumService, groupID);
        } catch (Exception e) {
            throw new WedprException(e);
        } finally {
            context.close();
        }
    }

    public static void printIssueCreditInfo(
            StorageExampleClient storageClient,
            PublicKeyCrypto publicKeyCrypto,
            byte[] regulatorSecretKey,
            CreditCredential creditCredential)
            throws Exception, WedprException, InvalidProtocolBufferException {
        String encodedCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("owner save the currentCredit:" + encodedCurrentCredit);
        System.out.println("owner save the creditSecret:" + creditCredential.getCreditSecret());

        // (Optional) Queries regulation information for example.
        Tuple3<List<String>, List<String>, List<String>> RegulationInfos =
                storageClient.queryRegulationInfo(encodedCurrentCredit);
        byte[] encrypedRegulationInfo = Utils.stringToBytes(RegulationInfos.getValue3().get(0));
        byte[] decryptRegulationInfo =
                publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        RegulationInfo regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the regulation information about issuing credit is:\n"
                        + regulationInfo);
    }

    public static void printTransferCreditInfo(
            StorageExampleClient storageClient,
            PublicKeyCrypto publicKeyCrypto,
            byte[] regulatorSecretKey,
            CreditCredential receiverCreditCredential)
            throws Exception {
        String recevierEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        receiverCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Receiver save the currentCredit:" + recevierEncodedCurrentCredit);
        System.out.println(
                "Receiver save the creditSecret:" + receiverCreditCredential.getCreditSecret());
        System.out.println(
                "Sender transfers "
                        + receiverCreditCredential.getCreditSecret().getCreditValue()
                        + " to receiver susscessful!");

        // (Optional) Queries regulation information for example.
        Tuple3<List<String>, List<String>, List<String>> regulationInfos =
                storageClient.queryRegulationInfo(recevierEncodedCurrentCredit);
        byte[] encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));
        byte[] decryptRegulationInfo =
                publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        RegulationInfo regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the regulation information about transferring credit is:\n"
                        + regulationInfo);
    }

    public static void printSplitCreditInfo(
            StorageExampleClient storageClient,
            PublicKeyCrypto publicKeyCrypto,
            byte[] regulatorSecretKey,
            CreditCredential senderReturnCreditCredential,
            CreditCredential receiverCreditCredential)
            throws Exception {
        String senderEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        senderReturnCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Sender save the currentCredit:" + senderEncodedCurrentCredit);
        System.out.println(
                "Sender save the creditSecret:" + senderReturnCreditCredential.getCreditSecret());

        String receiverEncodedCurrentCredit =
                Utils.protoToEncodedString(
                        receiverCreditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Receiver save the currentCredit:" + receiverEncodedCurrentCredit);
        System.out.println(
                "Receiver save the creditSecret:" + receiverCreditCredential.getCreditSecret());
        System.out.println(
                "Sender transfers "
                        + senderReturnCreditCredential.getCreditSecret().getCreditValue()
                        + " to receiver and "
                        + receiverCreditCredential.getCreditSecret().getCreditValue()
                        + " to itself susscessful!");

        // (Optional) Queries regulation information for example.
        Tuple3<List<String>, List<String>, List<String>> regulationInfos =
                storageClient.queryRegulationInfo(senderEncodedCurrentCredit);
        byte[] encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));

        byte[] decryptRegulationInfo =
                publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        RegulationInfo regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "\nDecrypted the sender regulation information about spliting credit is:\n"
                        + regulationInfo);

        regulationInfos = storageClient.queryRegulationInfo(receiverEncodedCurrentCredit);
        encrypedRegulationInfo = Utils.stringToBytes(regulationInfos.getValue3().get(0));
        decryptRegulationInfo = publicKeyCrypto.decrypt(regulatorSecretKey, encrypedRegulationInfo);
        regulationInfo = RegulationInfo.parseFrom(decryptRegulationInfo);
        System.out.println(
                "Decrypted the receiver regulation information about spliting credit is:\n"
                        + regulationInfo);
    }
}

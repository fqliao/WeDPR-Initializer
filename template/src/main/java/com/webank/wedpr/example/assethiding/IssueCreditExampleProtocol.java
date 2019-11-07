package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.IssueResult;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.RedeemerClient;
import com.webank.wedpr.assethiding.RedeemerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.assethiding.proto.IssueArgument;
import com.webank.wedpr.assethiding.proto.RegulationInfo;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.assethiding.DemoMain.TransferType;

public class IssueCreditExampleProtocol {

    public static CreditCredential issueCredit(
            TransferType transferType,
            RedeemerClient redeemerClient,
            EncodedKeyPair redeemerKeyPair,
            CreditValue creditValue,
            StorageExampleClient storageClient,
            OwnerClient ownerClient,
            byte[] masterSecret,
            byte[] regulatorPublicKey)
            throws Exception {
        // 1 issue credit
        // 1.1 owner: issue credit
        String secretKey = Utils.getSecretKey(masterSecret).getSecretKey();
        IssueResult issueResult = ownerClient.issueCredit(secretKey);
        if (Utils.hasWedprError(issueResult)) {
            throw new WedprException(issueResult.wedprErrorMessage);
        }
        System.out.println("Owner send issue credit request successful!");

        // 1.2 redeemer: confirm credit
        RedeemerResult redeemerResult = null;
        if (transferType == TransferType.Numberic) {
            redeemerResult =
                    redeemerClient.confirmNumericalCredit(
                            redeemerKeyPair, issueResult, creditValue);
        } else {
            redeemerResult =
                    redeemerClient.confirmNonnumericalCredit(
                            redeemerKeyPair, issueResult, creditValue);
        }

        if (Utils.hasWedprError(redeemerResult)) {
            throw new WedprException(redeemerResult.wedprErrorMessage);
        }
        System.out.println("Redeemer confirm credit successful!");

        // 1.3 blockchain: verfiy issue credit
        // Create table `hidden_asset_example` and `hidden_asset_regulation_info`.
        storageClient.init();

        // verify issueCredit and save credit on blockchain
        storageClient.issueCredit(redeemerResult.issueArgument);
        System.out.println("\nBlockchain verify issue credit successful!");

        // Owner assemble the issued CreditCredential
        CreditCredential creditCredential =
                ownerClient.makeCreditCredential(redeemerResult, secretKey);

        // (Optional) Upload regulation information to blockchain.
        String regulationCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        String regulationSpentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getRootCredit());
        IssueArgument issueArgument =
                IssueArgument.parseFrom(Utils.stringToBytes(issueResult.issueArgument));
        String regulationRG = issueArgument.getRG();
        byte[] regulationInfo =
                RegulationInfo.newBuilder()
                        .setNumericalValue(creditValue.getNumericalValue())
                        .setStringValue(creditValue.getStringValue())
                        .setBlindingRG(regulationRG)
                        .build()
                        .toByteArray();

        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String encryptedregulationInfo =
                Utils.bytesToString(publicKeyCrypto.encrypt(regulatorPublicKey, regulationInfo));
        storageClient.insertRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedregulationInfo);

        return creditCredential;
    }
}

package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
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
            EncodedKeyPair redeemerKeyPair,
            CreditValue creditValue,
            StorageExampleClient storageClient,
            byte[] masterSecret,
            byte[] regulatorPublicKey)
            throws Exception {
        // 1 issue credit
        // 1.1 owner: issue credit
        String secretKey = Utils.getSecretKey(masterSecret).getSecretKey();
        OwnerResult ownerResult = OwnerClient.issueCredit(secretKey);
        if (Utils.hasWedprError(ownerResult)) {
            throw new WedprException(ownerResult.wedprErrorMessage);
        }
        System.out.println("Owner send issue credit request successful!");

        // 1.2 redeemer: confirm credit
        RedeemerResult redeemerResult = null;
        if (transferType == TransferType.Numberic) {
            redeemerResult =
                    RedeemerClient.confirmNumericalCredit(
                            redeemerKeyPair, ownerResult, creditValue);
        } else {
            redeemerResult =
                    RedeemerClient.confirmNonnumericalCredit(
                            redeemerKeyPair, ownerResult, creditValue);
        }

        if (Utils.hasWedprError(redeemerResult)) {
            throw new WedprException(redeemerResult.wedprErrorMessage);
        }
        System.out.println("Redeemer confirm credit successful!");

        // 1.3 blockchain: verfiy issue credit
        // verify issueCredit and save credit on blockchain
        storageClient.issueCredit(redeemerResult.issueArgument);
        System.out.println("\nBlockchain verify issue credit successful!");

        // Owner assemble the issued CreditCredential
        CreditCredential creditCredential =
                OwnerClient.makeCreditCredential(redeemerResult, secretKey);

        // (Optional) Upload regulation information to blockchain.
        String regulationCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        String regulationSpentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getRootCredit());
        IssueArgument issueArgument =
                IssueArgument.parseFrom(Utils.stringToBytes(ownerResult.issueArgument));
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

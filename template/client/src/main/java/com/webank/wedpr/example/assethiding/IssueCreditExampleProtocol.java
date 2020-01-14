package com.webank.wedpr.example.assethiding;

import com.webank.wedpr.assethiding.AssethidingUtils;
import com.webank.wedpr.assethiding.OwnerClient;
import com.webank.wedpr.assethiding.OwnerResult;
import com.webank.wedpr.assethiding.RedeemerClient;
import com.webank.wedpr.assethiding.RedeemerResult;
import com.webank.wedpr.assethiding.proto.CreditCredential;
import com.webank.wedpr.assethiding.proto.CreditValue;
import com.webank.wedpr.common.EncodedKeyPair;
import com.webank.wedpr.common.PublicKeyCrypto;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
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
        System.out.println("Redeemer confirm credit successful!");

        // 1.3 blockchain: verfiy issue credit
        // verify issueCredit and save credit on blockchain
        storageClient.issueCredit(redeemerResult.issueArgument);
        System.out.println("\nBlockchain verify issue credit successful!");

        // Owner assemble the issued CreditCredential
        CreditCredential creditCredential =
                OwnerClient.makeCreditCredential(redeemerResult, secretKey);

        // (Optional) Upload regulation information to blockchain.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String regulationCurrentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredential);
        String regulationSpentCredit =
                AssethidingUtils.makeRegulationIssueSpentCredit(creditCredential);
        String regulationBlindingRG =
                AssethidingUtils.makeIssueBlindingRG(ownerResult.issueArgument);
        String encryptedregulationInfo =
                AssethidingUtils.makeIssueRegulationInfo(
                        publicKeyCrypto, regulatorPublicKey, creditValue, regulationBlindingRG);
        storageClient.insertRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedregulationInfo);

        return creditCredential;
    }
}

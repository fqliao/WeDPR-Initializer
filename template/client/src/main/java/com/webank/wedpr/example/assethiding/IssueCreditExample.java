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

public class IssueCreditExample {

    public static CreditCredential issueCredit(
            TransferType transferType,
            EncodedKeyPair redeemerKeyPair,
            CreditValue creditValue,
            StorageExampleClient storageClient,
            byte[] masterSecret,
            byte[] regulatorPublicKey)
            throws Exception {

        //////////////////////////////////////
        // 1 Owner sends issue credit request.
        //////////////////////////////////////

        // 1.1 Owner gets secretKey for masterSecret.
        String secretKey = Utils.getSecretKey(masterSecret).getSecretKey();
        // 1.2 Owner sends issue credit request.
        OwnerResult ownerResult = OwnerClient.issueCredit(secretKey);
        System.out.println("Owner send issue credit request successful!");

        ///////////////////////////////////////////
        // 2 Redeemer confirms issue credit request.
        ///////////////////////////////////////////

        RedeemerResult redeemerResult = null;
        String issueArgument = ownerResult.issueArgument;
        // 2.1 Redeemer confirms issue credit request.
        if (transferType == TransferType.Numberic) {
            redeemerResult =
                    RedeemerClient.confirmNumericalCredit(
                            redeemerKeyPair, issueArgument, creditValue);
        } else {
            redeemerResult =
                    RedeemerClient.confirmNonnumericalCredit(
                            redeemerKeyPair, issueArgument, creditValue);
        }
        System.out.println("Redeemer confirm credit successful!");
        // 2.2 Redeemer verifies issue credit and then uploading credit credential on blockchain if
        // the
        // request passed the validation.
        storageClient.issueCredit(redeemerResult.issueArgument);
        System.out.println("\nBlockchain verify issue credit successful!");

        ////////////////////////////////////////////
        // 3 Owner obtains the issued CreditCredential.
        ////////////////////////////////////////////
        // 3.1 Owner obtains the issued CreditCredential.
        CreditCredential creditCredential =
                AssethidingUtils.makeCreditCredential(redeemerResult.creditCredential, secretKey);
        String encodedCurrentCredit =
                Utils.protoToEncodedString(creditCredential.getCreditStorage().getCurrentCredit());
        System.out.println("Owner saves the currentCredit:" + encodedCurrentCredit);
        System.out.println("Owner saves the creditSecret:" + creditCredential.getCreditSecret());

        // 3.2 (Optional) Owner uploads regulation information to blockchain.
        PublicKeyCrypto publicKeyCrypto = new PublicKeyCryptoExample();
        String regulationCurrentCredit =
                AssethidingUtils.makeRegulationCurrentCredit(creditCredential);
        String regulationSpentCredit =
                AssethidingUtils.makeRegulationIssueSpentCredit(creditCredential);
        String regulationBlindingRG = AssethidingUtils.makeIssueBlindingRG(issueArgument);
        String encryptedregulationInfo =
                AssethidingUtils.makeIssueRegulationInfo(
                        publicKeyCrypto, regulatorPublicKey, creditValue, regulationBlindingRG);
        storageClient.uploadRegulationInfo(
                regulationCurrentCredit, regulationSpentCredit, encryptedregulationInfo);

        return creditCredential;
    }
}

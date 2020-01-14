package com.webank.wedpr.example.selectivedisclosure;

import com.google.protobuf.InvalidProtocolBufferException;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.selectivedisclosure.CredentialTemplateStorage;
import com.webank.wedpr.selectivedisclosure.IssuerClient;
import com.webank.wedpr.selectivedisclosure.IssuerResult;
import com.webank.wedpr.selectivedisclosure.PredicateType;
import com.webank.wedpr.selectivedisclosure.SelectivedisclosureUtils;
import com.webank.wedpr.selectivedisclosure.UserClient;
import com.webank.wedpr.selectivedisclosure.UserResult;
import com.webank.wedpr.selectivedisclosure.VerifierClient;
import com.webank.wedpr.selectivedisclosure.VerifierResult;
import com.webank.wedpr.selectivedisclosure.proto.Predicate;
import com.webank.wedpr.selectivedisclosure.proto.RevealedAttributeInfo;
import com.webank.wedpr.selectivedisclosure.proto.StringToStringPair;
import com.webank.wedpr.selectivedisclosure.proto.VerificationRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DemoMain {

    public static String userId = "CnEDk9HrMnmiHXEV1WFgbVCRteYnPqsJwrTdcZaNhFVW";
    public static List<String> attributeList = Arrays.asList("name", "age", "balance");
    public static Map<String, String> credentialInfoMap = new HashMap<>();
    public static List<String> revealedAttributeList = Arrays.asList("name");
    public static List<Predicate> predicateList = new ArrayList<>();

    static {
        // NOTICE:
        // 1. Revealed attribute value belong decimal String.
        // 2. Predicate attribute value belong int
        credentialInfoMap.put(
                "name",
                "5944657099558967239210949258394887428692050081607692519917050011144233115103");
        credentialInfoMap.put("age", "20");
        credentialInfoMap.put("balance", "10000");

        // Predicate support: LT LE GT GE EQ
        Predicate predicate1 = Utils.makePredicate("age", PredicateType.GE, 20);
        Predicate predicate2 = Utils.makePredicate("balance", PredicateType.GE, 10000);
        Predicate predicate3 = Utils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
    }

    public static void main(String[] args) throws WedprException, InvalidProtocolBufferException {
        run();
    }

    public static void run() throws WedprException, InvalidProtocolBufferException {
        // 1 Issuer make credential template.
        IssuerResult issuerResult = IssuerClient.makeCredentialTemplate(attributeList);
        System.out.println("attributeList:" + attributeList);
        // Issuer save the templateSecretKey
        String templateSecretKey = issuerResult.templateSecretKey;
        CredentialTemplateStorage credentialTemplateStorage =
                issuerResult.credentialTemplateStorage;

        // 2 User make credential.
        UserResult userResult =
                UserClient.makeCredential(credentialInfoMap, credentialTemplateStorage);
        System.out.println("credentialInfoMap:" + credentialInfoMap);
        String credentialSignatureRequest = userResult.credentialSignatureRequest;
        // masterSecret is saved by User
        String masterSecret = userResult.masterSecret;
        String credentialSecretsBlindingFactors = userResult.credentialSecretsBlindingFactors;
        String userNonce = userResult.userNonce;

        // 3 Issuer sign credential.
        issuerResult =
                IssuerClient.signCredential(
                        credentialTemplateStorage,
                        templateSecretKey,
                        credentialSignatureRequest,
                        userId,
                        userNonce);
        String credentialSignature = issuerResult.credentialSignature;
        String issuerNonce = issuerResult.issuerNonce;

        // 4 User blind credential signature.
        userResult =
                UserClient.blindCredentialSignature(
                        credentialSignature,
                        credentialInfoMap,
                        credentialTemplateStorage,
                        masterSecret,
                        credentialSecretsBlindingFactors,
                        issuerNonce);
        // newCredentialSignature is saved by User.
        String blindedCredentialSignature = userResult.credentialSignature;
        // 5 User set VerificationRule.
        VerificationRule verificationRule =
                SelectivedisclosureUtils.makeVerificationRule(revealedAttributeList, predicateList);
        System.out.println("verificationRule:" + verificationRule);

        // 6 User prove credentialInfo.
        userResult =
                UserClient.proveCredentialInfo(
                        verificationRule,
                        blindedCredentialSignature,
                        credentialInfoMap,
                        credentialTemplateStorage,
                        masterSecret);
        String verificationRequest = userResult.verificationRequest;

        // 7 Verifier verify credential proof.
        VerifierResult verifierResult =
                VerifierClient.verifyProof(verificationRule, verificationRequest);
        Utils.checkWedprResult(verifierResult);
        System.out.println("Verify successfully!");

        // 8 Verifier get revealed attribute info from verificationRequest
        verifierResult =
                VerifierClient.getRevealedAttrsFromVerificationRequest(verificationRequest);
        String revealedAttributeInfo = verifierResult.revealedAttributeInfo;
        RevealedAttributeInfo revealedAttributes =
                RevealedAttributeInfo.parseFrom(Utils.stringToBytes(revealedAttributeInfo));
        List<StringToStringPair> attrList = revealedAttributes.getAttrList();
        System.out.println(attrList);
    }
}

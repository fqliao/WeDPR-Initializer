package com.webank.wedpr.example.selectivedisclosure;

import com.google.protobuf.InvalidProtocolBufferException;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.selectivedisclosure.CredentialTemplateStorage;
import com.webank.wedpr.selectivedisclosure.IssuerClient;
import com.webank.wedpr.selectivedisclosure.IssuerResult;
import com.webank.wedpr.selectivedisclosure.PredicateType;
import com.webank.wedpr.selectivedisclosure.SelectivedisclosureUtils;
import com.webank.wedpr.selectivedisclosure.UserClient;
import com.webank.wedpr.selectivedisclosure.UserResult;
import com.webank.wedpr.selectivedisclosure.VerifierClient;
import com.webank.wedpr.selectivedisclosure.proto.Predicate;
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
        credentialInfoMap.put("age", "30");
        credentialInfoMap.put("balance", "10000");

        // Predicate support: LT LE GT GE EQ
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.GE, 20);
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 10000);
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
    }

    public static void main(String[] args) throws WedprException, InvalidProtocolBufferException {
        run();
    }

    public static void run() throws WedprException, InvalidProtocolBufferException {

        /////////////////////////////////////
        // 1 Issuer makes credential template.
        /////////////////////////////////////
        IssuerResult issuerResult = IssuerClient.makeCredentialTemplate(attributeList);
        System.out.println("attributeList:" + attributeList);
        // Issuer saves templateSecretKey
        String templateSecretKey = issuerResult.templateSecretKey;
        CredentialTemplateStorage credentialTemplateStorage =
                issuerResult.credentialTemplateStorage;

        /////////////////////////////////////////////////////
        // 2 User makes credential without signature request.
        /////////////////////////////////////////////////////
        System.out.println("credentialInfoMap:" + credentialInfoMap);
        UserResult userResult =
                UserClient.makeCredential(credentialInfoMap, credentialTemplateStorage);
        String credentialSignatureRequest = userResult.credentialSignatureRequest;
        // User saves masterSecret.
        String masterSecret = userResult.masterSecret;
        String credentialSecretsBlindingFactors = userResult.credentialSecretsBlindingFactors;
        String userNonce = userResult.userNonce;

        ////////////////////////////
        // 3 Issuer signs credential.
        ////////////////////////////
        issuerResult =
                IssuerClient.signCredential(
                        credentialTemplateStorage,
                        templateSecretKey,
                        credentialSignatureRequest,
                        userId,
                        userNonce);
        String credentialSignature = issuerResult.credentialSignature;
        String issuerNonce = issuerResult.issuerNonce;

        /////////////////////////////////////
        // 4 User blinds credential signature.
        /////////////////////////////////////
        userResult =
                UserClient.blindCredentialSignature(
                        credentialSignature,
                        credentialInfoMap,
                        credentialTemplateStorage,
                        masterSecret,
                        credentialSecretsBlindingFactors,
                        issuerNonce);
        // User saves new credential signature.
        String blindedCredentialSignature = userResult.credentialSignature;

        /////////////////////////////////
        // 5 User sets VerificationRule.
        /////////////////////////////////
        VerificationRule verificationRule =
                SelectivedisclosureUtils.makeVerificationRule(revealedAttributeList, predicateList);

        /////////////////////////////////////
        // 6 User makes verification request.
        /////////////////////////////////////
        userResult =
                UserClient.proveCredentialInfo(
                        verificationRule,
                        blindedCredentialSignature,
                        credentialInfoMap,
                        credentialTemplateStorage,
                        masterSecret);
        String verificationRequest = userResult.verificationRequest;

        /////////////////////////////
        // 7 Verifier verifies proof.
        /////////////////////////////
        VerifierClient.verifyProof(verificationRule, verificationRequest);
        System.out.println("Verify successfully!");

        ////////////////////////////////////////////////////////////////////
        // 8 Verifier gets revealed attribute info from verification request.
        ////////////////////////////////////////////////////////////////////
        List<StringToStringPair> revealedAttributeList =
                SelectivedisclosureUtils.getRevealedAttributeList(verificationRequest);
        System.out.println(revealedAttributeList);
    }
}

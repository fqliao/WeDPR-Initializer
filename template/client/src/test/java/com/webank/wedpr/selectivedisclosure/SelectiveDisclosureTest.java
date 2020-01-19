package com.webank.wedpr.selectivedisclosure;

import com.google.protobuf.InvalidProtocolBufferException;
import com.webank.wedpr.common.WedprException;
import com.webank.wedpr.example.selectivedisclosure.DemoMain;
import com.webank.wedpr.selectivedisclosure.proto.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SelectiveDisclosureTest {

    @Test
    public void runCorrect() throws WedprException, InvalidProtocolBufferException {
        DemoMain.userId = "CnEDk9HrMnmiHXEV1WFgbVCRteYnPqsJwrTdcZaNhFVW";
        DemoMain.attributeList = Arrays.asList("name", "age", "balance");
        Map<String, String> credentialInfoMap = new HashMap<>();
        credentialInfoMap.put(
                "name",
                "5944657099558967239210949258394887428692050081607692519917050011144233115103");
        credentialInfoMap.put("age", "20");
        credentialInfoMap.put("balance", "10000");
        DemoMain.credentialInfoMap = credentialInfoMap;
        DemoMain.revealedAttributeList = Arrays.asList("name");
        ;
        List<Predicate> predicateList = new ArrayList<>();
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 20);
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 1000);
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;

        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void revealedAttributeError() throws WedprException, InvalidProtocolBufferException {
        List<String> attributeList = Arrays.asList("fisco");
        DemoMain.attributeList = attributeList;
        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void predicateEQError() throws WedprException, InvalidProtocolBufferException {
        List<Predicate> predicateList = new ArrayList<>();
        // correct value is 20
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 30);
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 1000);
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;
        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void predicateGTError() throws WedprException, InvalidProtocolBufferException {
        List<Predicate> predicateList = new ArrayList<>();
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 20);
        // correct value is less than 10000
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GT, 100000);
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;
        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void predicateGEError() throws WedprException, InvalidProtocolBufferException {
        List<Predicate> predicateList = new ArrayList<>();
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 20);
        // correct value is less than 10001
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 100000);
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 10001);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;
        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void predicateLTError() throws WedprException, InvalidProtocolBufferException {
        List<Predicate> predicateList = new ArrayList<>();
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 20);
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 1000);
        // correct value is greater than 10000
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LT, 1000);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;
        DemoMain.run();
    }

    @Test(expected = WedprException.class)
    public void predicateLEError() throws WedprException, InvalidProtocolBufferException {
        List<Predicate> predicateList = new ArrayList<>();
        Predicate predicate1 = SelectivedisclosureUtils.makePredicate("age", PredicateType.EQ, 20);
        Predicate predicate2 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.GE, 1000);
        // correct value is greater than 9999
        Predicate predicate3 =
                SelectivedisclosureUtils.makePredicate("balance", PredicateType.LE, 1000);
        predicateList.add(predicate1);
        predicateList.add(predicate2);
        predicateList.add(predicate3);
        DemoMain.predicateList = predicateList;
        DemoMain.run();
    }
}

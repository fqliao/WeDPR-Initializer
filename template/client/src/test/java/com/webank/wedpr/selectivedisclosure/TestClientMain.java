package com.webank.wedpr.selectivedisclosure;

import com.webank.wedpr.common.UtilsForTest;
import com.webank.wedpr.example.selectivedisclosure.DemoMain;
import com.webank.wedpr.selectivedisclosure.proto.Predicate;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;

public class TestClientMain {

    public static void main(String[] args) throws Exception {
        System.out.println("Welcome to test selective disclosure!");
        LineReader lineReader = UtilsForTest.getLineReader();
        KeyMap<Binding> keymap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keymap.bind(new Reference("beginning-of-line"), "\033[1~");
        keymap.bind(new Reference("end-of-line"), "\033[4~");

        String[] params = null;
        try {
            String userId = null;
            boolean userIdFlag = true;
            while (userIdFlag) {
                System.out.println("Please input userId:");
                System.out.println("Example:u123");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                if (params.length == 0) {
                    continue;
                }
                userId = params[0];
                userIdFlag = false;
            }
            System.out.println();
            List<String> attributeList = null;
            boolean attributeListFlag = true;
            while (attributeListFlag) {
                System.out.println("Please input attribute list:");
                System.out.println("Example:name age balance");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                if (params.length == 0) {
                    continue;
                }
                attributeList = Arrays.asList(params);
                attributeListFlag = false;
            }
            System.out.println();
            int size = attributeList.size();
            Map<String, String> credentialInfoMap = new HashMap<>(size);
            boolean credentialInfoMapFlag = true;
            while (credentialInfoMapFlag) {
                for (int i = 0; i < size; i++) {
                    System.out.println(
                            "Please input attribute '" + attributeList.get(i) + "' value:");
                    if (i == 0) {
                        System.out.println("Example:594465709955896723921094925839488742869205008");
                    }
                    params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                    if (params.length == 0) {
                        i--;
                        continue;
                    }
                    String valueStr = params[0].trim();
                    if (!SelectivedisclosureUtils.isDecimalInteger(valueStr)) {
                        System.out.println(
                                "Error:please provide attribute value greater or equal than "
                                        + Integer.MIN_VALUE
                                        + " by decimal digit.\n");
                        i--;
                        continue;
                    }
                    BigInteger value = new BigInteger(valueStr);
                    if (value.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
                        System.out.println(
                                "Error:please provide attribute value greater or equal than "
                                        + Integer.MIN_VALUE
                                        + " by decimal digit.\n");
                        i--;
                        continue;
                    }
                    credentialInfoMap.put(attributeList.get(i), valueStr);
                }
                credentialInfoMapFlag = false;
            }
            System.out.println();
            List<String> revealedAttributeList = null;
            boolean revealedAttributeListFlag = true;
            while (revealedAttributeListFlag) {
                System.out.println("Please input revealed attribute list:");
                System.out.println("Example:name");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                revealedAttributeList = Arrays.asList(params);
                revealedAttributeListFlag = false;
            }
            System.out.println();
            int predicateNum = 0;
            boolean predicateNumFlag = true;
            while (predicateNumFlag) {
                System.out.println("Please input predicate numbers:");
                System.out.println("Example:3");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                if (params.length == 0) {
                    continue;
                }
                try {
                    predicateNum = Integer.parseInt(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error:please provide predicate numbers from 0 to "
                                    + Integer.MAX_VALUE
                                    + "by integer mode.\n");
                    continue;
                }
                predicateNumFlag = false;
            }
            System.out.println();
            List<Predicate> predicateList = new ArrayList<>(predicateNum);
            boolean predicateListFlag = true;
            while (predicateListFlag) {
                for (int i = 0; i < predicateNum; i++) {
                    System.out.println("Please input predicate " + (i + 1) + " information:");
                    if (i == 0) {
                        System.out.println("Example:age GE 20");
                    }
                    params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                    if (params.length < 3) {
                        i--;
                        continue;
                    }
                    String attributeName = params[0].trim();
                    PredicateType predicateCondition;
                    try {
                        predicateCondition = PredicateType.valueOf(params[1].trim());
                    } catch (Exception e1) {
                        System.out.println(
                                "Error:please provide predicate condition in "
                                        + Arrays.toString(PredicateType.values())
                                        + ".\n");
                        i--;
                        continue;
                    }
                    int predicateValue = 0;
                    try {
                        predicateValue = Integer.parseInt(params[2].trim());
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "Error:please provide predicate value from "
                                        + Integer.MIN_VALUE
                                        + " to "
                                        + Integer.MAX_VALUE
                                        + " by decimal digit.\n");
                        i--;
                        continue;
                    }

                    Predicate predicate =
                            SelectivedisclosureUtils.makePredicate(
                                    attributeName, predicateCondition, predicateValue);
                    predicateList.add(predicate);
                }
                predicateListFlag = false;
            }
            DemoMain.userId = userId;
            DemoMain.attributeList = attributeList;
            DemoMain.credentialInfoMap = credentialInfoMap;
            DemoMain.revealedAttributeList = revealedAttributeList;
            DemoMain.predicateList = predicateList;

            System.out.println();
            System.out.println("Selective disclosure is running ...");
            DemoMain.run();

            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}

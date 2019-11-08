package com.webank.wedpr.anonymousvoting;

import com.webank.wedpr.common.CommandUtils;
import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.example.anonymousvoting.DemoMain;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;

public class TestClientMain {

    public static final String DIGTAL_PATERN = "^\\d+$";

    public static void main(String[] args) throws Exception {
        System.out.println("Welcome to test anonymous voting!");
        LineReader lineReader = CommandUtils.getLineReader();
        KeyMap<Binding> keymap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keymap.bind(new Reference("beginning-of-line"), "\033[1~");
        keymap.bind(new Reference("end-of-line"), "\033[4~");

        String[] params = null;
        int votingType = 0;
        boolean votingTypeFlag = true;
        try {
            while (votingTypeFlag) {
                System.out.println(
                        "Please input number to select voting type. 1: vote bounded, 2: vote unbounded");
                params = CommandUtils.tokenizeCommand(lineReader.readLine("> "));
                if (params.length == 0) {
                    continue;
                }
                try {
                    votingType = Integer.parseInt(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error: please provide voting type by integer mode from 0 to "
                                    + Integer.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                if (votingType != 1 && votingType != 2) {
                    continue;
                }
                votingTypeFlag = false;
            }
            System.out.println();
            List<String> candidates = null;
            boolean candidatesFlag = true;
            while (candidatesFlag) {
                System.out.println("Please input candidates name list:");
                System.out.println("Example: \"Alice\" \"Bob\" \"Kevin\"");
                params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                if (params.length == 0) {
                    continue;
                }
                boolean flag = true;
                for (String candidate : params) {
                    if (!(candidate.startsWith("\"") && candidate.endsWith("\""))) {
                        System.out.println(
                                "Error: please provide double quote for each candidate name: "
                                        + candidate
                                        + "\n");
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    candidates =
                            Arrays.asList(params)
                                    .stream()
                                    .map(x -> x.substring(1, x.length() - 1))
                                    .collect(Collectors.toList());
                    candidatesFlag = false;
                }
            }
            System.out.println();
            List<String> counterIds = null;
            boolean counterIdsFlag = true;
            while (counterIdsFlag) {
                System.out.println("Please input counter id list:");
                System.out.println("Example: \"100\" \"101\"");
                params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                if (params.length == 0) {
                    continue;
                }
                boolean flag = true;
                for (String counterId : params) {
                    if (!(counterId.startsWith("\"") && counterId.endsWith("\""))) {
                        System.out.println(
                                "Error: please provide double quote for each counter id: "
                                        + counterId
                                        + "\n");
                        flag = false;
                        break;
                    }
                }
                if (flag) {
                    counterIds =
                            Arrays.asList(params)
                                    .stream()
                                    .map(x -> x.substring(1, x.length() - 1))
                                    .collect(Collectors.toList());
                    counterIdsFlag = false;
                }
            }
            System.out.println();
            int voterCount = 0;
            boolean voterCountFlag = true;
            while (voterCountFlag) {
                System.out.println("Please input voter numbers:");
                System.out.println("Example: 3");
                params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                if (params.length == 0) {
                    System.out.println(
                            "Error: please provide voter numbers by integer mode from 0 to "
                                    + Integer.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                try {
                    voterCount = Integer.parseInt(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error: please provide voter numbers by integer mode from 0 to "
                                    + Integer.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                voterCountFlag = false;
            }
            System.out.println();
            int[] blankBallots = new int[voterCount];
            boolean blankBallotsFlag = true;
            while (blankBallotsFlag) {
                System.out.println("Please input blank ballot count for each voter:");
                System.out.println("Example: 10 20 30");
                params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                if (params.length != voterCount) {
                    System.out.println(
                            "Error: please provide " + voterCount + " voter ballot count.\n");
                    continue;
                }
                boolean flag = true;
                for (int i = 0; i < voterCount; i++) {
                    int blankBallot = 0;
                    try {
                        blankBallot = Integer.parseInt(params[i]);
                    } catch (NumberFormatException e) {
                        System.out.println(
                                "Error: please provide voter ballot count by integer mode from 0 to "
                                        + Integer.MAX_VALUE
                                        + ".\n");
                        flag = false;
                        break;
                    }
                    blankBallots[i] = blankBallot;
                }
                if (flag) {
                    blankBallotsFlag = false;
                }
            }
            System.out.println();
            int[][] allVotingBallots = new int[voterCount][candidates.size()];
            boolean allVotingBallotsFlag = true;
            while (allVotingBallotsFlag) {
                for (int i = 0; i < voterCount; i++) {
                    System.out.println("Please input voter" + (i + 1) + " voting ballot count:");
                    if (i == 0) {
                        System.out.println("Example for bounded: 1 2 3");
                        System.out.println("Example for unbounded: 10 10 0");
                    }
                    params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                    if (params.length != candidates.size()) {
                        System.out.println(
                                "Error: please provide " + candidates.size() + " voting ballot.\n");
                        i--;
                        continue;
                    }
                    int[] votingBallots = new int[candidates.size()];
                    boolean flag = true;
                    for (int j = 0; j < candidates.size(); j++) {
                        int votingBallot = 0;
                        try {
                            votingBallot = Integer.parseInt(params[j]);
                        } catch (NumberFormatException e) {
                            System.out.println(
                                    "Error: please provide voting ballot count by integer mode from 0 to "
                                            + Integer.MAX_VALUE
                                            + ".\n");
                            flag = false;
                            i--;
                            break;
                        }
                        votingBallots[j] = votingBallot;
                    }
                    if (votingType == 1) {
                        int sumVotingBallots = 0;
                        for (int v : votingBallots) {
                            sumVotingBallots = sumVotingBallots + v;
                        }
                        if (sumVotingBallots > blankBallots[i]) {
                            System.out.println(
                                    "Error: voting ballot sum "
                                            + sumVotingBallots
                                            + " is greater than blank ballot sum "
                                            + blankBallots[i]
                                            + ".\n");
                            i--;
                            flag = false;
                            continue;
                        }
                    }
                    if (votingType == 2) {
                        for (int v : votingBallots) {
                            if (v == blankBallots[i] || v == 0) {
                                continue;
                            } else {
                                System.out.println(
                                        "Error: voting ballot count "
                                                + v
                                                + " should be 0 or "
                                                + blankBallots[i]
                                                + ".\n");
                                i--;
                                flag = false;
                                break;
                            }
                        }
                    }
                    if (flag) {
                        allVotingBallots[i] = votingBallots;
                    } else {
                        continue;
                    }
                }
                allVotingBallotsFlag = false;
            }
            System.out.println();
            long maxVoteNumber = 0;
            boolean maxVoteNumberFlag = true;
            while (maxVoteNumberFlag) {
                System.out.println("Please input max vote number to caculate ballot:");
                System.out.println("Example: 1000");
                params = CommandUtils.tokenizeCommand(lineReader.readLine(votingType + "> "));
                if (params.length == 0) {
                    continue;
                }
                try {
                    maxVoteNumber = Long.parseLong(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error: please provide voter count by integer mode from 0 to "
                                    + Long.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                Arrays.sort(blankBallots);
                if (maxVoteNumber < blankBallots[blankBallots.length - 1]) {
                    System.out.println(
                            "Error: please provide max vote number greater than the max voting ballot count "
                                    + blankBallots[blankBallots.length - 1]
                                    + ".\n");
                    continue;
                }
                maxVoteNumberFlag = false;
            }
            ECKeyPair ecKeyPair = Utils.getEcKeyPair();
            DemoMain.regulatorSecretKey = ecKeyPair.getPrivateKey().toByteArray();
            DemoMain.regulatorPublicKey = ecKeyPair.getPublicKey().toByteArray();
            DemoMain.publicKeyCrypto = new PublicKeyCryptoExample();

            DemoMain.CANDIDATE_LIST = candidates;
            DemoMain.COUNTER_ID_LIST = counterIds;
            DemoMain.VOTER_COUNT = voterCount;
            DemoMain.MAX_VOTE_NUMBER = maxVoteNumber;
            if (votingType == 1) { // vote bounded
                DemoMain.BLANK_BALLOT_COUNT = blankBallots;
                DemoMain.VOTING_BALLOT_COUNT = allVotingBallots;
                DemoMain.doVoteBounded();
            } else if (votingType == 2) { // vote unbounded
                DemoMain.BLANK_BALLOT_WEIGHT = blankBallots;
                DemoMain.VOTING_BALLOT_WEIGHT = allVotingBallots;
                DemoMain.doVoteUnbounded();
            } else {
                System.out.println(
                        "Error: please input 1 to select vote bounded or 2 to select vote unbounded.");
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}

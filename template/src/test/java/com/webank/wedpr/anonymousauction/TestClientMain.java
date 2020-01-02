package com.webank.wedpr.anonymousauction;

import com.webank.wedpr.common.PublicKeyCryptoExample;
import com.webank.wedpr.common.Utils;
import com.webank.wedpr.common.UtilsForTest;
import com.webank.wedpr.example.anonymousauction.DemoMain;
import org.fisco.bcos.web3j.crypto.ECKeyPair;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.Reference;

public class TestClientMain {

    public static final String DIGTAL_PATERN = "^\\d+$";

    public static void main(String[] args) throws Exception {
        System.out.println("Welcome to test anonymous auction!");
        LineReader lineReader = UtilsForTest.getLineReader();
        KeyMap<Binding> keymap = lineReader.getKeyMaps().get(LineReader.MAIN);
        keymap.bind(new Reference("beginning-of-line"), "\033[1~");
        keymap.bind(new Reference("end-of-line"), "\033[4~");

        String[] params = null;
        int bidType = 0;
        boolean bidTypeFlag = true;
        try {
            while (bidTypeFlag) {
                System.out.println(
                        "Please input number to select bid type. 1: highest bid, 2: lowest bid");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine("> "));
                if (params.length == 0) {
                    continue;
                }
                try {
                    bidType = Integer.parseInt(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error: please provide voting type by integer mode from 0 to "
                                    + Integer.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                if (bidType != 1 && bidType != 2) {
                    continue;
                }
                bidTypeFlag = false;
            }
            System.out.println();
            int bidderNum = 0;
            boolean bidderNumFlag = true;
            while (bidderNumFlag) {
                System.out.println("Please input bidder numbers:");
                System.out.println("Example: 3");
                params = UtilsForTest.tokenizeCommand(lineReader.readLine(bidType + "> "));
                if (params.length == 0) {
                    continue;
                }
                try {
                    bidderNum = Integer.parseInt(params[0]);
                } catch (NumberFormatException e) {
                    System.out.println(
                            "Error: please provide voter numbers by integer mode from 0 to "
                                    + Integer.MAX_VALUE
                                    + ".\n");
                    continue;
                }
                bidderNumFlag = false;
            }
            System.out.println();
            int[] bidValues = new int[bidderNum];
            boolean bidValueFlag = true;
            while (bidValueFlag) {
                for (int i = 0; i < bidderNum; i++) {
                    System.out.println("Please input bidder" + (i + 1) + " bid value:");
                    if (i == 0) {
                        System.out.println("Example: 10");
                    }
                    params = UtilsForTest.tokenizeCommand(lineReader.readLine(bidType + "> "));
                    if (params.length == 0) {
                        i--;
                        continue;
                    }
                    int bidValue = 0;
                    try {
                        bidValue = Integer.parseInt(params[0]);
                        if (bidValue >= 0 && bidValue <= AuctionUtils.MAX_BID_VALUE) {
                            bidValues[i] = bidValue;
                        } else {
                            System.out.println(
                                    "Error: please provide bid value by integer mode from 0 to "
                                            + AuctionUtils.MAX_BID_VALUE
                                            + ".\n");
                            i--;
                            continue;
                        }

                    } catch (NumberFormatException e) {
                        System.out.println(
                                "Error: please provide bid value by integer mode from 0 to "
                                        + AuctionUtils.MAX_BID_VALUE
                                        + ".\n");
                        i--;
                        continue;
                    }
                }
                bidValueFlag = false;
            }
            ECKeyPair ecKeyPair = Utils.getEcKeyPair();
            DemoMain.regulatorSecretKey = ecKeyPair.getPrivateKey().toByteArray();
            DemoMain.regulatorPublicKey = ecKeyPair.getPublicKey().toByteArray();
            DemoMain.publicKeyCrypto = new PublicKeyCryptoExample();

            DemoMain.bidderNum = bidderNum;
            DemoMain.bidValues = bidValues;
            if (bidType == 1) {
                DemoMain.doAuction(BidType.HighestPriceBid);
            } else if (bidType == 2) {
                DemoMain.doAuction(BidType.LowestPriceBid);
            } else {
                System.out.println(
                        "Error: please input 1 to select highest price bid or 2 to select lowest price bid.");
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}

package com.webank.wedpr.example.anonymousauction;

import lombok.Data;

@Data
public class BidWinner {
    private String publicKey;
    private long bidValue;
}

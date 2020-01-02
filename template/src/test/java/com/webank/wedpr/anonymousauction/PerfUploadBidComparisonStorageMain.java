package com.webank.wedpr.anonymousauction;

import com.google.common.util.concurrent.RateLimiter;
import com.webank.wedpr.common.PerformanceCallback;
import com.webank.wedpr.common.PerformanceCollector;
import java.util.concurrent.atomic.AtomicInteger;
import org.fisco.bcos.web3j.protocol.core.methods.response.TransactionReceipt;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class PerfUploadBidComparisonStorageMain {

    private static AtomicInteger sended = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        UploadBidComparisonStorageParams uploadBidComparisonStorageParams =
                PerfAnonymousAuctionUtils.getUploadBidComparisonStorageParams();
        AnonymousAuctionExamplePerf anonymousAuction =
                uploadBidComparisonStorageParams.storageClient.getAnonymousAuction();
        String uuid = uploadBidComparisonStorageParams.bidderIdList.get(0);
        String bidComparisonRequest = uploadBidComparisonStorageParams.bidComparisonRequest;

        Integer count = Integer.parseInt(args[0]);
        Integer qps = Integer.parseInt(args[1]);
        ThreadPoolTaskExecutor threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(200);
        threadPool.setMaxPoolSize(500);
        threadPool.setQueueCapacity(count);
        threadPool.initialize();
        PerformanceCollector collector = new PerformanceCollector();
        collector.setTotal(count);

        RateLimiter limiter = RateLimiter.create(qps);
        Integer area = count / 10;
        final Integer total = count;
        System.out.println("Start upload bidComparisonStorage test, total: " + count);
        for (Integer i = 0; i < count; ++i) {
            threadPool.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            limiter.acquire();
                            PerformanceCallback callback = new PerformanceCallback();
                            callback.setCollector(collector);
                            try {
                                anonymousAuction.uploadBidComparisonStorage(
                                        uuid, bidComparisonRequest, callback);
                            } catch (Exception e) {
                                TransactionReceipt receipt = new TransactionReceipt();
                                receipt.setStatus("-1");
                                callback.onResponse(receipt);
                            }
                            int current = sended.incrementAndGet();
                            if (current >= area && ((current % area) == 0)) {
                                System.out.println(
                                        "Already sended: "
                                                + current
                                                + "/"
                                                + total
                                                + " transactions");
                            }
                        }
                    });
        }
    }
}

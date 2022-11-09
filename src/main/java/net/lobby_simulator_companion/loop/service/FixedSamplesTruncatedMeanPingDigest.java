package net.lobby_simulator_companion.loop.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.queue.CircularFifoQueue;

@RequiredArgsConstructor
public class FixedSamplesTruncatedMeanPingDigest implements PingDigest {

    private static final int NUM_SAMPLES = 20;
    private final CircularFifoQueue<Integer> reqDelays = new CircularFifoQueue<>(NUM_SAMPLES);

    private Long lastRequestTime;


    public void requestSent(long pingPacketTimestamp) {
        int requestDelay = lastRequestTime == null ? Integer.MAX_VALUE : (int) (pingPacketTimestamp - lastRequestTime);
        lastRequestTime = pingPacketTimestamp;
        reqDelays.add(requestDelay);
    }


    public int calculatePing() {
        int numReqDelays = reqDelays.size();

        if (numReqDelays <= 4) {
            return -1;
        }

        int max1 = 0, max2 = 0;
        int min1 = Integer.MAX_VALUE, min2 = Integer.MAX_VALUE;
        int delaySum = 0;

        for (Integer reqDelay : reqDelays) {

            if (reqDelay > max1) {
                max2 = max1;
                max1 = reqDelay;
            } else if (reqDelay > max2) {
                max2 = reqDelay;
            }

            if (reqDelay < min1) {
                min2 = min1;
                min1 = reqDelay;
            }
            else if (reqDelay < min2) {
                min2 = reqDelay;
            }

            delaySum += reqDelay;
        }

        delaySum -= (max1 + max2 + min1 + min2);

        return delaySum / (numReqDelays - 4);
    }

    @Override
    public void reset() {
        reqDelays.clear();
        lastRequestTime = null;
    }


}

package net.lobby_simulator_companion.loop.service;

import lombok.RequiredArgsConstructor;

import java.util.Deque;
import java.util.LinkedList;

@RequiredArgsConstructor
public class TimePeriodTruncatedMeanPingDigest implements PingDigest {


    @RequiredArgsConstructor
    private static final class Request {
        final long timestamp;
        final int delayFromPrevious;
    }

    private static final int SAMPLE_INTERVAL_MS = 1000;
    private final Deque<Request> requests = new LinkedList<>();

    private Long prevRequestTimestamp;


    public void requestSent(long pingPacketTimestamp) {
        while (!requests.isEmpty() && pingPacketTimestamp - requests.getFirst().timestamp > SAMPLE_INTERVAL_MS) {
            requests.removeFirst();
        }

        int requestDelay = prevRequestTimestamp == null ? Integer.MAX_VALUE : (int) (pingPacketTimestamp - prevRequestTimestamp);
        prevRequestTimestamp = pingPacketTimestamp;
        requests.add(new Request(pingPacketTimestamp, requestDelay));
    }


    public int calculatePing() {
        int numReqDelays = requests.size();

        if (numReqDelays <= 2) {
            return -1;
        }

        int max1 = 0, max2 = 0;
//        int min1 = Integer.MAX_VALUE, min2 = Integer.MAX_VALUE;
        int delaySum = 0;

        for (Request request : requests) {

            if (request.delayFromPrevious > max1) {
                max2 = max1;
                max1 = request.delayFromPrevious;
            } else if (request.delayFromPrevious > max2) {
                max2 = request.delayFromPrevious;
            }

//            if (reqDelay < min1) {
//                min2 = min1;
//                min1 = reqDelay;
//            }
//            else if (reqDelay < min2) {
//                min2 = reqDelay;
//            }

            delaySum += request.delayFromPrevious;
        }

        delaySum -= (max1 + max2); // + min1 + min2);

        return delaySum / (numReqDelays - 2);
    }

    @Override
    public void reset() {
        requests.clear();
        prevRequestTimestamp = null;
    }


}

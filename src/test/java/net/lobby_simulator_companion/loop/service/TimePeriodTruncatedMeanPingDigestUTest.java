package net.lobby_simulator_companion.loop.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TimePeriodTruncatedMeanPingDigestUTest {

    private TimePeriodTruncatedMeanPingDigest pingDigest = new TimePeriodTruncatedMeanPingDigest();


    @Test
    public void test() {
        // 9999, 50, 50, 30, 20, 100, 100
        verifyPingWithNewRequest(100L, -1);
        verifyPingWithNewRequest(150, -1);
        verifyPingWithNewRequest(200L, 50);
        verifyPingWithNewRequest(230L, (50 + 30) / 2);
        verifyPingWithNewRequest(250L, (50 + 30 + 20) / 3);
        verifyPingWithNewRequest(350L, (50 + 50 + 30 + 20) / 4);
        verifyPingWithNewRequest(450L, (50 + 50 + 30 + 20 + 100) / 5);
        // 50, 50, 30, 20, 100, 100, 100, 100, 40, 110, 200
        verifyPingWithNewRequest(550L, (50 + 50 + 30 + 20 + 100 + 100) / 6);
        verifyPingWithNewRequest(590L, (50 + 50 + 30 + 20 + 100 + 100 + 40) / 7);
        verifyPingWithNewRequest(700L, (50 + 50 + 30 + 20 + 100 + 100 + 100 + 40) / 8);
        verifyPingWithNewRequest(900L, (50 + 50 + 30 + 20 + 100 + 100 + 100 + 100 + 40) / 9);

//        verifyPingWithNewRequest(220L, (60 + 15 + 40) / 3);
//        verifyPingWithNewRequest(240L, (60 + 15 + 40 + 20) / 4);
//        verifyPingWithNewRequest(275L, (60 + 15 + 40 + 20 + 35) / 5);
//        verifyPingWithNewRequest(375L, (60 + 15 + 80 + 40 + 20 + 35) / 6);
//        verifyPingWithNewRequest(465L, (60 + 15 + 80 + 40 + 20 + 35 + 90) / 7);
//        verifyPingWithNewRequest(515L, (60 + 15 + 80 + 40 + 20 + 35 + 90 + 50) / 8);
//        verifyPingWithNewRequest(545L, (60 + 15 + 80 + 40 + 20 + 35 + 50 + 30) / 8);
    }


    private void verifyPingWithNewRequest(long packetTimestamp, int expectedPing) {
        pingDigest.requestSent(packetTimestamp);
        assertEquals(expectedPing, pingDigest.calculatePing());
    }


}

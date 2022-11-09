package net.lobby_simulator_companion.loop.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FixedSamplesTruncatedMeanPingDigestUTest {

    private FixedSamplesTruncatedMeanPingDigest pingDigest = new FixedSamplesTruncatedMeanPingDigest();


    @Test
    public void test() {
        verifyPingWithNewRequest(25L, -1);
        verifyPingWithNewRequest(85L, -1);
        verifyPingWithNewRequest(100L, 15);
        verifyPingWithNewRequest(180L, (60 + 15) / 2);
        verifyPingWithNewRequest(220L, (60 + 15 + 40) / 3);
        verifyPingWithNewRequest(240L, (60 + 15 + 40 + 20) / 4);
        verifyPingWithNewRequest(275L, (60 + 15 + 40 + 20 + 35) / 5);
        verifyPingWithNewRequest(375L, (60 + 15 + 80 + 40 + 20 + 35) / 6);
        verifyPingWithNewRequest(465L, (60 + 15 + 80 + 40 + 20 + 35 + 90) / 7);
        verifyPingWithNewRequest(515L, (60 + 15 + 80 + 40 + 20 + 35 + 90 + 50) / 8);
        verifyPingWithNewRequest(545L, (60 + 15 + 80 + 40 + 20 + 35 + 50 + 30) / 8);
    }


    private void verifyPingWithNewRequest(long packetTimestamp, int expectedPing) {
        pingDigest.requestSent(packetTimestamp);
        assertEquals(expectedPing, pingDigest.calculatePing());
    }


}

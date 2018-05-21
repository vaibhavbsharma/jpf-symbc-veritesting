package gov.nasa.jpf.symbc.veritesting;

/*
author: Vaibhav Sharma (vaibhav@umn.edu)
*/

import gov.nasa.jpf.symbc.VeritestingListener;

import static gov.nasa.jpf.symbc.VeritestingListener.DEBUG_OFF;

public class LogUtil {
    public static void log(int debugLevel, String message) {
        if (debugLevel == DEBUG_OFF) return;
        if (debugLevel == VeritestingListener.debug)
            System.out.println(message);
    }
}

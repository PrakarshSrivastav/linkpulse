package com.example.linkpulse.click;

public interface ClickRecorder {

    int MAX_REFERRER_LENGTH = 512;

    void record(String code, String referrer);

    static String truncate(String referrer) {
        if (referrer == null || referrer.length() <= MAX_REFERRER_LENGTH) {
            return referrer;
        }
        return referrer.substring(0, MAX_REFERRER_LENGTH);
    }
}
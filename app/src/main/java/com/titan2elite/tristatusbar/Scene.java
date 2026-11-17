package com.titan2elite.tristatusbar;

enum Scene {
    STATUS("status", true),
    SHADE("shade", true),
    LOCK("lock", true);

    final String key;
    final boolean defaultEnabled;

    Scene(String key, boolean defaultEnabled) {
        this.key = key;
        this.defaultEnabled = defaultEnabled;
    }
}

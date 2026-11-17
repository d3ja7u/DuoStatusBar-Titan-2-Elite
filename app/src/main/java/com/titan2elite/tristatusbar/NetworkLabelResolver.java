package com.titan2elite.tristatusbar;

import android.content.Context;
import android.telephony.TelephonyManager;

/**
 * Resolves the current cellular data network generation to a short label
 * (2G / 3G / 4G / 5G). Mirrors the logic of DuoLauncher's NetworkLabelResolver
 * but keeps only the coarse generation, which is enough for the status-bar
 * indicator. SystemUI has phone-state access, so no extra permission dance.
 */
final class NetworkLabelResolver {
    private NetworkLabelResolver() {
    }

    static String label(Context context) {
        int type = TelephonyManager.NETWORK_TYPE_UNKNOWN;
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(
                    Context.TELEPHONY_SERVICE);
            if (tm != null) {
                type = tm.getDataNetworkType();
            }
        } catch (Throwable ignored) {
            // Read failure: fall through to the default "5G" label.
        }
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_NR:
                return "5G";
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "4G";
            case TelephonyManager.NETWORK_TYPE_UMTS:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSPAP:
            case TelephonyManager.NETWORK_TYPE_TD_SCDMA:
            case TelephonyManager.NETWORK_TYPE_EVDO_0:
            case TelephonyManager.NETWORK_TYPE_EVDO_A:
            case TelephonyManager.NETWORK_TYPE_EVDO_B:
            case TelephonyManager.NETWORK_TYPE_EHRPD:
                return "3G";
            case TelephonyManager.NETWORK_TYPE_GPRS:
            case TelephonyManager.NETWORK_TYPE_EDGE:
            case TelephonyManager.NETWORK_TYPE_CDMA:
            case TelephonyManager.NETWORK_TYPE_1xRTT:
            case TelephonyManager.NETWORK_TYPE_GSM:
                return "2G";
            default:
                // Unknown / no data: keep the familiar default appearance.
                return "5G";
        }
    }
}

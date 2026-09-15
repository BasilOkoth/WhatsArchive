package com.elimara.chatarchive;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cached Google Play entitlement used by the UI.
 *
 * Google Play ownership is refreshed by PlayBillingManager. The local cache
 * keeps Pro usable while the device is temporarily offline.
 */
public final class ProAccess {
    private static final String PREFS = "chatarchive_pro_entitlement";
    private static final String KEY_PLAY_OWNED = "play_owned";
    private static final String KEY_WELCOME_SEEN = "welcome_seen";

    private ProAccess() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isPro(Context context) {
        return prefs(context).getBoolean(KEY_PLAY_OWNED, false);
    }

    public static boolean isPlayOwned(Context context) {
        return prefs(context).getBoolean(KEY_PLAY_OWNED, false);
    }

    public static void setPlayOwned(Context context, boolean owned) {
        prefs(context).edit().putBoolean(KEY_PLAY_OWNED, owned).apply();
    }

    public static boolean hasSeenWelcome(Context context) {
        return prefs(context).getBoolean(KEY_WELCOME_SEEN, false);
    }

    public static void markWelcomeSeen(Context context) {
        prefs(context).edit().putBoolean(KEY_WELCOME_SEEN, true).apply();
    }
}

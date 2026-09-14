package com.basil.whatsarchive;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

public class WhatsArchiveApp extends Application
        implements Application.ActivityLifecycleCallbacks {

    private int startedActivities = 0;
    private boolean changingConfiguration = false;

    private AppSecurity security;
    private LicenseManager licenseManager;

    @Override
    public void onCreate() {
        super.onCreate();
        security = new AppSecurity(this);
        licenseManager = new LicenseManager(this);
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(
            Activity activity,
            Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        boolean returningFromBackground = startedActivities == 0;
        startedActivities++;

        if (!returningFromBackground) return;

        if (activity instanceof LockActivity
                || activity instanceof ActivationActivity) {
            return;
        }

        if (!licenseManager.isLicensed()) return;
        if (!security.isLockEnabled()) return;

        if (security.shouldAutoLockNow()) {
            Intent intent = new Intent(activity, LockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        changingConfiguration = activity.isChangingConfigurations();

        startedActivities = Math.max(0, startedActivities - 1);

        if (startedActivities == 0 && !changingConfiguration) {
            security.recordBackgrounded();
        }
    }

    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}

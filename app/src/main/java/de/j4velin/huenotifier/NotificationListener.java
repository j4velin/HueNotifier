/*
 * Copyright 2017 Thomas Hoffmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.j4velin.huenotifier;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationListener extends NotificationListenerService {

    private final static long TIME_THRESHOLD = 1000;
    private static boolean ignoreOnGoing = true, ignoreLowPriority = true;
    private long lastTime = 0L;
    private String lastPackage = null;

    static void loadValues(final Context c) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        ignoreOnGoing = prefs.getBoolean("ignoreOnGoing", true);
        ignoreLowPriority = prefs.getBoolean("ignoreLowPriority", true);
    }

    @Override
    public IBinder onBind(final Intent mIntent) {
        getSharedPreferences("NotificationListener", Context.MODE_PRIVATE).edit()
                .putBoolean("listenerEnabled", true).apply();
        loadValues(this);
        return super.onBind(mIntent);
    }

    @Override
    public boolean onUnbind(final Intent mIntent) {
        getSharedPreferences("NotificationListener", Context.MODE_PRIVATE).edit()
                .putBoolean("listenerEnabled", false).apply();
        return super.onUnbind(mIntent);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }

    @Override
    public void onNotificationPosted(final StatusBarNotification notification) {
        if (!notification.getPackageName().equals("android")
                && (!ignoreLowPriority || notification
                .getNotification().priority > Notification.PRIORITY_MIN)
                && (!ignoreOnGoing || !notification.isOngoing())) {
            long current = System.currentTimeMillis();
            String pkg = notification.getPackageName();
            if (current - lastTime < TIME_THRESHOLD && lastPackage.equals(pkg)) {
                if (BuildConfig.DEBUG)
                    android.util.Log
                            .d(MainActivity.TAG, "ignore duplicate notification from " + pkg);
                return;
            }
            lastTime = current;
            lastPackage = pkg;
            if (BuildConfig.DEBUG)
                android.util.Log.d(MainActivity.TAG, "received notification from " + lastPackage);
            Database db = Database.getInstance(this);
            if (db.contains(lastPackage)) {
                String pattern = db.getPattern(lastPackage);
                startService(new Intent(this, ColorFlashService.class)
                        .putExtra("lights", Util.getLights(pattern))
                        .putExtra("colors", Util.getColors(pattern)));
            }
            db.close();
        }
    }

}

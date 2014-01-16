/*
Copyright © 2013-2014, Silent Circle, LLC.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Any redistribution, use, or modification is done solely for personal 
      benefit and not for any commercial purpose or for monetary gain
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name Silent Circle nor the names of its contributors may 
      be used to endorse or promote products derived from this software 
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL SILENT CIRCLE, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.silentcircle.keymngr;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.app.Service;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import java.util.Hashtable;

import com.silentcircle.contacts.R;
/**
 * The KeyService is a very simple service that provides long term storage and
 * some helper functions.
 *
 * In Android Services usually live as long as the applications live and also
 * keeps the application alive. Refer to the relevant lifecycle documentation.
 *
 * Android manages the lifecycle of content providers and a content provider does
 * not offer an 'onDestroy()' method, only 'onCreate()'.
 *
 * Created by werner on 31.08.13.
 */
public class KeyService extends Service {

    private static String TAG = "KeyService";

    private static PackageManager mPackageMngr;
    private static ContentResolver mContentResolver;

    private static boolean started;
    private static boolean ready;

    private static char[] storeKey;

    static class AppInfo {
        long token;
        String displayName;
    }

    private static Hashtable<String, AppInfo> registeredApps = new Hashtable<String, AppInfo>(5, 0.75f);

    private final IBinder mBinder = new LocalBinder();

    private NotificationManager notificationManager;
    private long lockUnlockTime;
    private Bitmap notifyLargeIcon;

    private KeyStoreDatabase mDbHelper;
    private static SQLiteDatabase database;

    /**
     * Class for clients to access. Because we know this service always runs in the same process
     * as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public KeyService getService() {
            return KeyService.this;
        }
    }

    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private static KeyStoreDatabase getDatabaseHelper(final Context context) {
        return KeyStoreDatabase.getInstance(context);
    }

    protected static SQLiteDatabase getDatabase() {
        return (ready) ? database : null;
    }

    @Override
    public void onCreate() {
        if (mPackageMngr == null) {
            mPackageMngr = getPackageManager();
        }
        if (mContentResolver == null) {
            mContentResolver = getContentResolver();
        }
        //you must set Context on SQLiteDatabase first
        SQLiteDatabase.loadLibs(this);

        mDbHelper = getDatabaseHelper(this);
        lockUnlockTime = System.currentTimeMillis();
        notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        startForeground(R.string.key_manager, getNotification());   // use string resource as id
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            Log.d(TAG, "++++ Intent null - restart happened");
        }
        started = true;
        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "++++ onDestroy called.");
        ProviderDbBackend.sendLockRequests();                // Service onDestroy is an 'implicit' lock
        started = false;
        closeDatabase();
        stopForeground(true);
    }

    public void updateNotification() {
        if (notificationManager != null) {
            notificationManager.notify(R.string.key_manager, getNotification());
        }
    }

    private Notification getNotification() {
        CharSequence text = KeyService.isReady() ? getString(R.string.unlocked) : getString(R.string.locked);

        Intent intent = new Intent(this, KeyManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

        if (notifyLargeIcon == null)
            notifyLargeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher_km);

        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.key_manager))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_km)
                .setLargeIcon(notifyLargeIcon)
                .setWhen(lockUnlockTime)
                .setContentIntent(contentIntent)
                .setPriority(Notification.PRIORITY_MIN)
                .build();
        return notification;
    }

    static boolean isReady() {
        return ready;
    }

    static PackageManager getmPackageMngr() {
        return mPackageMngr;
    }

    static ContentResolver getmContentResolver() {
        return mContentResolver;
    }

    static Hashtable<String, AppInfo> getRegisteredApps() {
        return registeredApps;
    }

    boolean openOrCreateDatabase(CharSequence password) {
        try {
            database = mDbHelper.getWritableDatabase(password.toString());
        } catch (Exception e) {
            Log.w(TAG, "Cannot open writable database", e);
            return false;
        }
        ready = true;
        lockUnlockTime = System.currentTimeMillis();
        return true;
    }

    void closeDatabase() {
        ready = false;
        if (database != null)
            database.close();
        lockUnlockTime = System.currentTimeMillis();
    }

    static void notifyActivity() {
        KeyManagerActivity.updateAppList();
    }
}

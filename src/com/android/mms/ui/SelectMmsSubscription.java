/*
 * Copyright (c) 2012, Code Aurora Forum. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained for attribution purposes only.
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.ui;

import com.android.mms.R;
import com.android.internal.telephony.MSimConstants;

import android.app.PendingIntent;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Telephony.Mms;

import android.telephony.TelephonyManager;
import android.telephony.MSimTelephonyManager;

import android.util.Log;
import android.widget.Toast;

public class SelectMmsSubscription extends Service {
    static private final String TAG = "SelectMmsSubscription";

    private Context mContext;
    private Intent startUpIntent;
    private int requestedSub =0;
    private int triggerSwitchOnly = 0;
    private SwitchSubscriptionTask switchSubscriptionTask;;

    private boolean flagOkToStartTransactionService = false;
    private ConnectivityBroadcastReceiver mReceiver;

    public class SwitchSubscriptionTask extends AsyncTask<Integer, Void, Integer> {


        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected Integer doInBackground(Integer... params) {
            Log.d(TAG, "doInBackground(), Thread="+
                    Thread.currentThread().getName());

            if (getCurrentSubcription() != params[0]) {
                return switchSubscriptionTo(params[0]);
            }
            return -1; //no change.
        }

        @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                Log.d(TAG, "onPostExecute(), Thread="+Thread.currentThread().getName());


                if (result == -1) {
                    Log.d(TAG, "No DDS switch required. set requestedSubid=-1");
                    Bundle tempBundle = startUpIntent.getExtras();
                    tempBundle.putInt(Mms.SUB_ID, -1);
                    startUpIntent.putExtras(tempBundle);
                } else {

                    String status = "Data subscription switch "+((result ==1)? "was success.": "failed.");

                    Toast.makeText(mContext, status, Toast.LENGTH_SHORT).show();
                }

                //TODO: Below set of nested conditions are dirty, need a better
                //way.
                if (result == -1 || result == 1) {
                    if (triggerSwitchOnly == 1) {
                        removeAbortNotification();
                        stopSelf();
                        return;

                    }
                    if(result == -1) {
                        //no change in sub and the trigger was not switch only,
                        //start transaction service without any UI.
                        Log.d(TAG, "Starting transaction service");
                        triggerTransactionService();
                        stopSelf();
                    } else {
                        //Switch was real and it succeeded, start transaction
                        //service with all UI hoopla
                        flagOkToStartTransactionService = true; //if PDP is up, transactionService can be started.
                        //TODO: may be in future, we should have a timeout for
                        //PDP up event. Upon expiration trigger transaction
                        //service(if not done already). It should work for the cases where
                        //second SIM does not have default propfile set. This is not the
                        //bullet-proof method but should work most of the time.
                        registerForPdpUp();
                        removeStatusBarNotification();
                        showNotificationMmsInProgress();
                        showNotificationAbortAndSwitchBack();
                    }
                }
            }

        private void removeAbortNotification() {
            Log.d(TAG, "removeAbortNotification");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            mNotificationManager.cancel("ABORT", 2); //ID=2, abort notification
            mNotificationManager.cancel(getOtherSub(requestedSub));

        }

        private void showNotificationAbortAndSwitchBack() {
            Log.d(TAG, "showNotificationAbortAndSwitchBack");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            //TODO: use the proper messaging icon
            int icon = android.R.drawable.stat_notify_chat;

            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon, null, when);

            Intent src = new Intent();
            src.putExtra("TRIGGER_SWITCH_ONLY", 1);
            src.putExtra(Mms.SUB_ID, getOtherSub(requestedSub));

            Intent notificationIntent = new Intent(mContext,
                    com.android.mms.ui.SelectMmsSubscription.class);
            notificationIntent.putExtras(src);
            PendingIntent contentIntent = PendingIntent.getService(mContext, 0,
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            notification.setLatestEventInfo(mContext, mContext.getString(R.string.abort_mms),
                    mContext.getString(R.string.abort_mms_text), contentIntent);

            mNotificationManager.notify("ABORT", 2, notification); //ID=2 for the abort.

        }

        private int getOtherSub(int sub) {
            return (sub == 0)? 1 : 0;
        }

        private void showNotificationMmsInProgress() {
            Log.d(TAG, "showNotificationMmsInProgress");
            String ns = Context.NOTIFICATION_SERVICE;
            NotificationManager mNotificationManager = (NotificationManager)
                    mContext.getSystemService(ns);
            //TODO: use the proper messaging icon
            int icon = android.R.drawable.stat_notify_chat;

            long when = System.currentTimeMillis();

            Notification notification = new Notification(icon,
                    mContext.getString(R.string.progress_mms_title), when);

            Intent notificationIntent = new Intent(mContext,
                    com.android.mms.transaction.TransactionService.class);
            Bundle tempBundle = startUpIntent.getExtras();

            notificationIntent.putExtras(tempBundle); //copy all extras

            PendingIntent contentIntent = PendingIntent.getService(mContext, 0,
                    notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setLatestEventInfo(mContext, mContext.getString(R.string.progress_mms),
                    mContext.getString(R.string.progress_mms_text), contentIntent);

            mNotificationManager.notify(requestedSub, notification);

        }

        void sleep(int ms) {
            try {
                Log.d(TAG, "Sleeping for "+ms+"(ms)...");
                Thread.currentThread().sleep(ms);
                Log.d(TAG, "Sleeping...Done!");
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

        private int switchSubscriptionTo(int sub) {
            TelephonyManager tmgr = (TelephonyManager)
                    mContext.getSystemService(Context.TELEPHONY_SERVICE);
            if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                Log.d(TAG, "DSDS enabled");
                MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                    mContext.getSystemService (Context.MSIM_TELEPHONY_SERVICE);
                int result = (mtmgr.setPreferredDataSubscription(sub))? 1: 0;
                if (result == 1) { //Success.
                    Log.d(TAG, "Subscription switch done.");
                    sleep(1000);

                    while(!isNetworkAvailable()) {
                        Log.d(TAG, "isNetworkAvailable = false, sleep..");
                        sleep(1000);
                    }
                }
                return result;
            }
            return 1;
        }

    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo ni = connMgr.getNetworkInfo(ConnectivityManager.TYPE_MOBILE_MMS);

        return (ni == null ? false : ni.isAvailable());

    }

    private void triggerTransactionService() {
        Log.d(TAG, "triggerTransactionService");
        Intent svc = new Intent(mContext, com.android.mms.transaction.TransactionService.class);

        //The purpose of subId in the start Intent was to trigger DDS switc, if
        //required. The purpose is served, clean up the intent and forward it to
        //TransactionService.
        Bundle tempBundle = startUpIntent.getExtras();
        svc.putExtras(tempBundle); //copy all extras
        mContext.startService(svc);
    }

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "ConnectivityBroadcastReceiver.onReceive() action: " + action);

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                return;
            }

            boolean noConnectivity =
                intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            NetworkInfo networkInfo = (NetworkInfo)
                intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);

            Log.d(TAG, "Handle ConnectivityBroadcastReceiver.onReceive(): " + networkInfo);

            // Check availability of the mobile network.
            if ((networkInfo == null) || (networkInfo.getType() !=
                        ConnectivityManager.TYPE_MOBILE)) {
                Log.d(TAG, "   type is not TYPE_MOBILE, bail");
                return;
            }

            if (!networkInfo.isConnected()) {
                Log.d(TAG, "   TYPE_MOBILE not connected, bail");
                return;
            }

            Log.d(TAG, "TYPE_MOBILE is available and connected.");

            // TYPE_MOBILE is available and connected.
            if (flagOkToStartTransactionService == true) {
                triggerTransactionService();
                flagOkToStartTransactionService = false;
                //finish();
                stopSelf();
            }
        }
    };


    private void removeStatusBarNotification() {
        Log.d(TAG, "removeStatusBarNotification");
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager mNotificationManager = (NotificationManager)
                mContext.getSystemService(ns);
        mNotificationManager.cancel(requestedSub);

    }

    void registerForPdpUp() {
        mReceiver = new ConnectivityBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        Log.d(TAG, "registerReceiver");
        registerReceiver(mReceiver, intentFilter);
    }


    public void onCreate() {
        super.onCreate();

        Log.d (TAG, "Create()");
        mContext = getApplicationContext();

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {

        startUpIntent = intent;

        requestedSub = startUpIntent.getIntExtra(Mms.SUB_ID, 0);
        triggerSwitchOnly =startUpIntent.getIntExtra("TRIGGER_SWITCH_ONLY", 0);

        Log.d(TAG, "Requested sub = "+requestedSub);
        Log.d(TAG, "triggerSwitchOnly = "+triggerSwitchOnly);


        switchSubscriptionTask = new SwitchSubscriptionTask();
        switchSubscriptionTask.execute(requestedSub);
        return Service.START_NOT_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");
        if (mReceiver != null) {
            Log.d(TAG, "onDestroy(): UnregisterReceiver.");
            unregisterReceiver(mReceiver);
        }
        super.onDestroy();
    }


    private int getCurrentSubcription() {
        TelephonyManager tmgr = (TelephonyManager)
                getSystemService(Context.TELEPHONY_SERVICE);

        if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
            MSimTelephonyManager mtmgr = (MSimTelephonyManager)
                getSystemService (Context.MSIM_TELEPHONY_SERVICE);
            return mtmgr.getPreferredDataSubscription();

        } else {
            return 0;
        }
    }


}

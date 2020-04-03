package com.dieam.reactnativepushnotification.modules;

import java.util.Map;

import com.dieam.reactnativepushnotification.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.dieam.reactnativepushnotification.helpers.ApplicationBadgeHelper;
import com.facebook.react.ReactApplication;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;

import org.json.JSONObject;

import java.util.List;
import java.util.Random;

import static com.dieam.reactnativepushnotification.modules.RNPushNotification.LOG_TAG;

public class RNPushNotificationListenerService extends FirebaseMessagingService {
    private static String KEY_INCOMING_CALL = "incomingCall";
    private static int NOTIFICATION_ID = 10;
    private static String NOTIFICATION_CHANNEL_NAME = "benefy-notification-channel";
    private static String NOTIFICATION_CHANNEL_ID = NOTIFICATION_CHANNEL_NAME + "-id";
    private static long[] VIBRATION_PATTERN = new long[]{1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000, 1000, 2000};

    private boolean isDeviceLocked(KeyguardManager keyguardManager, PowerManager powerManager) {
        return keyguardManager.isDeviceLocked() || !powerManager.isInteractive();
    }

    private void sendIncomingCallNotification(Context context, int notificationIcon, String callerName, PendingIntent mainIntent, PendingIntent cancelIntent) {
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(notificationIcon)
                        .setContentTitle("Incoming call")
                        .setContentText(callerName)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setVibrate(VIBRATION_PATTERN)
                        .setAutoCancel(true)
                        // Use a full-screen intent only for the highest-priority alerts where you
                        // have an associated activity that you would like to launch after the user
                        // interacts with the notification. Also, if your app targets Android 10
                        // or higher, you need to request the USE_FULL_SCREEN_INTENT permission in
                        // order for the platform to invoke this notification.
                        .setFullScreenIntent(mainIntent, true)
                        .addAction(0, "Accept", mainIntent)
                        .addAction(0, "Reject", cancelIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Channel title", NotificationManager.IMPORTANCE_HIGH);
            // every time we change something related to channel, we need to clear data or reinstall the app for the changes to take effect
            channel.setVibrationPattern(VIBRATION_PATTERN);
            notificationManager.createNotificationChannel(channel);
        }
        Notification incomingCallNotification = notificationBuilder.build();
        notificationManager.notify(NOTIFICATION_ID, incomingCallNotification);
    }

    private String getCallerName(RemoteMessage message) {
        try {
            String data = message.getData().get(KEY_INCOMING_CALL);
            JSONObject dataObject = new JSONObject(data);
            String caller = dataObject.getString("user");
            JSONObject callerObject = new JSONObject(caller);

            String callerFirstName = callerObject.getString("firstName");
            String callerLastName = callerObject.getString("lastName");

            return callerFirstName + " " + callerLastName;
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to get caller name", e);
            return "";
        }

    }

    private void handleIncomingCall(RemoteMessage message) {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            PowerManager powerManager = (PowerManager) getApplicationContext()
                    .getSystemService(Context.POWER_SERVICE);
            Context context = getApplicationContext();
            boolean showIncomingCallScreen = isDeviceLocked(keyguardManager, powerManager);
            Intent cancelBtnIntent = new Intent(context, AutoDismissReceiver.class);
            cancelBtnIntent.putExtra("notificationId", NOTIFICATION_ID);
            PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(context, 1, cancelBtnIntent, 0);
            Intent intent = getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.putExtra(KEY_INCOMING_CALL, message.getData().get(KEY_INCOMING_CALL));
            intent.putExtra("showIncomingCallScreen", showIncomingCallScreen);

            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(context, 0,
                    intent, PendingIntent.FLAG_UPDATE_CURRENT);

            int notificationIcon;
            int mainIcon = context.getResources().getIdentifier("ic_stat_notification", "mipmap", context.getPackageName());
            int fallbackIcon = context.getResources().getIdentifier("ic_launcher_round", "mipmap", context.getPackageName());

            if (mainIcon == 0) {
                notificationIcon = fallbackIcon;
            } else {
                notificationIcon = mainIcon;
            }

            String callerName = getCallerName(message);

            sendIncomingCallNotification(context, notificationIcon, callerName, fullScreenPendingIntent, cancelPendingIntent);
        } catch (Exception e) {
            Log.w(LOG_TAG, "Failed to open application on message receive", e);
        }
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (message.getData().get(KEY_INCOMING_CALL) != null) {
            handleIncomingCall(message);
        } else {
            String from = message.getFrom();
            RemoteMessage.Notification remoteNotification = message.getNotification();

            final Bundle bundle = new Bundle();
            // Putting it from remoteNotification first so it can be overriden if message
            // data has it
            if (remoteNotification != null) {
                // ^ It's null when message is from GCM
                bundle.putString("title", remoteNotification.getTitle());
                bundle.putString("message", remoteNotification.getBody());
            }

            for (Map.Entry<String, String> entry : message.getData().entrySet()) {
                bundle.putString(entry.getKey(), entry.getValue());
            }
            JSONObject data = getPushData(bundle.getString("data"));

            // Copy `twi_body` to `message` to support Twilio
            if (bundle.containsKey("twi_body")) {
                bundle.putString("message", bundle.getString("twi_body"));
            }

            if (data != null) {
                if (!bundle.containsKey("message")) {
                    bundle.putString("message", data.optString("alert", null));
                }
                if (!bundle.containsKey("title")) {
                    bundle.putString("title", data.optString("title", null));
                }
                if (!bundle.containsKey("sound")) {
                    bundle.putString("soundName", data.optString("sound", null));
                }
                if (!bundle.containsKey("color")) {
                    bundle.putString("color", data.optString("color", null));
                }

                final int badge = data.optInt("badge", -1);
                if (badge >= 0) {
                    ApplicationBadgeHelper.INSTANCE.setApplicationIconBadgeNumber(this, badge);
                }
            }

            Log.d(LOG_TAG, "onMessageReceived: " + bundle);

            // We need to run this on the main thread, as the React code assumes that is true.
            // Namely, DevServerHelper constructs a Handler() without a Looper, which triggers:
            // "Can't create handler inside thread that has not called Looper.prepare()"
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    // Construct and load our normal React JS code bundle
                    ReactInstanceManager mReactInstanceManager = ((ReactApplication) getApplication()).getReactNativeHost().getReactInstanceManager();
                    ReactContext context = mReactInstanceManager.getCurrentReactContext();
                    // If it's constructed, send a notification
                    if (context != null) {
                        handleRemotePushNotification((ReactApplicationContext) context, bundle);
                    } else {
                        // Otherwise wait for construction, then send the notification
                        mReactInstanceManager.addReactInstanceEventListener(new ReactInstanceManager.ReactInstanceEventListener() {
                            public void onReactContextInitialized(ReactContext context) {
                                handleRemotePushNotification((ReactApplicationContext) context, bundle);
                            }
                        });
                        if (!mReactInstanceManager.hasStartedCreatingInitialContext()) {
                            // Construct it in the background
                            mReactInstanceManager.createReactContextInBackground();
                        }
                    }
                }
            });
        }
    }

    private JSONObject getPushData(String dataString) {
        try {
            return new JSONObject(dataString);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleRemotePushNotification(ReactApplicationContext context, Bundle bundle) {

        // If notification ID is not provided by the user for push notification, generate one at random
        if (bundle.getString("id") == null) {
            Random randomNumberGenerator = new Random(System.currentTimeMillis());
            bundle.putString("id", String.valueOf(randomNumberGenerator.nextInt()));
        }

        Boolean isForeground = isApplicationInForeground();

        RNPushNotificationJsDelivery jsDelivery = new RNPushNotificationJsDelivery(context);
        bundle.putBoolean("foreground", isForeground);
        bundle.putBoolean("userInteraction", false);
        jsDelivery.notifyNotification(bundle);

        // If contentAvailable is set to true, then send out a remote fetch event
        if (bundle.getString("contentAvailable", "false").equalsIgnoreCase("true")) {
            jsDelivery.notifyRemoteFetch(bundle);
        }

        Log.v(LOG_TAG, "sendNotification: " + bundle);

        Application applicationContext = (Application) context.getApplicationContext();
        RNPushNotificationHelper pushNotificationHelper = new RNPushNotificationHelper(applicationContext);
        pushNotificationHelper.sendToNotificationCentre(bundle);

    }

    private boolean isApplicationInForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List<RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos != null) {
            for (RunningAppProcessInfo processInfo : processInfos) {
                if (processInfo.processName.equals(getApplication().getPackageName())) {
                    if (processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                        for (String d : processInfo.pkgList) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

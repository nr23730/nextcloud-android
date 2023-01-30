package com.nextcloud.utils;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.nextcloud.client.account.User;
import com.nextcloud.client.account.UserAccountManager;
import com.nextcloud.client.jobs.AccountRemovalWork;
import com.nextcloud.client.jobs.BackgroundJobManager;
import com.nextcloud.client.jobs.NotificationWork;
import com.nextcloud.client.preferences.AppPreferences;
import com.owncloud.android.datamodel.ArbitraryDataProvider;
import com.owncloud.android.datamodel.ArbitraryDataProviderImpl;
import com.owncloud.android.datamodel.PushConfigurationState;
import com.owncloud.android.utils.PushUtils;

import org.jetbrains.annotations.NotNull;
import org.unifiedpush.android.connector.MessagingReceiver;

import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import dagger.android.AndroidInjection;

public class UnifiedPushUtils extends MessagingReceiver {

    @Inject AppPreferences preferences;
    @Inject UserAccountManager accountManager;
    @Inject BackgroundJobManager backgroundJobManager;

    public UnifiedPushUtils() {
        super();
    }

    @Override
    public void onNewEndpoint(@NotNull Context context, @NotNull String endpoint, @NotNull String instance) {
        AndroidInjection.inject(this, context);
        onUnregistered(context, instance);
        preferences.setPushServerUrl(endpoint);
        Thread registerNewEndpoint = new Thread(() -> PushUtils.pushRegistrationToServer(accountManager, endpoint));
        registerNewEndpoint.start();
    }

    @Override
    public void onRegistrationFailed(@NotNull Context context,  @NotNull String instance) {
        AndroidInjection.inject(this, context);
        onUnregistered(context, instance);
    }

    @Override
    public void onUnregistered(@NotNull Context context,  @NotNull String instance) {
        AndroidInjection.inject(this, context);
        try {
            ArbitraryDataProvider adp = new ArbitraryDataProviderImpl(context);
            for (User u : accountManager.getAllUsers()) {
                Thread removeOldEndpoint = new Thread(() -> AccountRemovalWork.Companion.unregisterPushNotifications(context, u, adp, preferences, accountManager));
                removeOldEndpoint.start();
                removeOldEndpoint.join();
                String arbitraryDataPushString = adp.getValue(u, PushUtils.KEY_PUSH);
                String pushServerUrl = preferences.getPushServerUrl();
                if (!TextUtils.isEmpty(arbitraryDataPushString) && !TextUtils.isEmpty(pushServerUrl)) {
                    Gson gson = new Gson();
                    PushConfigurationState pushArbitraryData = gson.fromJson(
                        arbitraryDataPushString,
                        PushConfigurationState.class
                                                                            );
                    pushArbitraryData.setShouldBeDeleted(false);
                    adp.storeOrUpdateKeyValue(
                        u.getAccountName(),
                        PushUtils.KEY_PUSH,
                        gson.toJson(pushArbitraryData)
                                             );
                }
            }
            preferences.removePushServerUrl();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onMessage(@NotNull Context context, @NotNull byte[] message, @NotNull String instance) {
        AndroidInjection.inject(this, context);
        String sMessage = ("["+java.net.URLDecoder.decode(new String(message)).replaceAll("&?notifications\\[[0-9]*\\]=", ",")+"]").replaceFirst(",", "");
        Gson gb = new Gson();
        List<Map<String, String>> l = gb.fromJson(sMessage, List.class);
        for (Map<String, String>  data : l) {
            final String subject = data.get(NotificationWork.KEY_NOTIFICATION_SUBJECT);
            final String signature = data.get(NotificationWork.KEY_NOTIFICATION_SIGNATURE);
            if (subject != null && signature != null) {
                backgroundJobManager.startNotificationJob(subject, signature);
            }
        }
    }
}

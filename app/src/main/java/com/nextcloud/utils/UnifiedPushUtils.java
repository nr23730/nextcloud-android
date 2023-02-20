/*
 * Nextcloud Android client application
 *
 * @author Niklas Reimer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.utils;

import android.content.Context;

import com.google.gson.Gson;
import com.nextcloud.client.account.UserAccountManager;
import com.nextcloud.client.jobs.BackgroundJobManager;
import com.nextcloud.client.jobs.NotificationWork;
import com.nextcloud.client.preferences.AppPreferences;
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
        PushUtils.unregister(context, accountManager, preferences);
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

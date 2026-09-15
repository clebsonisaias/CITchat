/*
 * This file is a part of CITchat, a modified version of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.thunderdog.challegram.core;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bundled strings and the strings downloaded from the Telegram X translation platform
 * (translations.telegram.org/.../android_x) call the app "Telegram X". Forks built with a
 * different {@code app.name} replace that name with {@link BuildConfig#PROJECT_NAME}.
 *
 * <p>Plain "Telegram" is only replaced in the strings listed in {@link #APP_NAME_KEYS}, where it
 * means this app. Everywhere else it refers to the Telegram service (accounts, servers, Premium,
 * support, terms, contacts using Telegram) and must stay, so users can tell what belongs to Telegram.
 */
public final class Branding {
  private static final String UPSTREAM_NAME = "Telegram X";
  private static final String SERVICE_NAME = "Telegram";
  private static final boolean ENABLED = !BuildConfig.PROJECT_NAME.contains(SERVICE_NAME);

  private static final Set<String> APP_NAME_KEYS = new HashSet<>(Arrays.asList(
    // Intro pages and demo chat
    "Page2Message", "Page3Message", "Page4Message", "Page5Message", "Page6Message",
    "json_3_text1", "json_3_text2",
    // Contacts sync prompt
    "SyncHintTitle2",
    // Calls
    "VoipBranding", "VoipInCallBranding", "CallBrandingIncoming", "VoipRateCallAlert",
    // Notifications, settings and local data
    "NotificationsGuideBlockedApp", "NotificationsGuideBlockedAll", "NotificationsGuidePermission",
    "ChangePasscodeInfo", "OptimizingInfo", "ApplicationFolderWarning", "ProxySponsorAlert",
    // Login by phone call is handled by the app
    "SentCallOnly",
    // Invitations use the app download link
    "NoChatsText",
    "InviteTextCommonMany", "InviteTextCommonMany_one", "InviteTextCommonMany_other", "InviteTextCommonOverThousand"
  ));

  // "Telegram" as a word, not inside a link, an e-mail address or a username (telegram.org, @telegram)
  private static final Pattern SERVICE_WORD = Pattern.compile("(?<![\\w@/.])" + SERVICE_NAME + "(?!\\w|\\.(?:org|me|dog|ph)\\b)");

  private Branding () { }

  // Telegram X community chats mentioned in help texts, and what the fork shows instead
  private static final String UPSTREAM_UPDATES_LINK = "https://t.me/tgx_log";
  private static final String UPSTREAM_UPDATES_CHANNEL = "@tgx_log";
  private static final String UPSTREAM_SUPPORT_CHAT = "@tgandroidtests";

  public static String apply (String value) {
    if (!ENABLED || value == null) {
      return value;
    }
    if (value.contains(UPSTREAM_NAME)) {
      value = value.replace(UPSTREAM_NAME, BuildConfig.PROJECT_NAME);
    }
    if (value.contains("tgx_log")) {
      value = value.replace(UPSTREAM_UPDATES_LINK, BuildConfig.DOWNLOAD_URL).replace(UPSTREAM_UPDATES_CHANNEL, BuildConfig.DOWNLOAD_URL);
    }
    if (value.contains(UPSTREAM_SUPPORT_CHAT)) {
      value = value.replace(UPSTREAM_SUPPORT_CHAT, BuildConfig.REMOTE_URL + "/issues");
    }
    return value;
  }

  /** True when the string may need branding, so the caller should look up its key and call {@link #apply(String, String)}. */
  public static boolean needsKey (@Nullable String value) {
    return ENABLED && value != null && (value.contains(SERVICE_NAME) || value.contains("tgx_log") || value.contains(UPSTREAM_SUPPORT_CHAT));
  }

  public static String apply (@Nullable String key, String value) {
    value = apply(value);
    if (ENABLED && key != null && value != null && APP_NAME_KEYS.contains(key) && value.contains(SERVICE_NAME)) {
      value = SERVICE_WORD.matcher(value).replaceAll(Matcher.quoteReplacement(BuildConfig.PROJECT_NAME));
    }
    return value;
  }

  public static TdApi.LanguagePackString apply (TdApi.LanguagePackString string) {
    if (string.value instanceof TdApi.LanguagePackStringValueOrdinary) {
      apply(string.key, (TdApi.LanguagePackStringValueOrdinary) string.value);
    } else if (string.value instanceof TdApi.LanguagePackStringValuePluralized) {
      apply(string.key, (TdApi.LanguagePackStringValuePluralized) string.value);
    }
    return string;
  }

  public static void apply (@Nullable String key, @Nullable TdApi.LanguagePackStringValueOrdinary string) {
    if (string != null) {
      string.value = apply(key, string.value);
    }
  }

  public static void apply (@Nullable String key, @Nullable TdApi.LanguagePackStringValuePluralized string) {
    if (string != null) {
      string.zeroValue = apply(key, string.zeroValue);
      string.oneValue = apply(key, string.oneValue);
      string.twoValue = apply(key, string.twoValue);
      string.fewValue = apply(key, string.fewValue);
      string.manyValue = apply(key, string.manyValue);
      string.otherValue = apply(key, string.otherValue);
    }
  }
}

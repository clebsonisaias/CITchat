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
package org.thunderdog.challegram.citchat;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.U;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.sync.TemporaryNotification;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.telegram.TdlibFilesManager;
import org.thunderdog.challegram.tool.Strings;
import org.thunderdog.challegram.voip.annotation.DataSavingOption;

import java.util.Calendar;

/**
 * Internet saving for people on metered mobile plans.
 *
 * The ready-made modes only combine settings the app already has (Data Saver, automatic download limits
 * on mobile data, less data for calls), so any of them can still be adjusted one by one below.
 *
 * Usage comes from Android's own counters: CITchat's share is always available; the whole phone needs the
 * "usage access" the user grants in the system settings, and is only read to compare with the data cap.
 */
public final class DataSaving {
  private DataSaving () { }

  public static final int MODE_CUSTOM = 0, MODE_NORMAL = 1, MODE_VIDEO_ON_WIFI = 2, MODE_MAXIMUM = 3;

  private static final int VIDEO_ON_WIFI_EXCLUDE =
    TdlibFilesManager.DOWNLOAD_FLAG_VIDEO | TdlibFilesManager.DOWNLOAD_FLAG_VIDEO_NOTE | TdlibFilesManager.DOWNLOAD_FLAG_GIF |
    TdlibFilesManager.DOWNLOAD_FLAG_FILE | TdlibFilesManager.DOWNLOAD_FLAG_MUSIC;
  private static final int MAXIMUM_EXCLUDE = VIDEO_ON_WIFI_EXCLUDE | TdlibFilesManager.DOWNLOAD_FLAG_PHOTO;

  // ── Modes ───────────────────────────────────────────────────────────────

  /** Which mode the current settings match; MODE_CUSTOM once anything was changed by hand. */
  public static int currentMode (Tdlib tdlib) {
    TdlibFilesManager files = tdlib.files();
    if (files.isDataSaverAlwaysEnabled() || !files.isDataSaverEnabledOverRoaming()) {
      return MODE_CUSTOM;
    }
    int exclude = files.getExcludeOverMobile();
    int limit = files.getDownloadLimitOverMobile();
    int calls = files.getVoipDataSavingOption();
    if (files.isDataSaverEnabledOverMobile()) {
      return exclude == MAXIMUM_EXCLUDE && limit == TdlibFilesManager.DOWNLOAD_LIMIT_1MB && calls == DataSavingOption.MOBILE ? MODE_MAXIMUM : MODE_CUSTOM;
    }
    if (calls != DataSavingOption.ROAMING) {
      return MODE_CUSTOM;
    }
    if (exclude == VIDEO_ON_WIFI_EXCLUDE && limit == TdlibFilesManager.DOWNLOAD_LIMIT_5MB) {
      return MODE_VIDEO_ON_WIFI;
    }
    if (exclude == 0 && limit == TdlibFilesManager.DOWNLOAD_LIMIT_15MB) {
      return MODE_NORMAL;
    }
    return MODE_CUSTOM;
  }

  public static void applyMode (Tdlib tdlib, int mode) {
    TdlibFilesManager files = tdlib.files();
    switch (mode) {
      case MODE_NORMAL:
        files.setDataSaverEnabled(false);
        files.setDataSaverForcedOptions(false, true);
        files.setLimitsOverMobile(0, TdlibFilesManager.DOWNLOAD_LIMIT_15MB);
        files.setVoipDataSavingOption(DataSavingOption.ROAMING);
        break;
      case MODE_VIDEO_ON_WIFI:
        // Photos and voice messages still arrive on mobile data; anything heavy waits for Wi-Fi or a tap.
        files.setDataSaverEnabled(false);
        files.setDataSaverForcedOptions(false, true);
        files.setLimitsOverMobile(VIDEO_ON_WIFI_EXCLUDE, TdlibFilesManager.DOWNLOAD_LIMIT_5MB);
        files.setVoipDataSavingOption(DataSavingOption.ROAMING);
        break;
      case MODE_MAXIMUM:
        // Data Saver on mobile data stops every automatic download there; calls use less data too.
        files.setDataSaverEnabled(false);
        files.setDataSaverForcedOptions(true, true);
        files.setLimitsOverMobile(MAXIMUM_EXCLUDE, TdlibFilesManager.DOWNLOAD_LIMIT_1MB);
        files.setVoipDataSavingOption(DataSavingOption.MOBILE);
        break;
    }
  }

  public static @StringRes int modeName (int mode) {
    switch (mode) {
      case MODE_NORMAL:
        return R.string.CITchatDataModeNormal;
      case MODE_VIDEO_ON_WIFI:
        return R.string.CITchatDataModeVideoWifi;
      case MODE_MAXIMUM:
        return R.string.CITchatDataModeMaximum;
      default:
        return R.string.CITchatDataModeCustom;
    }
  }

  // ── Data cap ────────────────────────────────────────────────────────────

  private static final String KEY_CAP_MB = "citchat_data_cap_mb";
  private static final String KEY_CAP_DAY = "citchat_data_cap_day";
  private static final String KEY_WARNED_CYCLE = "citchat_data_cap_warned_cycle";
  private static final String KEY_WARNED_LEVEL = "citchat_data_cap_warned_level";

  /** The plan's data allowance in megabytes; 0 when not set. */
  public static long capMegabytes () {
    return org.thunderdog.challegram.unsorted.Settings.instance().getLong(KEY_CAP_MB, 0);
  }

  /** Day of the month the allowance renews (1-28). */
  public static int capRenewalDay () {
    return org.thunderdog.challegram.unsorted.Settings.instance().getInt(KEY_CAP_DAY, 1);
  }

  public static void setCap (long megabytes, int renewalDay) {
    org.thunderdog.challegram.unsorted.Settings prefs = org.thunderdog.challegram.unsorted.Settings.instance();
    prefs.putLong(KEY_CAP_MB, megabytes);
    prefs.putInt(KEY_CAP_DAY, Math.max(1, Math.min(28, renewalDay)));
    prefs.remove(KEY_WARNED_CYCLE);
    prefs.remove(KEY_WARNED_LEVEL);
  }

  /** "20", "20 GB", "2,5", "500 MB" → megabytes; -1 when it is not a size. */
  public static long parseCapMegabytes (@Nullable String input) {
    if (input == null) {
      return -1;
    }
    String value = input.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "");
    boolean megabytes = value.endsWith("mb");
    value = value.replace("gb", "").replace("mb", "").replace(',', '.');
    try {
      double number = Double.parseDouble(value);
      long result = Math.round(megabytes ? number : number * 1024);
      return result > 0 && result <= 10_000_000 ? result : -1;
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  public static String formatCap (long megabytes) {
    return Strings.buildSize(megabytes * 1024L * 1024L);
  }

  /** Midnight of the last renewal day, so usage is counted per billing cycle, not per calendar month. */
  static long cycleStart (int renewalDay, Calendar now) {
    Calendar start = (Calendar) now.clone();
    start.set(Calendar.HOUR_OF_DAY, 0);
    start.set(Calendar.MINUTE, 0);
    start.set(Calendar.SECOND, 0);
    start.set(Calendar.MILLISECOND, 0);
    if (start.get(Calendar.DAY_OF_MONTH) < renewalDay) {
      start.add(Calendar.MONTH, -1);
    }
    start.set(Calendar.DAY_OF_MONTH, renewalDay);
    return start.getTimeInMillis();
  }

  // ── Usage ───────────────────────────────────────────────────────────────

  public static final class Usage {
    /** Mobile data CITchat used in the current cycle; -1 when Android didn't answer. */
    public final long appBytes;
    /** Mobile data the whole phone used in the current cycle; -1 without usage access. */
    public final long deviceBytes;
    public final long cycleStart;

    Usage (long appBytes, long deviceBytes, long cycleStart) {
      this.appBytes = appBytes;
      this.deviceBytes = deviceBytes;
      this.cycleStart = cycleStart;
    }
  }

  /** Reads the counters. Slow-ish (a system service call), so call it off the main thread. */
  public static Usage readUsage (Context context) {
    long start = cycleStart(capRenewalDay(), Calendar.getInstance());
    long end = System.currentTimeMillis();
    NetworkStatsManager manager = (NetworkStatsManager) context.getSystemService(Context.NETWORK_STATS_SERVICE);
    long appBytes = -1, deviceBytes = -1;
    if (manager != null) {
      // Any app may read its own traffic; no permission involved.
      NetworkStats stats = null;
      try {
        stats = manager.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, start, end, Process.myUid());
        NetworkStats.Bucket bucket = new NetworkStats.Bucket();
        long total = 0;
        while (stats.hasNextBucket()) {
          stats.getNextBucket(bucket);
          total += bucket.getRxBytes() + bucket.getTxBytes();
        }
        appBytes = total;
      } catch (Throwable ignored) {
        appBytes = -1;
      } finally {
        if (stats != null) {
          stats.close();
        }
      }
      if (hasUsageAccess(context)) {
        try {
          NetworkStats.Bucket bucket = manager.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, end);
          deviceBytes = bucket != null ? bucket.getRxBytes() + bucket.getTxBytes() : -1;
        } catch (Throwable ignored) {
          deviceBytes = -1;
        }
      }
    }
    return new Usage(appBytes, deviceBytes, start);
  }

  public static boolean hasUsageAccess (Context context) {
    AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
    if (appOps == null) {
      return false;
    }
    int mode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
      appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName()) :
      appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
    return mode == AppOpsManager.MODE_ALLOWED;
  }

  public static void openUsageAccessSettings (Context context) {
    try {
      context.startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (Throwable ignored) { }
  }

  public static String describeUsage (Usage usage) {
    long cap = capMegabytes();
    if (usage.deviceBytes >= 0 && cap > 0) {
      long capBytes = cap * 1024L * 1024L;
      int percent = (int) Math.min(999, usage.deviceBytes * 100 / capBytes);
      return Lang.getString(R.string.CITchatDataUsagePhone, Strings.buildSize(usage.deviceBytes), formatCap(cap), percent,
        usage.appBytes >= 0 ? Strings.buildSize(usage.appBytes) : "—");
    }
    if (usage.appBytes >= 0) {
      return Lang.getString(R.string.CITchatDataUsageApp, Strings.buildSize(usage.appBytes));
    }
    return Lang.getString(R.string.CITchatDataUsageUnavailable);
  }

  // ── Warning ─────────────────────────────────────────────────────────────

  private static long lastCheck;

  /**
   * Warns once per level (80%, 90%, 100%) per billing cycle. Called when the app comes to the front, at most
   * once an hour; does nothing without a data cap or usage access.
   */
  public static void checkCap (Context context) {
    long now = System.currentTimeMillis();
    if (capMegabytes() <= 0 || now - lastCheck < 60 * 60 * 1000L || !hasUsageAccess(context)) {
      return;
    }
    lastCheck = now;
    Context app = context.getApplicationContext();
    new Thread(() -> {
      Usage usage = readUsage(app);
      long capBytes = capMegabytes() * 1024L * 1024L;
      if (usage.deviceBytes < 0 || capBytes <= 0) {
        return;
      }
      int level = usage.deviceBytes >= capBytes ? 100 : usage.deviceBytes * 10 >= capBytes * 9 ? 90 : usage.deviceBytes * 10 >= capBytes * 8 ? 80 : 0;
      org.thunderdog.challegram.unsorted.Settings prefs = org.thunderdog.challegram.unsorted.Settings.instance();
      if (level == 0 || (prefs.getLong(KEY_WARNED_CYCLE, 0) == usage.cycleStart && prefs.getInt(KEY_WARNED_LEVEL, 0) >= level)) {
        return;
      }
      prefs.putLong(KEY_WARNED_CYCLE, usage.cycleStart);
      prefs.putInt(KEY_WARNED_LEVEL, level);
      CharSequence title = Lang.getString(level >= 100 ? R.string.CITchatDataCapReachedTitle : R.string.CITchatDataCapTitle);
      CharSequence text = Lang.getString(R.string.CITchatDataCapText, level, Strings.buildSize(usage.deviceBytes), formatCap(capMegabytes()));
      TemporaryNotification.showNotification(app, U.getOtherNotificationChannel(), title, text);
    }, "CITchatDataCap").start();
  }
}

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

import org.thunderdog.challegram.unsorted.Settings;

import java.util.Calendar;

/**
 * CITmax's own ad for CITmóvel, shown as a row at the top of the main chat list.
 *
 * Deliberately limited:
 * - no ad network and no network request: the content ships with the app;
 * - nothing about the user's chats is read to decide whether or what to show;
 * - at most one app session per day, and "Hide" removes it until the next day.
 *
 * Telegram's API terms (3.2) require every way the app makes money to be listed in the store description.
 */
public final class HouseAd {
  public static final String URL = "https://citmax.com.br/citmovel";

  /** Change it whenever the ad content changes: a new campaign is shown even where the old one was already shown or hidden today. */
  private static final String CAMPAIGN = "citmovel-25gb-202609";

  private static final String KEY_SHOWN_DAY = "citchat_house_ad_shown_day_" + CAMPAIGN;
  private static final String KEY_HIDDEN_DAY = "citchat_house_ad_hidden_day_" + CAMPAIGN;

  private static boolean loaded;
  private static long shownDay, hiddenDay;
  /** Day on which this process already showed the ad, so it stays visible for the rest of the session. */
  private static long shownInProcessDay = -1;

  private HouseAd () { }

  /** Called whenever the chat list changes, so it only touches the database once per process. */
  public static boolean shouldShow () {
    load();
    long today = today();
    if (hiddenDay == today) {
      return false;
    }
    return shownInProcessDay == today || shownDay != today;
  }

  public static void markShown () {
    long today = today();
    if (shownInProcessDay != today) {
      load();
      shownInProcessDay = shownDay = today;
      Settings.instance().pmc().putLong(KEY_SHOWN_DAY, today);
    }
  }

  public static void hideForToday () {
    load();
    hiddenDay = today();
    Settings.instance().pmc().putLong(KEY_HIDDEN_DAY, hiddenDay);
  }

  private static void load () {
    if (!loaded) {
      loaded = true;
      shownDay = Settings.instance().pmc().getLong(KEY_SHOWN_DAY, -1);
      hiddenDay = Settings.instance().pmc().getLong(KEY_HIDDEN_DAY, -1);
    }
  }

  private static long today () {
    Calendar calendar = Calendar.getInstance();
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
  }
}

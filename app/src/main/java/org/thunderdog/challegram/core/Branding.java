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

/**
 * Bundled strings and the strings downloaded from the Telegram X translation platform
 * (translations.telegram.org/.../android_x) call the app "Telegram X". Forks built with a
 * different {@code app.name} replace that name with {@link BuildConfig#PROJECT_NAME}.
 */
public final class Branding {
  private static final String UPSTREAM_NAME = "Telegram X";
  private static final boolean ENABLED = !BuildConfig.PROJECT_NAME.contains(UPSTREAM_NAME);

  private Branding () { }

  public static String apply (String value) {
    if (ENABLED && value != null && value.contains(UPSTREAM_NAME)) {
      return value.replace(UPSTREAM_NAME, BuildConfig.PROJECT_NAME);
    }
    return value;
  }

  public static TdApi.LanguagePackString apply (TdApi.LanguagePackString string) {
    if (string.value instanceof TdApi.LanguagePackStringValueOrdinary) {
      apply((TdApi.LanguagePackStringValueOrdinary) string.value);
    } else if (string.value instanceof TdApi.LanguagePackStringValuePluralized) {
      apply((TdApi.LanguagePackStringValuePluralized) string.value);
    }
    return string;
  }

  public static void apply (@Nullable TdApi.LanguagePackStringValueOrdinary string) {
    if (string != null) {
      string.value = apply(string.value);
    }
  }

  public static void apply (@Nullable TdApi.LanguagePackStringValuePluralized string) {
    if (string != null) {
      string.zeroValue = apply(string.zeroValue);
      string.oneValue = apply(string.oneValue);
      string.twoValue = apply(string.twoValue);
      string.fewValue = apply(string.fewValue);
      string.manyValue = apply(string.manyValue);
      string.otherValue = apply(string.otherValue);
    }
  }
}

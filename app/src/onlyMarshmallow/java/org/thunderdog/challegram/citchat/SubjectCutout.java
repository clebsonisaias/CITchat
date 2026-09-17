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

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.Nullable;

/**
 * Stub for flavors below minSdk 24, where ML Kit subject segmentation is not available. The "latest"
 * flavor has the real implementation in src/onlyLatest.
 */
public final class SubjectCutout {
  private SubjectCutout () { }

  public interface Callback {
    void onResult (@Nullable Bitmap foreground, @Nullable Throwable error, boolean preparing);
  }

  public static boolean isAvailable (Context context) {
    return false;
  }

  public static void cutOut (Context context, Bitmap photo, Callback callback) {
    callback.onResult(null, new UnsupportedOperationException(), false);
  }
}

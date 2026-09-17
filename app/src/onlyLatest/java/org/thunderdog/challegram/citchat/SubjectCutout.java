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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

/**
 * Cuts the main subject out of a photo with ML Kit subject segmentation. Runs on the device; the model is
 * an optional Google Play services module that is downloaded the first time it is needed.
 *
 * Only in the "latest" flavor (minSdk 24, which the library requires). Older flavors have a stub that
 * reports the feature as unavailable, so the option never shows up there.
 */
public final class SubjectCutout {
  private SubjectCutout () { }

  public interface Callback {
    /** @param preparing true while Google Play services is still downloading the model: try again soon. */
    void onResult (@Nullable Bitmap foreground, @Nullable Throwable error, boolean preparing);
  }

  public static boolean isAvailable (Context context) {
    try {
      return context != null && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
    } catch (Throwable t) {
      return false;
    }
  }

  public static void cutOut (Context context, Bitmap photo, Callback callback) {
    SubjectSegmenter segmenter;
    try {
      segmenter = SubjectSegmentation.getClient(new SubjectSegmenterOptions.Builder().enableForegroundBitmap().build());
    } catch (Throwable t) {
      callback.onResult(null, t, false);
      return;
    }
    segmenter.process(InputImage.fromBitmap(photo, 0))
      .addOnSuccessListener(result -> {
        callback.onResult(result.getForegroundBitmap(), null, false);
        segmenter.close();
      })
      .addOnFailureListener(error -> {
        boolean preparing = error instanceof MlKitException && ((MlKitException) error).getErrorCode() == MlKitException.UNAVAILABLE;
        if (preparing) {
          // Asks Play services for the module now, so the next attempt finds it ready.
          ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build())
            .addOnCompleteListener(task -> segmenter.close());
        } else {
          segmenter.close();
        }
        callback.onResult(null, error, preparing);
      });
  }
}

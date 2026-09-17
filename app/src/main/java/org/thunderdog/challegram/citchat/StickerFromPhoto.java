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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.core.Media;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;

import java.io.File;
import java.io.FileOutputStream;

import tgx.td.Td;

/**
 * A sticker from a photo: the subject is cut out on the device (Google ML Kit subject segmentation, through
 * Google Play services), gets a white outline and is sent as a 512 px WebP sticker after a preview.
 */
public final class StickerFromPhoto {
  private StickerFromPhoto () { }

  private static final int REQUEST_PICK_PHOTO = 0x5C17;
  private static final int STICKER_SIZE = 512;
  private static final int INPUT_MAX_SIZE = 1280;
  private static final int OUTLINE = 12;

  public static boolean isSupported () {
    return SubjectCutout.isAvailable(UI.getAppContext());
  }

  public static boolean canUse (@Nullable TdApi.Message message) {
    return message != null && message.content != null && message.selfDestructType == null &&
      message.content.getConstructor() == TdApi.MessagePhoto.CONSTRUCTOR && isSupported();
  }

  // ── Sources ─────────────────────────────────────────────────────────────

  public static void fromMessage (MessagesController c, TdApi.Message message) {
    if (!canUse(message)) {
      return;
    }
    TdApi.PhotoSize size = Td.findBiggest(((TdApi.MessagePhoto) message.content).photo.sizes);
    if (size == null) {
      return;
    }
    UI.showToast(R.string.CITchatStickerWorking, Toast.LENGTH_SHORT);
    c.tdlib().client().send(new TdApi.DownloadFile(size.photo.id, 32, 0, 0, true), result -> {
      if (result.getConstructor() != TdApi.File.CONSTRUCTOR || !((TdApi.File) result).local.isDownloadingCompleted) {
        UI.showToast(R.string.CITchatStickerFailed, Toast.LENGTH_SHORT);
        return;
      }
      String path = ((TdApi.File) result).local.path;
      Media.instance().post(() -> process(c, decodeFile(path)));
    });
  }

  public static void pickFromGallery (MessagesController c) {
    Intent intent = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ?
      new Intent(MediaStore.ACTION_PICK_IMAGES) :
      new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
    c.context().putActivityResultHandler(REQUEST_PICK_PHOTO, (requestCode, resultCode, data) -> {
      Uri uri = resultCode == Activity.RESULT_OK && data != null ? data.getData() : null;
      if (uri != null) {
        UI.showToast(R.string.CITchatStickerWorking, Toast.LENGTH_SHORT);
        Context context = c.context();
        Media.instance().post(() -> process(c, decodeUri(context, uri)));
      }
    });
    try {
      UI.startActivityForResult(intent, REQUEST_PICK_PHOTO);
    } catch (Throwable t) {
      Log.e("Unable to open the photo picker", t);
      UI.showToast(R.string.CITchatStickerFailed, Toast.LENGTH_SHORT);
    }
  }

  // ── Pipeline ────────────────────────────────────────────────────────────

  private static void process (MessagesController c, @Nullable Bitmap photo) {
    if (photo == null) {
      UI.showToast(R.string.CITchatStickerFailed, Toast.LENGTH_SHORT);
      return;
    }
    UI.post(() -> SubjectCutout.cutOut(c.context(), photo, (foreground, error, preparing) -> {
      if (foreground == null) {
        if (error != null) {
          Log.e("Subject segmentation failed", error);
        }
        UI.showToast(preparing ? R.string.CITchatStickerPreparing : R.string.CITchatStickerNoSubject, Toast.LENGTH_LONG);
        return;
      }
      Media.instance().post(() -> {
        File file = null;
        Bitmap sticker = null;
        try {
          sticker = makeSticker(foreground);
          file = sticker != null ? save(c.context(), sticker) : null;
        } catch (Throwable t) {
          Log.e("Unable to build sticker", t);
        }
        if (file == null || sticker == null) {
          UI.showToast(sticker == null ? R.string.CITchatStickerNoSubject : R.string.CITchatStickerFailed, Toast.LENGTH_LONG);
          return;
        }
        Bitmap preview = sticker;
        String path = file.getPath();
        c.runOnUiThreadOptional(() -> showPreview(c, preview, path));
      });
    }));
  }

  private static void showPreview (MessagesController c, Bitmap sticker, String path) {
    int padding = Screen.dp(16f);
    ImageView image = new ImageView(c.context());
    image.setImageBitmap(sticker);
    image.setAdjustViewBounds(true);
    image.setMaxHeight(Screen.dp(220f));
    image.setBackground(checkerboard());
    FrameLayout container = new FrameLayout(c.context());
    container.setPadding(padding, padding, padding, 0);
    container.addView(image, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
    AlertDialog.Builder builder = new AlertDialog.Builder(c.context(), Theme.dialogTheme())
      .setTitle(Lang.getString(R.string.CITchatStickerPreviewTitle))
      .setView(container)
      .setPositiveButton(Lang.getString(R.string.CITchatStickerSend), (dialog, which) -> c.sendStickerFile(path))
      .setNegativeButton(Lang.getString(R.string.Cancel), (dialog, which) -> dialog.dismiss());
    c.showAlert(builder);
  }

  /** Gray checkerboard, so the transparent background and the white outline are both visible. */
  private static BitmapDrawable checkerboard () {
    int cell = Screen.dp(8f);
    Bitmap tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(tile);
    canvas.drawColor(0xFFB0B8BC);
    Paint dark = new Paint();
    dark.setColor(0xFF8D979C);
    canvas.drawRect(0, 0, cell, cell, dark);
    canvas.drawRect(cell, cell, cell * 2, cell * 2, dark);
    BitmapDrawable drawable = new BitmapDrawable(UI.getAppContext().getResources(), tile);
    drawable.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    return drawable;
  }

  /** Crops to the subject, fits it in 512 px with room for a white outline, and draws the outline. */
  @Nullable
  static Bitmap makeSticker (Bitmap foreground) {
    Rect bounds = opaqueBounds(foreground);
    if (bounds == null || bounds.width() < 8 || bounds.height() < 8) {
      return null;
    }
    int margin = OUTLINE + 2;
    int inner = STICKER_SIZE - margin * 2;
    float scale = Math.min((float) inner / bounds.width(), (float) inner / bounds.height());
    int width = Math.max(1, Math.round(bounds.width() * scale));
    int height = Math.max(1, Math.round(bounds.height() * scale));

    Bitmap subject = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    new Canvas(subject).drawBitmap(foreground, bounds, new Rect(0, 0, width, height), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG));

    Bitmap sticker = Bitmap.createBitmap(width + margin * 2, height + margin * 2, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(sticker);
    Paint outline = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    outline.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    // The silhouette in white, shifted around two circles, makes a solid outline without gaps.
    for (int radius : new int[] {OUTLINE / 2, OUTLINE}) {
      for (int step = 0; step < 32; step++) {
        double angle = Math.PI * 2 * step / 32;
        canvas.drawBitmap(subject, margin + (float) (Math.cos(angle) * radius), margin + (float) (Math.sin(angle) * radius), outline);
      }
    }
    canvas.drawBitmap(subject, margin, margin, null);
    subject.recycle();

    int longest = Math.max(sticker.getWidth(), sticker.getHeight());
    if (longest != STICKER_SIZE) {
      float fix = (float) STICKER_SIZE / longest;
      Bitmap exact = Bitmap.createScaledBitmap(sticker,
        Math.min(STICKER_SIZE, Math.max(1, Math.round(sticker.getWidth() * fix))),
        Math.min(STICKER_SIZE, Math.max(1, Math.round(sticker.getHeight() * fix))), true);
      sticker.recycle();
      sticker = exact;
    }
    return sticker;
  }

  @Nullable
  private static Rect opaqueBounds (Bitmap bitmap) {
    int width = bitmap.getWidth(), height = bitmap.getHeight();
    int[] pixels = new int[width * height];
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
    int left = width, top = height, right = -1, bottom = -1;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        if ((pixels[y * width + x] >>> 24) > 24) {
          if (x < left) left = x;
          if (x > right) right = x;
          if (y < top) top = y;
          if (y > bottom) bottom = y;
        }
      }
    }
    return right < 0 ? null : new Rect(left, top, right + 1, bottom + 1);
  }

  @SuppressWarnings("deprecation")
  private static File save (Context context, Bitmap sticker) throws Exception {
    File dir = new File(context.getCacheDir(), "citchat");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IllegalStateException("Unable to create " + dir);
    }
    File file = new File(dir, "sticker-" + System.currentTimeMillis() + ".webp");
    Bitmap.CompressFormat format = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
    // Telegram refuses static stickers over 512 KB.
    for (int quality : new int[] {90, 75, 60}) {
      try (FileOutputStream out = new FileOutputStream(file)) {
        sticker.compress(format, quality, out);
      }
      if (file.length() <= 500 * 1024) {
        return file;
      }
    }
    return file;
  }

  // ── Decoding ────────────────────────────────────────────────────────────

  @Nullable
  private static Bitmap decodeFile (String path) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decode(ImageDecoder.createSource(new File(path)));
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, options);
      options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
      options.inJustDecodeBounds = false;
      return BitmapFactory.decodeFile(path, options);
    } catch (Throwable t) {
      Log.e("Unable to decode photo for sticker", t);
      return null;
    }
  }

  @Nullable
  private static Bitmap decodeUri (Context context, Uri uri) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return decode(ImageDecoder.createSource(context.getContentResolver(), uri));
      }
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
        BitmapFactory.decodeStream(in, null, options);
      }
      options.inSampleSize = sampleSize(options.outWidth, options.outHeight);
      options.inJustDecodeBounds = false;
      try (java.io.InputStream in = context.getContentResolver().openInputStream(uri)) {
        return BitmapFactory.decodeStream(in, null, options);
      }
    } catch (Throwable t) {
      Log.e("Unable to decode picked photo for sticker", t);
      return null;
    }
  }

  /** ImageDecoder applies the EXIF rotation; software memory so the pixels can be read. */
  @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
  private static Bitmap decode (ImageDecoder.Source source) throws java.io.IOException {
    return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
      decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
      int longest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
      if (longest > INPUT_MAX_SIZE) {
        float scale = (float) INPUT_MAX_SIZE / longest;
        decoder.setTargetSize(Math.max(1, Math.round(info.getSize().getWidth() * scale)), Math.max(1, Math.round(info.getSize().getHeight() * scale)));
      }
    });
  }

  private static int sampleSize (int width, int height) {
    int sample = 1;
    while (Math.max(width, height) / (sample * 2) >= INPUT_MAX_SIZE) {
      sample *= 2;
    }
    return sample;
  }
}

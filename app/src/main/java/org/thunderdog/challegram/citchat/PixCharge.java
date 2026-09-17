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
import android.text.InputType;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.Log;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import org.thunderdog.challegram.ui.MessagesController;
import org.thunderdog.challegram.unsorted.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.util.EnumMap;
import java.util.Map;

import me.vkryl.core.StringUtils;
import me.vkryl.core.lambda.RunnableData;
import tgx.td.Td;

/**
 * "Cobrar com Pix" and "Racha conta": the user types an amount, the app builds a static Pix code with the
 * user's own key and sends the QR plus the copy-paste code to the chat.
 *
 * No money goes through CITchat and no bank is contacted: the payer pastes the code in their bank app and
 * the transfer goes straight to the key. The key, name and city stay on the phone, per account.
 */
public final class PixCharge {
  private PixCharge () { }

  private static final String KEY_PIX_KEY = "citchat_pix_key_";
  private static final String KEY_PIX_NAME = "citchat_pix_name_";
  private static final String KEY_PIX_CITY = "citchat_pix_city_";

  private static final int QR_SIZE = 768;
  private static final long NEXT_DIALOG_DELAY = 250L;

  private static final class Profile {
    final PixCode.Key key;
    final String name, city;

    Profile (PixCode.Key key, String name, String city) {
      this.key = key;
      this.name = name;
      this.city = city;
    }
  }

  // ── Entry points ─────────────────────────────────────────────────────────

  public static void charge (MessagesController c) {
    withProfile(c, profile -> askAmount(c, profile));
  }

  public static void splitBill (MessagesController c) {
    withProfile(c, profile -> askTotal(c, profile));
  }

  // ── Receiver data ────────────────────────────────────────────────────────

  private static @Nullable Profile loadProfile (Tdlib tdlib) {
    PixCode.Key key = PixCode.parseKey(Settings.instance().getString(KEY_PIX_KEY + tdlib.id(), null));
    String name = Settings.instance().getString(KEY_PIX_NAME + tdlib.id(), null);
    String city = Settings.instance().getString(KEY_PIX_CITY + tdlib.id(), null);
    if (key == null || PixCode.receiverName(name).isEmpty() || PixCode.receiverCity(city).isEmpty()) {
      return null;
    }
    return new Profile(key, name, city);
  }

  private static void saveProfile (Tdlib tdlib, Profile profile) {
    Settings.instance().putString(KEY_PIX_KEY + tdlib.id(), profile.key.value);
    Settings.instance().putString(KEY_PIX_NAME + tdlib.id(), profile.name);
    Settings.instance().putString(KEY_PIX_CITY + tdlib.id(), profile.city);
  }

  private static void withProfile (MessagesController c, RunnableData<Profile> next) {
    Profile saved = loadProfile(c.tdlib());
    if (saved != null) {
      next.runWithData(saved);
    } else {
      editProfile(c, next);
    }
  }

  private static void later (MessagesController c, Runnable runnable) {
    c.runOnUiThreadOptional(runnable, null, NEXT_DIALOG_DELAY);
  }

  private static void editProfile (MessagesController c, RunnableData<Profile> next) {
    Profile current = loadProfile(c.tdlib());
    c.openInputAlert(Lang.getString(R.string.CITchatPixKeyTitle), Lang.getString(R.string.CITchatPixKeyHint), R.string.CITchatNext, R.string.Cancel,
      current != null ? PixCode.formatKey(current.key) : null, (inputView, result) -> {
        PixCode.Key key = PixCode.parseKey(result);
        if (key == null) {
          return false;
        }
        later(c, () -> askName(c, key, current, next));
        return true;
      }, false);
  }

  private static void askName (MessagesController c, PixCode.Key key, @Nullable Profile current, RunnableData<Profile> next) {
    String name = current != null ? current.name : null;
    if (StringUtils.isEmpty(name)) {
      TdApi.User me = c.tdlib().myUser();
      name = me != null ? (me.firstName + " " + me.lastName).trim() : null;
    }
    c.openInputAlert(Lang.getString(R.string.CITchatPixNameTitle), Lang.getString(R.string.CITchatPixNameHint), R.string.CITchatNext, R.string.Cancel, name, (inputView, result) -> {
      if (PixCode.receiverName(result).length() < 3) {
        return false;
      }
      String typedName = result.trim();
      later(c, () -> askCity(c, key, typedName, current, next));
      return true;
    }, false).getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
  }

  private static void askCity (MessagesController c, PixCode.Key key, String name, @Nullable Profile current, RunnableData<Profile> next) {
    c.openInputAlert(Lang.getString(R.string.CITchatPixCityTitle), Lang.getString(R.string.CITchatPixCityHint), R.string.CITchatNext, R.string.Cancel,
      current != null ? current.city : null, (inputView, result) -> {
        if (PixCode.receiverCity(result).isEmpty()) {
          return false;
        }
        Profile profile = new Profile(key, name, result.trim());
        saveProfile(c.tdlib(), profile);
        later(c, () -> next.runWithData(profile));
        return true;
      }, false).getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
  }

  // ── Charge ──────────────────────────────────────────────────────────────

  private static void askAmount (MessagesController c, Profile profile) {
    c.openInputAlert(Lang.getString(R.string.CITchatPixCharge), Lang.getString(R.string.CITchatPixAmountHint), R.string.CITchatNext, R.string.Cancel, null, (inputView, result) -> {
      long cents = PixCode.parseAmount(result);
      if (cents <= 0) {
        return false;
      }
      later(c, () -> confirmCharge(c, profile, cents));
      return true;
    }, true).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
  }

  private static void confirmCharge (MessagesController c, Profile profile, long cents) {
    String amount = PixCode.formatBrl(cents);
    String info = Lang.getString(R.string.CITchatPixChargeConfirm, amount, profile.name, PixCode.formatKey(profile.key));
    confirm(c, info, R.string.CITchatPixSendCharge, () ->
      send(c, profile, cents, Lang.getString(R.string.CITchatPixChargeCaption, amount, profile.name)),
      () -> editProfile(c, edited -> confirmCharge(c, edited, cents))
    );
  }

  // ── Split the bill ──────────────────────────────────────────────────────

  private static void askTotal (MessagesController c, Profile profile) {
    c.openInputAlert(Lang.getString(R.string.CITchatPixSplit), Lang.getString(R.string.CITchatPixTotalHint), R.string.CITchatNext, R.string.Cancel, null, (inputView, result) -> {
      long total = PixCode.parseAmount(result);
      if (total <= 0) {
        return false;
      }
      later(c, () -> askPeople(c, profile, total));
      return true;
    }, false).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
  }

  private static void askPeople (MessagesController c, Profile profile, long total) {
    int members = c.tdlib().chatMemberCount(c.getChatId());
    String suggestion = members >= 2 && members <= 100 ? String.valueOf(members) : null;
    c.openInputAlert(Lang.getString(R.string.CITchatPixSplit), Lang.getString(R.string.CITchatPixPeopleHint), R.string.CITchatNext, R.string.Cancel, suggestion, (inputView, result) -> {
      int people = StringUtils.parseInt(result.trim(), -1);
      if (people < 2 || people > 1000 || PixCode.shareOf(total, people) <= 0) {
        return false;
      }
      later(c, () -> confirmSplit(c, profile, total, people));
      return true;
    }, true).getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
  }

  private static void confirmSplit (MessagesController c, Profile profile, long total, int people) {
    long share = PixCode.shareOf(total, people);
    String totalText = PixCode.formatBrl(total), shareText = PixCode.formatBrl(share);
    String info = Lang.getString(R.string.CITchatPixSplitConfirm, totalText, people, shareText, profile.name, PixCode.formatKey(profile.key));
    confirm(c, info, R.string.CITchatPixSendSplit, () ->
      send(c, profile, share, Lang.getString(R.string.CITchatPixSplitCaption, totalText, people, shareText, profile.name)),
      () -> editProfile(c, edited -> confirmSplit(c, edited, total, people))
    );
  }

  // ── Sending ─────────────────────────────────────────────────────────────

  private static void confirm (MessagesController c, String info, int sendText, Runnable onSend, Runnable onEdit) {
    c.showOptions(info,
      new int[] {R.id.btn_citchatPixSend, R.id.btn_citchatPixEdit, R.id.btn_cancel},
      new String[] {Lang.getString(sendText), Lang.getString(R.string.CITchatPixEditData), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.BLUE, ViewController.OptionColor.NORMAL, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_send_24, R.drawable.baseline_edit_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_citchatPixSend) {
          onSend.run();
        } else if (id == R.id.btn_citchatPixEdit) {
          later(c, onEdit);
        }
        return true;
      });
  }

  /** The QR as a photo with a short explanation, then the code alone, so it can be copied as is. */
  private static void send (MessagesController c, Profile profile, long cents, String caption) {
    String code;
    File qr;
    try {
      code = PixCode.build(profile.key, cents, profile.name, profile.city, null);
      qr = writeQr(c.context(), code);
    } catch (Throwable t) {
      Log.e("Unable to build Pix code", t);
      UI.showToast(R.string.CITchatPixFailed, Toast.LENGTH_SHORT);
      return;
    }
    TdApi.InputMessagePhoto photo = new TdApi.InputMessagePhoto(
      new TdApi.InputPhoto(new TdApi.InputFileLocal(qr.getPath()), null, null, null, QR_SIZE, QR_SIZE),
      new TdApi.FormattedText(caption, new TdApi.TextEntity[0]), false, null, false
    );
    TdApi.InputMessageText codeMessage = new TdApi.InputMessageText(
      new TdApi.FormattedText(code, new TdApi.TextEntity[] {new TdApi.TextEntity(0, code.length(), new TdApi.TextEntityTypeCode())}),
      null, false
    );
    CharSequence restriction = c.tdlib().getRestrictionText(c.getChat(), photo);
    if (restriction == null) {
      restriction = c.tdlib().getRestrictionText(c.getChat(), codeMessage);
    }
    if (restriction != null) {
      UI.showToast(restriction, Toast.LENGTH_LONG);
      return;
    }
    c.send(photo, true, Td.newSendOptions(), sent ->
      c.runOnUiThreadOptional(() -> c.send(codeMessage, false, Td.newSendOptions(), null))
    );
  }

  private static File writeQr (Context context, String code) throws Exception {
    Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
    hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
    hints.put(EncodeHintType.MARGIN, 4);
    hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    BitMatrix matrix = new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
    int width = matrix.getWidth(), height = matrix.getHeight();
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        pixels[y * width + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
      }
    }
    Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    File dir = new File(context.getCacheDir(), "citchat");
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IllegalStateException("Unable to create " + dir);
    }
    File file = new File(dir, "pix-" + System.currentTimeMillis() + ".png");
    try (FileOutputStream out = new FileOutputStream(file)) {
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
    } finally {
      bitmap.recycle();
    }
    return file;
  }
}

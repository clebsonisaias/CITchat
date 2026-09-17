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

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.tool.UI;

import tgx.td.Td;

/** Pix and boleto codes found in chat messages: what the copy button says and does. */
public final class PixMessages {
  private PixMessages () { }

  /** First code in the text or caption of a message. */
  public static @Nullable PixCode.Found find (@Nullable TdApi.Message message) {
    if (message == null || message.content == null) {
      return null;
    }
    TdApi.FormattedText text = Td.textOrCaption(message.content);
    return text != null ? PixCode.findFirst(text.text) : null;
  }

  /** "Copiar Pix · R$ 30,00" — the amount helps tell two codes in the same chat apart. */
  public static String buttonText (PixCode.Found found) {
    String label = Lang.getString(optionText(found));
    return found.amountCents > 0 ? label + " · " + PixCode.formatBrl(found.amountCents) : label;
  }

  public static @StringRes int optionText (PixCode.Found found) {
    return found.type == PixCode.TYPE_PIX ? R.string.CITchatCopyPix : R.string.CITchatCopyBoleto;
  }

  public static void copy (PixCode.Found found) {
    UI.copyText(found.code, found.type == PixCode.TYPE_PIX ? R.string.CITchatPixCopied : R.string.CITchatBoletoCopied);
  }
}

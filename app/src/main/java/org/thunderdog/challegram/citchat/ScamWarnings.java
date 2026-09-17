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

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.TdlibDelegate;
import org.thunderdog.challegram.telegram.TdlibUi;
import org.thunderdog.challegram.tool.UI;

import me.vkryl.core.lambda.RunnableBool;
import me.vkryl.core.lambda.RunnableData;

/** What the scam warnings say, and the prompt before opening a risky link. */
public final class ScamWarnings {
  private ScamWarnings () { }

  /** Bold first sentence, then the advice. */
  public static TdApi.FormattedText messageWarning (int reason) {
    String title = Lang.getString(reason == ScamGuard.MESSAGE_VERIFICATION_CODE ? R.string.CITchatScamCodeTitle : R.string.CITchatScamTitle);
    int body;
    switch (reason) {
      case ScamGuard.MESSAGE_VERIFICATION_CODE:
        body = R.string.CITchatScamCode;
        break;
      case ScamGuard.MESSAGE_NEW_NUMBER:
        body = R.string.CITchatScamNewNumber;
        break;
      default:
        body = R.string.CITchatScamMoney;
        break;
    }
    String text = title + " " + Lang.getString(body);
    return new TdApi.FormattedText(text, new TdApi.TextEntity[] {
      new TdApi.TextEntity(0, title.length(), new TdApi.TextEntityTypeBold())
    });
  }

  private static String linkReason (ScamGuard.LinkRisk risk) {
    switch (risk.reason) {
      case ScamGuard.LINK_LOOKALIKE:
        return Lang.getString(R.string.CITchatLinkLookalike, risk.imitated, risk.host);
      case ScamGuard.LINK_PUNYCODE:
        return Lang.getString(R.string.CITchatLinkPunycode, risk.host);
      case ScamGuard.LINK_HIDDEN_HOST:
        return Lang.getString(R.string.CITchatLinkHiddenHost, risk.host);
      case ScamGuard.LINK_IP_ADDRESS:
        return Lang.getString(R.string.CITchatLinkIpAddress, risk.host);
      case ScamGuard.LINK_SCAM_WORDS:
        return Lang.getString(R.string.CITchatLinkScamWords, risk.host);
      case ScamGuard.LINK_SHORTENER:
      default:
        return Lang.getString(R.string.CITchatLinkShortener, risk.host);
    }
  }

  /**
   * Shows the warning when the link looks risky and returns true; the link is then only opened through
   * {@code proceed}, with options marked so it is not checked a second time.
   */
  public static boolean confirmLink (TdlibDelegate context, String url, @Nullable TdlibUi.UrlOpenParameters options, @Nullable RunnableBool after, RunnableData<TdlibUi.UrlOpenParameters> proceed) {
    if (options != null && options.citchatLinkChecked) {
      return false;
    }
    ScamGuard.LinkRisk risk = ScamGuard.checkLink(url);
    if (risk.reason == ScamGuard.LINK_SAFE) {
      return false;
    }
    ViewController<?> controller = context instanceof ViewController<?> ? (ViewController<?>) context : UI.getCurrentStackItem();
    if (controller == null || controller.isDestroyed()) {
      return false;
    }
    String info = Lang.getString(R.string.CITchatLinkWarning) + "\n\n" + linkReason(risk);
    controller.showOptions(info,
      new int[] {R.id.btn_citchatLinkOpen, R.id.btn_cancel},
      new String[] {Lang.getString(R.string.CITchatLinkOpenAnyway), Lang.getString(R.string.Cancel)},
      new int[] {ViewController.OptionColor.RED, ViewController.OptionColor.NORMAL},
      new int[] {R.drawable.baseline_warning_24, R.drawable.baseline_cancel_24},
      (itemView, id) -> {
        if (id == R.id.btn_citchatLinkOpen) {
          // Marked only now: cancelling must leave the next tap on the same link checked again.
          TdlibUi.UrlOpenParameters approved = options != null ? options : new TdlibUi.UrlOpenParameters();
          approved.citchatLinkChecked = true;
          proceed.runWithData(approved);
        } else if (after != null) {
          after.runWithBool(false);
        }
        return true;
      });
    return true;
  }
}

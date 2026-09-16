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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.thunderdog.challegram.R;
import org.thunderdog.challegram.component.dialogs.ChatView;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.support.RippleSupport;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.Theme;
import org.thunderdog.challegram.tool.Drawables;
import org.thunderdog.challegram.tool.Paints;
import org.thunderdog.challegram.tool.Screen;
import org.thunderdog.challegram.unsorted.Settings;

/**
 * Chat list row for {@link HouseAd}. Uses {@link ChatView}'s metrics for the current chat list mode,
 * so it lines up with the chats around it, while the "Sponsored" badge keeps it from passing for one.
 */
public class HouseAdView extends View {
  public interface Delegate {
    void onHouseAdClick ();
    void onHouseAdHide ();
  }

  private static final float CLOSE_AREA_DP = 56f;

  private final Drawable icon, closeIcon;
  private final int brandColor;
  private final RectF badgeRect = new RectF();

  private @Nullable Delegate delegate;
  private boolean touchOnClose;

  public HouseAdView (Context context, @Nullable ViewController<?> themeProvider) {
    super(context);
    icon = Drawables.get(getResources(), R.drawable.baseline_sim_card_24);
    closeIcon = Drawables.get(getResources(), R.drawable.baseline_close_24);
    brandColor = ContextCompat.getColor(context, R.color.citchat_brand_deep);
    RippleSupport.setSimpleWhiteBackground(this, themeProvider);
    setOnClickListener(v -> {
      if (delegate == null) {
        return;
      }
      if (touchOnClose) {
        delegate.onHouseAdHide();
      } else {
        delegate.onHouseAdClick();
      }
    });
  }

  public void setDelegate (@Nullable Delegate delegate) {
    this.delegate = delegate;
  }

  public void updateContentDescription () {
    setContentDescription(Lang.getString(R.string.CITchatAdTitle) + ", " + Lang.getString(R.string.CITchatAdSponsored) + ". " + Lang.getString(R.string.CITchatAdText));
  }

  @Override
  protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
    setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ChatView.getViewHeight(Settings.instance().getChatListMode()));
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent (MotionEvent e) {
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      int closeArea = Screen.dp(CLOSE_AREA_DP);
      touchOnClose = Lang.rtl() ? e.getX() < closeArea : e.getX() > getWidth() - closeArea;
    }
    return super.onTouchEvent(e);
  }

  @Override
  protected void onDraw (Canvas c) {
    final int mode = Settings.instance().getChatListMode();
    final int width = getWidth();
    final boolean rtl = Lang.rtl();

    final int avatarRadius = ChatView.getAvatarSize(mode) / 2;
    final int avatarX = rtl ? width - ChatView.getAvatarLeftFull(mode) : ChatView.getAvatarLeftFull(mode);
    final int avatarY = ChatView.getAvatarTopFull(mode);
    c.drawCircle(avatarX, avatarY, avatarRadius, Paints.fillingPaint(brandColor));
    Drawables.drawCentered(c, icon, avatarX, avatarY, Paints.getPorterDuffPaint(0xffffffff));

    final int closeArea = Screen.dp(CLOSE_AREA_DP);
    final int textStart = ChatView.getLeftPadding(mode);
    final int textWidth = Math.max(0, width - textStart - closeArea);

    final TextPaint titlePaint = Paints.getMediumTextPaint(17f, Theme.getColor(ColorId.text), false);
    final TextPaint badgePaint = Paints.getMediumTextPaint(12f, Theme.getColor(ColorId.badgeMutedText), false);
    final TextPaint textPaint = Paints.getRegularTextPaint(15f, Theme.getColor(ColorId.textLight));

    final String badge = Lang.getString(R.string.CITchatAdSponsored);
    final float badgePadding = Screen.dp(6f);
    final float badgeWidth = badgePaint.measureText(badge) + badgePadding * 2;
    final float badgeGap = Screen.dp(8f);

    final CharSequence title = TextUtils.ellipsize(Lang.getString(R.string.CITchatAdTitle), titlePaint, Math.max(0, textWidth - badgeWidth - badgeGap), TextUtils.TruncateAt.END);
    final float titleWidth = titlePaint.measureText(title, 0, title.length());
    final int titleBaseline = ChatView.getTitleTop(mode);
    c.drawText(title, 0, title.length(), rtl ? width - textStart - titleWidth : textStart, titleBaseline, titlePaint);

    final float badgeStart = textStart + titleWidth + badgeGap;
    final float badgeTop = titleBaseline - Screen.dp(15f);
    badgeRect.set(rtl ? width - badgeStart - badgeWidth : badgeStart, badgeTop, rtl ? width - badgeStart : badgeStart + badgeWidth, badgeTop + Screen.dp(18f));
    c.drawRoundRect(badgeRect, Screen.dp(4f), Screen.dp(4f), Paints.fillingPaint(Theme.getColor(ColorId.badgeMuted)));
    c.drawText(badge, badgeRect.left + badgePadding, badgeRect.top + Screen.dp(13f), badgePaint);

    final CharSequence text = TextUtils.ellipsize(Lang.getString(R.string.CITchatAdText), textPaint, textWidth, TextUtils.TruncateAt.END);
    final float textLineWidth = textPaint.measureText(text, 0, text.length());
    c.drawText(text, 0, text.length(), rtl ? width - textStart - textLineWidth : textStart, ChatView.getTextTop(mode) + Screen.dp(14f), textPaint);

    Drawables.drawCentered(c, closeIcon, rtl ? closeArea / 2f : width - closeArea / 2f, getHeight() / 2f, Paints.getPorterDuffPaint(Theme.getColor(ColorId.icon)));
  }
}

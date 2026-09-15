# Aplica a paleta da CITmax (citmax.com.br) aos temas do Telegram X.
#
#   powershell -ExecutionPolicy Bypass -File citchat\branding\aplicar-paleta.ps1
#
# Blue.tgx-theme e o tema base (Classic herda dele) e Night Blue.tgx-theme e o escuro padrao.
# O script troca cores por nome de chave, valida que toda chave existe no tema base e regrava o
# arquivo agrupando as chaves por cor, como o formato original. Pode ser rodado de novo sem efeito
# colateral (por exemplo, depois de receber atualizacoes do Telegram X).
$ErrorActionPreference = 'Stop'

$Themes = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'app\src\main\other\themes'

# Paleta do site (variaveis CSS)
$P  = '#00C896'   # --brand-primary
$M  = '#008B87'   # --brand-mid
$D  = '#036271'   # --brand-deep
$L  = '#D8FF3E'   # --brand-accent (lime)
$I  = '#0D1F1C'   # --ink
$I2 = '#12302A'   # --ink-2
$C  = '#FDFCF5'   # --cream
$LN = '#E4E7DD'   # --line
$MU = '#657370'   # --muted

function Expand([hashtable]$Groups) {
  $map = [ordered]@{}
  foreach ($k in $Groups.Keys) {
    foreach ($name in ($k -split ',')) {
      $n = $name.Trim()
      if ($map.Contains($n)) { throw "Chave '$n' aparece em mais de um grupo da paleta" }
      $map[$n] = $Groups[$k]
    }
  }
  return $map
}

$Light = Expand @{
  'headerBackground, passcode' = $I
  'notification, notificationLink, notificationPlayer, headerBarCallActive' = $P
  'headerBadge, headerTabActive' = $L
  'controlActive, themeBlue, themeClassic, fillingPositive, checkActive, chatListVerify, sliderActive, inputActive' = $P
  'togglerActive, togglerPositive, promo, profileSectionActive, seekDone, messageSwipeBackground, online' = $P
  'bubbleOut_waveformActive, waveformActive' = $P
  'fillingPositiveContent, checkContent, badgeText' = $I
  'circleButtonActive, circleButtonRegular, circleButtonTheme, snackbarUpdate, badge, tooltip_textLink' = $L
  'circleButtonActiveIcon, circleButtonRegularIcon, circleButtonThemeIcon, snackbarUpdateText, snackbarUpdateAction' = $I
  'chatSendButton, progress, bubbleIn_progress, iconActive, ticks, ticksRead, chatListAction, textSearchQueryHighlight' = $M
  'profileSectionActiveContent, playerButtonActive, bubbleOut_ticks, bubbleOut_ticksRead' = $M
  'bubbleOut_inlineOutline, inlineOutline, bubbleOut_chatCorrectChosenFilling, bubbleOut_chatCorrectFilling, bubbleOut_chatVerticalLine' = $M
  'bubbleOut_inlineIcon, inlineIcon, messageCorrectChosenFilling, messageCorrectFilling, messageVerticalLine' = $M
  'bubbleOut_file, file, bubbleOut_fillingPositive, bubbleOut_fillingPositive_overlay, bubbleIn_fillingPositive, bubbleIn_fillingPositive_overlay' = $M
  'avatarArchive, avatarReplies, avatarReplies_big, avatarSavedMessages, avatarSavedMessages_big' = $M
  'textLink, bubbleIn_textLink, bubbleOut_textLink, iv_textLink, iv_textMarkedLink, textNeutral, unreadText' = $D
  'bubbleOut_inlineText, bubbleOut_messageAuthor, inlineText, messageAuthor' = $D
  'textLinkPressHighlight, bubbleIn_textLinkPressHighlight, bubbleOut_textLinkPressHighlight, iv_textLinkPressHighlight' = '#03627130'
  'tooltip_textLinkPressHighlight' = '#D8FF3E44'
  'togglerActiveBackground, togglerPositiveBackground' = '#9FE7D2'
  'textSelectionHighlight' = '#BDEFDF'
  'unread' = '#E6F4EE'
  'messageSelection' = '#00C89612'
  'bubble_messageSelection, bubble_messageSelectionNoWallpaper' = '#00C89633'
  'bubbleOut_waveformInactive, waveformInactive' = '#CFE9DF'
  'background, iv_chatLinkBackground, iv_textReferenceBackground, chatKeyboard' = '#F3F2EA'
  'chatKeyboardButton, bubbleIn_separator, chatSeparator, inputInactive, iv_separator, separator' = $LN
  'bubble_chatSeparator, shareSeparator' = '#E4E7DDAA'
  'chatBackground' = '#ECEFE6'
  'bubbleOut_background' = '#D9F6EC'
  'bubbleIn_outline, bubbleOut_outline' = '#C9DDD3'
  'bubbleOut_progress, bubbleOut_separator, bubbleOut_time' = '#6E978A'
  'bubbleOut_fillingActive' = '#008B871A'
  'text, bubbleIn_text, bubbleOut_text' = $I
  'textLight' = $MU
  'icon, headerLightIcon, controlInactive' = '#6E7B77'
  'background_text' = '#5A6864'
  'background_textLight' = '#6F7C78'
  'background_icon' = '#93A09B'
  'chatListIcon, chatListMute' = '#A9B3AF'
  'bubbleIn_time, textPlaceholder' = '#9AA6A1'
  'iconLight' = '#A0ABA7'
  'fillingPressed' = '#F1F2EC'
  'themeNightBlue' = $I2
}

$Dark = Expand @{
  'filling, bubbleIn_background, circleButtonChat, circleButtonOverlay, overlayFilling, unread' = $I2
  'attachContact, attachFile, attachInlineBot, attachLocation, attachPhoto, bubble_mediaReply_noWallpaper, bubble_unread_noWallpaper' = $I2
  'background, chatBackground, chatKeyboard, iv_chatLinkBackground, iv_preBlockBackground, iv_textCodeBackground, iv_textCodeBackgroundPressed' = $I
  'headerBackground, headerLightBackground, notificationPlayer, passcode, chatKeyboardButton' = '#163A33'
  'background_icon, badgeMuted, bubbleIn_time, circleButtonChatIcon, circleButtonOverlayIcon, icon, iv_caption, iv_icon' = '#8FA39D'
  'iv_pageAuthor, iv_pageFooter, textLight, textPlaceholder, headerBadgeMuted, iv_pageSubtitle' = '#8FA39D'
  'background_text, background_textLight' = '#879A94'
  'chatListAction, chatListVerify, checkActive, iconActive, seekDone, sliderActive, ticks, ticksRead' = $P
  'bubbleIn_progress, chatSendButton, controlActive, inputActive, profileSectionActive, profileSectionActiveContent, progress, promo' = $P
  'textNeutral, textSearchQueryHighlight, togglerActive, togglerPositive, notification, notificationLink, headerButton' = $P
  'bubbleIn_textLink, iv_textLink, iv_textMarkedLink, messageAuthor, textLink, messageCorrectChosenFilling, messageCorrectFilling' = $P
  'inlineIcon, inlineOutline, inlineText, playerButtonActive, file, bubbleOut_file' = $P
  'bubbleIn_fillingPositive, bubbleIn_fillingPositive_overlay, bubbleOut_fillingPositive, bubbleOut_fillingPositive_overlay' = $P
  'badge, headerBadge, circleButtonActive, circleButtonRegular, circleButtonTheme, snackbarUpdate, bubbleOut_ticksRead' = $L
  'bubbleOut_chatCorrectChosenFilling, bubbleOut_chatCorrectFilling, bubbleOut_chatVerticalLine, bubbleOut_inlineIcon' = $L
  'bubbleOut_inlineOutline, bubbleOut_inlineText, bubbleOut_messageAuthor, bubbleOut_textLink, bubbleOut_ticks, bubbleOut_waveformActive' = $L
  'bubbleOut_background, messageSwipeBackground' = $D
  'text, bubbleIn_text, bubbleOut_text' = $C
  'bubble_chatSeparator, bubbleIn_outline, bubbleIn_separator, bubbleOut_outline, chatSeparator, inputInactive, iv_separator, separator, shareSeparator' = '#081512'
  'bubble_date, bubble_date_noWallpaper, bubble_overlay' = '#0D1F1C99'
  'textLinkPressHighlight, bubbleIn_textLinkPressHighlight, iv_textLinkPressHighlight' = '#00C89644'
  'bubbleOut_textLinkPressHighlight' = '#D8FF3E40'
  'bubbleOut_progress, bubbleOut_separator, bubbleOut_time' = '#9CCFC2'
  'bubbleOut_waveformInactive, waveformInactive' = '#4E8C7D'
  'waveformActive' = '#2F7F6E'
  'chatListIcon, chatListMute, togglerInactive' = '#82968F'
  'controlInactive, sliderInactive, togglerInactiveBackground' = '#3D5751'
  'fileAttach, playerCoverPlaceholder' = '#0A1916'
  'fillingPressed' = '#173B34'
  'iconLight' = '#4F615C'
  'messageSelection' = '#00C89614'
  'messageVerticalLine' = $M
  'playerCoverIcon' = '#6F837D'
  'seekEmpty' = '#0A1714'
  'seekReady' = '#1F4A40'
  'textSelectionHighlight' = '#1E4A40'
  'togglerActiveBackground, togglerPositiveBackground' = '#0F6E5A'
  'tooltip_outline' = '#03627120'
  'fillingActive, bubbleIn_fillingActive' = '#8FA39D26'
  'bubbleOut_fillingActive' = '#9CCFC226'
  'blockQuoteText, blockQuoteLine' = '#A9C2BA'
  'bubbleIn_blockQuoteText, bubbleIn_blockQuoteLine' = '#93ABA3'
  'bubbleOut_blockQuoteText, bubbleOut_blockQuoteLine' = '#BFE9DC'
}

function Read-Theme([string]$Path) {
  $lines = [IO.File]::ReadAllLines($Path)
  $idx = [Array]::IndexOf($lines, '#')
  if ($idx -lt 0) { throw "Secao de cores (#) nao encontrada em $Path" }
  $colors = [ordered]@{}
  foreach ($line in $lines[($idx + 1)..($lines.Length - 1)]) {
    if (-not $line.Trim()) { continue }
    $sep = $line.LastIndexOf(':')
    $value = $line.Substring($sep + 1).Trim()
    foreach ($name in ($line.Substring(0, $sep) -split ',')) {
      $n = $name.Trim()
      if ($colors.Contains($n)) { throw "Chave repetida $n em $Path" }
      $colors[$n] = $value
    }
  }
  return @{ Header = $lines[0..$idx]; Colors = $colors }
}

function Write-Theme([string]$Path, $Theme) {
  $groups = [ordered]@{}
  foreach ($name in $Theme.Colors.Keys) {
    $value = $Theme.Colors[$name]
    if (-not $groups.Contains($value)) { $groups[$value] = New-Object System.Collections.Generic.List[string] }
    $groups[$value].Add($name)
  }
  $out = New-Object System.Text.StringBuilder
  foreach ($h in $Theme.Header) { [void]$out.Append($h).Append("`n") }
  foreach ($value in $groups.Keys) { [void]$out.Append(($groups[$value] -join ', ') + ": $value`n") }
  [IO.File]::WriteAllText($Path, $out.ToString(), (New-Object System.Text.UTF8Encoding($false)))
}

$base = Read-Theme (Join-Path $Themes 'Blue.tgx-theme')
$known = $base.Colors.Keys
foreach ($pair in @(@('Blue.tgx-theme', $Light), @('Night Blue.tgx-theme', $Dark))) {
  $path = Join-Path $Themes $pair[0]
  $theme = Read-Theme $path
  foreach ($name in $pair[1].Keys) {
    if ($known -notcontains $name) { throw "Cor desconhecida '$name' ($($pair[0]))" }
    $theme.Colors[$name] = $pair[1][$name]
  }
  Write-Theme $path $theme
  "{0}: {1} cores aplicadas" -f $pair[0], $pair[1].Count
}

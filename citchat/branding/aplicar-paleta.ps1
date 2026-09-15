# Aplica a paleta do Manual da marca CITmax aos temas do Telegram X.
#
#   powershell -ExecutionPolicy Bypass -File citchat\branding\aplicar-paleta.ps1
#
# Blue.tgx-theme e o tema base (Classic herda dele) e Night Blue.tgx-theme e o escuro padrao.
# O script troca cores por nome de chave, valida que toda chave existe no tema base e regrava o
# arquivo agrupando as chaves por cor, como o formato original. Pode ser rodado de novo sem efeito
# colateral (por exemplo, depois de receber atualizacoes do Telegram X).
$ErrorActionPreference = 'Stop'

$Themes = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path 'app\src\main\other\themes'

# Manual da marca CITmax, "Padroes - Cores"
$C1 = '#00C896'   # Inovacao (cor 1), Pantone 3395 U
$C2 = '#008B87'   # Tecnologia em movimento (cor 2), Pantone 327 U
$C3 = '#036271'   # Conexao profunda (cor 3), Pantone 3155 C
$W  = '#FFFFFF'   # Pureza digital

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

# Tema claro: cabecalho em Conexao profunda, destaques em Inovacao, icones em Tecnologia em movimento
$Light = Expand @{
  'headerBackground, passcode' = $C3
  'notification, notificationLink, notificationPlayer, headerBarCallActive, headerBadge, headerTabActive' = $C1
  'controlActive, themeBlue, themeClassic, fillingPositive, checkActive, chatListVerify, sliderActive, inputActive' = $C1
  'togglerActive, togglerPositive, promo, profileSectionActive, seekDone, messageSwipeBackground, online' = $C1
  'bubbleOut_waveformActive, waveformActive' = $C1
  'circleButtonActive, circleButtonRegular, circleButtonTheme, snackbarUpdate, badge, tooltip_textLink' = $C1
  'circleButtonActiveIcon, circleButtonRegularIcon, circleButtonThemeIcon' = $W
  'fillingPositiveContent, checkContent, badgeText, snackbarUpdateText, snackbarUpdateAction' = $C3
  'chatSendButton, progress, bubbleIn_progress, iconActive, ticks, ticksRead, chatListAction, textSearchQueryHighlight' = $C2
  'profileSectionActiveContent, playerButtonActive, bubbleOut_ticks, bubbleOut_ticksRead' = $C2
  'bubbleOut_inlineOutline, inlineOutline, bubbleOut_chatCorrectChosenFilling, bubbleOut_chatCorrectFilling, bubbleOut_chatVerticalLine' = $C2
  'bubbleOut_inlineIcon, inlineIcon, messageCorrectChosenFilling, messageCorrectFilling, messageVerticalLine' = $C2
  'bubbleOut_file, file, bubbleOut_fillingPositive, bubbleOut_fillingPositive_overlay, bubbleIn_fillingPositive, bubbleIn_fillingPositive_overlay' = $C2
  'avatarArchive, avatarReplies, avatarReplies_big, avatarSavedMessages, avatarSavedMessages_big' = $C2
  'textLink, bubbleIn_textLink, bubbleOut_textLink, iv_textLink, iv_textMarkedLink, textNeutral, unreadText' = $C3
  'bubbleOut_inlineText, bubbleOut_messageAuthor, inlineText, messageAuthor' = $C3
  'textLinkPressHighlight, bubbleIn_textLinkPressHighlight, bubbleOut_textLinkPressHighlight, iv_textLinkPressHighlight' = '#03627130'
  'tooltip_textLinkPressHighlight' = '#00C89644'
  'togglerActiveBackground, togglerPositiveBackground' = '#9FE7D2'
  'textSelectionHighlight' = '#BDEFDF'
  'unread' = '#E3F4F1'
  'messageSelection' = '#00C89612'
  'bubble_messageSelection, bubble_messageSelectionNoWallpaper' = '#00C89633'
  'bubbleOut_waveformInactive, waveformInactive' = '#CDE7E4'
  'background, iv_chatLinkBackground, iv_textReferenceBackground, chatKeyboard' = '#F1F5F5'
  'chatKeyboardButton, bubbleIn_separator, chatSeparator, inputInactive, iv_separator, separator' = '#DCE6E6'
  'bubble_chatSeparator, shareSeparator' = '#DCE6E6AA'
  'chatBackground' = '#E6EEEE'
  'bubbleOut_background' = '#D5F4EA'
  'bubbleIn_outline, bubbleOut_outline' = '#C5DAD9'
  'bubbleOut_progress, bubbleOut_separator, bubbleOut_time' = '#5F8F86'
  'bubbleOut_fillingActive' = '#008B871A'
  'text, bubbleIn_text, bubbleOut_text' = '#10272B'
  'textLight' = '#5E7478'
  'icon, headerLightIcon, controlInactive' = '#6B7F82'
  'background_text' = '#566B6E'
  'background_textLight' = '#6B7F82'
  'background_icon' = '#8FA3A5'
  'chatListIcon, chatListMute' = '#A6B5B7'
  'bubbleIn_time, textPlaceholder' = '#98A8AA'
  'iconLight' = '#9EAEB0'
  'fillingPressed' = '#EEF3F3'
  'themeNightBlue' = '#06363F'
}

# Tema escuro: fundos derivados de Conexao profunda, destaques em Inovacao
$Dark = Expand @{
  'filling, bubbleIn_background, circleButtonChat, circleButtonOverlay, overlayFilling, unread' = '#06363F'
  'attachContact, attachFile, attachInlineBot, attachLocation, attachPhoto, bubble_mediaReply_noWallpaper, bubble_unread_noWallpaper' = '#06363F'
  'background, chatBackground, chatKeyboard, iv_chatLinkBackground, iv_preBlockBackground, iv_textCodeBackground, iv_textCodeBackgroundPressed' = '#042A31'
  'headerBackground, headerLightBackground, notificationPlayer, passcode, chatKeyboardButton' = '#054A55'
  'background_icon, badgeMuted, bubbleIn_time, circleButtonChatIcon, circleButtonOverlayIcon, icon, iv_caption, iv_icon' = '#8AA3A6'
  'iv_pageAuthor, iv_pageFooter, textLight, textPlaceholder, headerBadgeMuted, iv_pageSubtitle' = '#8AA3A6'
  'background_text, background_textLight' = '#85A0A3'
  'chatListAction, chatListVerify, checkActive, iconActive, seekDone, sliderActive, ticks, ticksRead' = $C1
  'bubbleIn_progress, chatSendButton, controlActive, inputActive, profileSectionActive, profileSectionActiveContent, progress, promo' = $C1
  'textNeutral, textSearchQueryHighlight, togglerActive, togglerPositive, notification, notificationLink, headerButton' = $C1
  'bubbleIn_textLink, iv_textLink, iv_textMarkedLink, messageAuthor, textLink, messageCorrectChosenFilling, messageCorrectFilling' = $C1
  'inlineIcon, inlineOutline, inlineText, playerButtonActive, file, bubbleOut_file' = $C1
  'bubbleIn_fillingPositive, bubbleIn_fillingPositive_overlay, bubbleOut_fillingPositive, bubbleOut_fillingPositive_overlay' = $C1
  'badge, headerBadge, circleButtonActive, circleButtonRegular, circleButtonTheme, snackbarUpdate' = $C1
  'bubbleOut_chatCorrectChosenFilling, bubbleOut_chatCorrectFilling, bubbleOut_chatVerticalLine, bubbleOut_inlineIcon, bubbleOut_ticksRead' = '#7FE6C8'
  'bubbleOut_inlineOutline, bubbleOut_inlineText, bubbleOut_messageAuthor, bubbleOut_textLink, bubbleOut_ticks, bubbleOut_waveformActive' = '#7FE6C8'
  'bubbleOut_background, messageSwipeBackground' = $C3
  'text, bubbleIn_text, bubbleOut_text' = '#F2F7F7'
  'bubble_chatSeparator, bubbleIn_outline, bubbleIn_separator, bubbleOut_outline, chatSeparator, inputInactive, iv_separator, separator, shareSeparator' = '#03222A'
  'bubble_date, bubble_date_noWallpaper, bubble_overlay' = '#042A3199'
  'textLinkPressHighlight, bubbleIn_textLinkPressHighlight, iv_textLinkPressHighlight' = '#00C89644'
  'bubbleOut_textLinkPressHighlight' = '#7FE6C840'
  'bubbleOut_progress, bubbleOut_separator, bubbleOut_time' = '#A5D9D1'
  'bubbleOut_waveformInactive, waveformInactive' = '#3F8A87'
  'waveformActive' = '#2E8F88'
  'chatListIcon, chatListMute, togglerInactive' = '#7F9A9D'
  'controlInactive, sliderInactive, togglerInactiveBackground' = '#3A5B60'
  'fileAttach, playerCoverPlaceholder' = '#032329'
  'fillingPressed' = '#0B414B'
  'iconLight' = '#4D666A'
  'messageSelection' = '#00C89614'
  'messageVerticalLine' = $C2
  'playerCoverIcon' = '#6C8A8D'
  'seekEmpty' = '#03222A'
  'seekReady' = '#1B525A'
  'textSelectionHighlight' = '#17545C'
  'togglerActiveBackground, togglerPositiveBackground' = '#0D6C66'
  'tooltip_outline' = '#03627120'
  'fillingActive, bubbleIn_fillingActive' = '#8AA3A626'
  'bubbleOut_fillingActive' = '#A5D9D126'
  'blockQuoteText, blockQuoteLine' = '#A7C3C5'
  'bubbleIn_blockQuoteText, bubbleIn_blockQuoteLine' = '#91AEB1'
  'bubbleOut_blockQuoteText, bubbleOut_blockQuoteLine' = '#BFE9E3'
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

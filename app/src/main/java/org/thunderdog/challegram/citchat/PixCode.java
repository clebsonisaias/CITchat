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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pix "copia e cola" codes (BR Code, EMV QRCPS-MPM) and boleto numbers: building, finding and validating.
 *
 * Plain Java on purpose. Nothing here touches money: a Pix code is just text the payer pastes into
 * their bank app, and the checks (CRC16, boleto check digits) only avoid offering a copy button for
 * something that is not a real code.
 */
public final class PixCode {
  private PixCode () { }

  public static final int TYPE_PIX = 1, TYPE_BOLETO = 2;

  public static final class Found {
    /** Range of the code in the searched text. */
    public final int start, end;
    /** The exact text to copy: the Pix payload, or only the digits of a boleto. */
    public final String code;
    public final int type;
    /** 0 when the code carries no amount (open-value Pix, boleto with a reference instead of a value). */
    public final long amountCents;
    /** Pix only: the receiver name inside the code. */
    public final @Nullable String receiverName;

    Found (int start, int end, String code, int type, long amountCents, @Nullable String receiverName) {
      this.start = start;
      this.end = end;
      this.code = code;
      this.type = type;
      this.amountCents = amountCents;
      this.receiverName = receiverName;
    }
  }

  // ── Finding ──────────────────────────────────────────────────────────────

  public static List<Found> find (@Nullable CharSequence source) {
    List<Found> result = new ArrayList<>();
    if (source == null || source.length() < 44) {
      return result;
    }
    String text = source.toString();
    int from = 0;
    while (true) {
      int start = text.indexOf("000201", from);
      if (start == -1) {
        break;
      }
      Found pix = parsePixAt(text, start);
      if (pix != null) {
        result.add(pix);
        from = pix.end;
      } else {
        from = start + 1;
      }
    }
    findBoletos(text, result);
    return result;
  }

  public static @Nullable Found findFirst (@Nullable CharSequence text) {
    List<Found> found = find(text);
    return found.isEmpty() ? null : found.get(0);
  }

  /** Reads TLV fields from {@code start} until the CRC field; null unless it is a valid Pix BR Code. */
  private static @Nullable Found parsePixAt (String text, int start) {
    int i = start;
    int fieldCount = 0;
    boolean isPix = false;
    long amountCents = 0;
    String name = null;
    while (i + 4 <= text.length() && fieldCount < 40) {
      String id = text.substring(i, i + 2);
      String length = text.substring(i + 2, i + 4);
      if (!isDigits(id) || !isDigits(length)) {
        return null;
      }
      int valueStart = i + 4;
      int valueEnd = valueStart + Integer.parseInt(length);
      if (valueEnd > text.length()) {
        return null;
      }
      String value = text.substring(valueStart, valueEnd);
      if (fieldCount == 0 && !(id.equals("00") && value.equals("01"))) {
        return null;
      }
      fieldCount++;
      if (id.equals("63")) {
        if (value.length() != 4 || !isPix || !crc16(text.substring(start, valueStart)).equalsIgnoreCase(value)) {
          return null;
        }
        return new Found(start, valueEnd, text.substring(start, valueEnd), TYPE_PIX, amountCents, name);
      }
      int idNumber = Integer.parseInt(id);
      if (idNumber >= 26 && idNumber <= 51 && value.toLowerCase(Locale.ROOT).contains("br.gov.bcb.pix")) {
        isPix = true;
      } else if (id.equals("54")) {
        amountCents = parseCents(value);
      } else if (id.equals("59")) {
        name = value.trim();
      }
      i = valueEnd;
    }
    return null;
  }

  private static final Pattern DIGIT_GROUPS = Pattern.compile("\\d[\\d .\\-]{42,80}\\d");
  private static final Pattern DIGIT_GROUP = Pattern.compile("\\d+");

  /**
   * Boletos travel with dots, spaces or hyphens between groups ("23793.38128 60000.000003 ..."), and a
   * digit run may touch other numbers. Every contiguous set of groups adding up to 44, 47 or 48 digits
   * is tried, and only numbers whose check digits hold are kept.
   */
  private static void findBoletos (String text, List<Found> result) {
    Matcher run = DIGIT_GROUPS.matcher(text);
    while (run.find()) {
      List<int[]> groups = new ArrayList<>();
      Matcher group = DIGIT_GROUP.matcher(text).region(run.start(), run.end());
      while (group.find()) {
        groups.add(new int[] {group.start(), group.end()});
      }
      int first = 0;
      while (first < groups.size()) {
        Found found = null;
        int digitCount = 0;
        for (int last = first; last < groups.size() && digitCount <= 48; last++) {
          digitCount += groups.get(last)[1] - groups.get(last)[0];
          if (digitCount == 44 || digitCount == 47 || digitCount == 48) {
            int start = groups.get(first)[0], end = groups.get(last)[1];
            found = parseBoleto(start, end, text.substring(start, end).replaceAll("\\D", ""));
            if (found != null && !overlaps(result, start, end)) {
              result.add(found);
              first = last;
              break;
            }
            found = null;
          }
        }
        first++;
      }
    }
  }

  private static boolean overlaps (List<Found> found, int start, int end) {
    for (Found item : found) {
      if (start < item.end && end > item.start) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable Found parseBoleto (int start, int end, String digits) {
    switch (digits.length()) {
      case 47:
        if (isValidBankLine(digits)) {
          return new Found(start, end, digits, TYPE_BOLETO, Long.parseLong(digits.substring(37)), null);
        }
        break;
      case 48:
        if (isValidCollectionLine(digits)) {
          String barcode = digits.substring(0, 11) + digits.substring(12, 23) + digits.substring(24, 35) + digits.substring(36, 47);
          return new Found(start, end, digits, TYPE_BOLETO, collectionAmount(barcode), null);
        }
        break;
      case 44:
        if (isValidBarcode(digits)) {
          long amount = digits.charAt(0) == '8' ? collectionAmount(digits) : Long.parseLong(digits.substring(9, 19));
          return new Found(start, end, digits, TYPE_BOLETO, amount, null);
        }
        break;
    }
    return null;
  }

  // ── Boleto check digits (FEBRABAN) ──────────────────────────────────────

  /** Bank boleto line (47 digits): three field check digits (mod 10) and the barcode check digit (mod 11). */
  static boolean isValidBankLine (String d) {
    if (d.length() != 47) {
      return false;
    }
    if (mod10(d.substring(0, 9)) != digit(d, 9) || mod10(d.substring(10, 20)) != digit(d, 20) || mod10(d.substring(21, 31)) != digit(d, 31)) {
      return false;
    }
    String barcode = d.substring(0, 4) + d.charAt(32) + d.substring(33, 47) + d.substring(4, 9) + d.substring(10, 20) + d.substring(21, 31);
    return isValidBarcode(barcode);
  }

  /** Utility/tax collection line (48 digits, starts with 8): one check digit per 11-digit block. */
  static boolean isValidCollectionLine (String d) {
    if (d.length() != 48 || d.charAt(0) != '8') {
      return false;
    }
    boolean useMod10 = d.charAt(2) == '6' || d.charAt(2) == '7';
    if (!useMod10 && d.charAt(2) != '8' && d.charAt(2) != '9') {
      return false;
    }
    for (int block = 0; block < 4; block++) {
      String data = d.substring(block * 12, block * 12 + 11);
      int expected = useMod10 ? mod10(data) : mod11Collection(data);
      if (expected != digit(d, block * 12 + 11)) {
        return false;
      }
    }
    return true;
  }

  static boolean isValidBarcode (String d) {
    if (d.length() != 44) {
      return false;
    }
    if (d.charAt(0) == '8') {
      boolean useMod10 = d.charAt(2) == '6' || d.charAt(2) == '7';
      String data = d.substring(0, 3) + d.substring(4);
      return (useMod10 ? mod10(data) : mod11Collection(data)) == digit(d, 3);
    }
    return mod11Bank(d.substring(0, 4) + d.substring(5)) == digit(d, 4);
  }

  private static long collectionAmount (String barcode) {
    char kind = barcode.charAt(2);
    return kind == '6' || kind == '8' ? Long.parseLong(barcode.substring(4, 15)) : 0;
  }

  static int mod10 (String digits) {
    int sum = 0, weight = 2;
    for (int i = digits.length() - 1; i >= 0; i--) {
      int product = digit(digits, i) * weight;
      sum += product > 9 ? product - 9 : product;
      weight = weight == 2 ? 1 : 2;
    }
    return (10 - sum % 10) % 10;
  }

  static int mod11Bank (String digits) {
    int result = 11 - weightedMod11Sum(digits) % 11;
    return result == 0 || result == 10 || result == 11 ? 1 : result;
  }

  static int mod11Collection (String digits) {
    int rest = weightedMod11Sum(digits) % 11;
    return rest == 0 || rest == 1 ? 0 : 11 - rest;
  }

  private static int weightedMod11Sum (String digits) {
    int sum = 0, weight = 2;
    for (int i = digits.length() - 1; i >= 0; i--) {
      sum += digit(digits, i) * weight;
      weight = weight == 9 ? 2 : weight + 1;
    }
    return sum;
  }

  // ── Building a Pix code ─────────────────────────────────────────────────

  public static final int KEY_CPF = 1, KEY_CNPJ = 2, KEY_PHONE = 3, KEY_EMAIL = 4, KEY_RANDOM = 5;

  public static final class Key {
    public final String value;
    public final int type;

    Key (String value, int type) {
      this.value = value;
      this.type = type;
    }
  }

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
  private static final Pattern RANDOM_KEY = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  /**
   * Reads a Pix key the way people type it. 11 digits are a CPF when the check digits hold, otherwise a
   * mobile number — the preview shows which one was understood, and "+55" forces a phone.
   */
  public static @Nullable Key parseKey (@Nullable String input) {
    if (input == null) {
      return null;
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty() || trimmed.length() > 77) {
      return null;
    }
    if (trimmed.contains("@")) {
      String email = trimmed.toLowerCase(Locale.ROOT);
      return EMAIL.matcher(email).matches() ? new Key(email, KEY_EMAIL) : null;
    }
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (RANDOM_KEY.matcher(lower).matches()) {
      return new Key(lower, KEY_RANDOM);
    }
    if (!trimmed.matches("[+\\d .()/\\-]+")) {
      return null;
    }
    String digits = trimmed.replaceAll("\\D", "");
    if (trimmed.startsWith("+")) {
      return digits.startsWith("55") && (digits.length() == 12 || digits.length() == 13) ? new Key("+" + digits, KEY_PHONE) : null;
    }
    switch (digits.length()) {
      case 14:
        return isValidCnpj(digits) ? new Key(digits, KEY_CNPJ) : null;
      case 11:
        if (isValidCpf(digits)) {
          return new Key(digits, KEY_CPF);
        }
        return digits.charAt(2) == '9' ? new Key("+55" + digits, KEY_PHONE) : null;
      case 10:
        return new Key("+55" + digits, KEY_PHONE);
      case 13:
        return digits.startsWith("55") ? new Key("+" + digits, KEY_PHONE) : null;
      default:
        return null;
    }
  }

  public static String formatKey (Key key) {
    String v = key.value;
    switch (key.type) {
      case KEY_CPF:
        return v.substring(0, 3) + "." + v.substring(3, 6) + "." + v.substring(6, 9) + "-" + v.substring(9);
      case KEY_CNPJ:
        return v.substring(0, 2) + "." + v.substring(2, 5) + "." + v.substring(5, 8) + "/" + v.substring(8, 12) + "-" + v.substring(12);
      case KEY_PHONE: {
        String d = v.substring(3);
        return "+55 " + d.substring(0, 2) + " " + d.substring(2, d.length() - 4) + "-" + d.substring(d.length() - 4);
      }
      default:
        return v;
    }
  }

  /**
   * A static Pix code with a fixed amount. The fields follow the BCB "Manual de Padrões para Iniciação do
   * Pix": name up to 25 and city up to 15 plain ASCII characters, txid "***", CRC16 at the end.
   */
  public static String build (Key key, long amountCents, String receiverName, String receiverCity, @Nullable String description) {
    if (amountCents <= 0) {
      throw new IllegalArgumentException("amountCents");
    }
    String name = plain(receiverName, 25);
    String city = plain(receiverCity, 15);
    if (name.isEmpty() || city.isEmpty()) {
      throw new IllegalArgumentException("name and city are required");
    }
    StringBuilder account = new StringBuilder()
      .append(field("00", "br.gov.bcb.pix"))
      .append(field("01", key.value));
    String info = description != null ? printable(description) : "";
    int room = 99 - account.length() - 4;
    if (!info.isEmpty() && room > 0) {
      account.append(field("02", info.substring(0, Math.min(info.length(), room))));
    }
    StringBuilder payload = new StringBuilder()
      .append(field("00", "01"))
      .append(field("26", account.toString()))
      .append(field("52", "0000"))
      .append(field("53", "986"))
      .append(field("54", BigDecimal.valueOf(amountCents, 2).toPlainString()))
      .append(field("58", "BR"))
      .append(field("59", name))
      .append(field("60", city))
      .append(field("62", field("05", "***")))
      .append("6304");
    return payload.append(crc16(payload.toString())).toString();
  }

  private static String field (String id, String value) {
    if (value.length() > 99) {
      throw new IllegalArgumentException("field " + id + " is too long");
    }
    return id + (value.length() < 10 ? "0" : "") + value.length() + value;
  }

  /** CRC16-CCITT-FALSE (poly 0x1021, init 0xFFFF), as required by the BR Code. */
  static String crc16 (String payload) {
    int crc = 0xFFFF;
    for (int i = 0; i < payload.length(); i++) {
      crc ^= (payload.charAt(i) & 0xFF) << 8;
      for (int bit = 0; bit < 8; bit++) {
        crc = (crc & 0x8000) != 0 ? ((crc << 1) ^ 0x1021) & 0xFFFF : (crc << 1) & 0xFFFF;
      }
    }
    return String.format(Locale.ROOT, "%04X", crc);
  }

  /** The receiver name exactly as it goes into the code (up to 25 plain characters); empty if nothing is left. */
  public static String receiverName (@Nullable String value) {
    return value != null ? plain(value, 25) : "";
  }

  /** The city exactly as it goes into the code (up to 15 plain characters); empty if nothing is left. */
  public static String receiverCity (@Nullable String value) {
    return value != null ? plain(value, 15) : "";
  }

  /** Accents stripped, only letters, digits and single spaces, upper case — what every bank accepts. */
  private static String plain (String value, int maxLength) {
    String ascii = stripAccents(value).replaceAll("[^A-Za-z0-9 ]", " ").replaceAll(" +", " ").trim().toUpperCase(Locale.ROOT);
    return ascii.length() > maxLength ? ascii.substring(0, maxLength).trim() : ascii;
  }

  private static String printable (String value) {
    return stripAccents(value).replaceAll("[^\\x20-\\x7E]", "").replaceAll(" +", " ").trim();
  }

  static String stripAccents (String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
  }

  // ── Money ───────────────────────────────────────────────────────────────

  /** "30", "30,50", "1.234,56", "R$ 12.5" → cents; -1 when it is not an amount. */
  public static long parseAmount (@Nullable String input) {
    if (input == null) {
      return -1;
    }
    String value = input.replace("R$", "").replaceAll("\\s", "");
    if (value.isEmpty() || !value.matches("[\\d.,]+")) {
      return -1;
    }
    if (value.contains(",")) {
      value = value.replace(".", "").replace(',', '.');
    } else if (value.indexOf('.') != value.lastIndexOf('.') || value.length() - value.lastIndexOf('.') > 3) {
      value = value.replace(".", "");
    }
    try {
      long cents = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
      return cents > 0 && cents <= 999_999_999_99L ? cents : -1;
    } catch (NumberFormatException | ArithmeticException e) {
      return -1;
    }
  }

  private static long parseCents (String value) {
    try {
      return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
    } catch (NumberFormatException | ArithmeticException e) {
      return 0;
    }
  }

  /** R$ 1.234,56 — built by hand so no locale puts a non-breaking space where people expect a space. */
  public static String formatBrl (long cents) {
    String integer = Long.toString(cents / 100);
    StringBuilder grouped = new StringBuilder();
    for (int i = 0; i < integer.length(); i++) {
      if (i > 0 && (integer.length() - i) % 3 == 0) {
        grouped.append('.');
      }
      grouped.append(integer.charAt(i));
    }
    long fraction = cents % 100;
    return "R$ " + grouped + "," + (fraction < 10 ? "0" : "") + fraction;
  }

  /** Each person's share, rounded up to the cent so whoever paid the bill never ends up short. */
  public static long shareOf (long totalCents, int people) {
    return people <= 0 ? totalCents : (totalCents + people - 1) / people;
  }

  // ── Documents ───────────────────────────────────────────────────────────

  static boolean isValidCpf (String d) {
    if (d.length() != 11 || d.chars().distinct().count() == 1) {
      return false;
    }
    for (int position = 9; position <= 10; position++) {
      int sum = 0;
      for (int i = 0; i < position; i++) {
        sum += digit(d, i) * (position + 1 - i);
      }
      int check = (sum * 10) % 11 % 10;
      if (check != digit(d, position)) {
        return false;
      }
    }
    return true;
  }

  static boolean isValidCnpj (String d) {
    if (d.length() != 14 || d.chars().distinct().count() == 1) {
      return false;
    }
    int[] weights = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    for (int position = 12; position <= 13; position++) {
      int sum = 0;
      for (int i = 0; i < position; i++) {
        sum += digit(d, i) * weights[i + 13 - position];
      }
      int rest = sum % 11;
      if ((rest < 2 ? 0 : 11 - rest) != digit(d, position)) {
        return false;
      }
    }
    return true;
  }

  private static int digit (String s, int index) {
    return s.charAt(index) - '0';
  }

  private static boolean isDigits (String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) < '0' || s.charAt(i) > '9') {
        return false;
      }
    }
    return true;
  }
}

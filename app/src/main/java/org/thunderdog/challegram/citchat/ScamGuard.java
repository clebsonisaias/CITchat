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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scam heuristics that run entirely on the phone. Nothing is sent anywhere: the text of a message or
 * the address of a link is matched against patterns of the most common scams in Brazil — the fake
 * relative asking for Pix, "I changed my number", the request for a verification code, and links that
 * imitate banks and government sites.
 *
 * These are warnings, not blocks: a false alarm costs one tap, a missed one can cost someone's savings.
 */
public final class ScamGuard {
  private ScamGuard () { }

  // ── Messages ────────────────────────────────────────────────────────────

  /** Ordered from worst to mildest: a stolen verification code takes over the whole account. */
  public static final int MESSAGE_SAFE = 0, MESSAGE_VERIFICATION_CODE = 1, MESSAGE_NEW_NUMBER = 2, MESSAGE_MONEY = 3;

  private static final Pattern VERIFICATION_CODE = Pattern.compile(
    "\\bcodigo (de )?(verificacao|seguranca|confirmacao|acesso|ativacao|autenticacao|6 digitos|seis digitos)\\b" +
    "|\\bcodigo (que |q )?(chegou|chegar|vai chegar|te enviaram|foi enviado|recebeu|voce recebeu)\\b" +
    "|\\b(passa|passe|manda|mande|envia|envie|fala|fale|diz|diga|informa|informe|repassa|repasse)( ai| pra mim| para mim| aqui)? (esse |o |um )?codigo\\b" +
    "|\\bcodigo (do|de|pelo) (whats|whatsapp|zap|telegram|sms|banco|instagram)\\b" +
    "|\\b(chegou|recebeu) (um |o )?(sms|codigo)\\b");

  private static final Pattern NEW_NUMBER = Pattern.compile(
    "\\b(mudei|troquei) (de |o |meu )?(numero|celular|telefone|chip|zap|whats|whatsapp)\\b" +
    "|\\b(numero|contato|zap|whats|whatsapp) novo\\b" +
    "|\\bnovo (numero|contato|zap|whats|whatsapp)\\b" +
    "|\\b(salva|salve|anota|anote|grava|grave|adiciona|adicione) (esse|este|meu|o meu|o) (novo )?(numero|contato)\\b" +
    "|\\b(esse|este) (e|eh) (o )?meu (numero|contato|zap|whats)\\b" +
    "|\\bperdi (o |meu )?(celular|chip|telefone)\\b" +
    "|\\b(celular|telefone) (foi )?(roubado|furtado|quebrou|quebrado|estragou)\\b");

  private static final Pattern MONEY = Pattern.compile(
    "\\bpix\\b" +
    "|\\btransfer(e|ir|encia|ido|ida)\\b" +
    "|\\b(me )?empresta(r)?\\b|\\bemprestimo\\b|\\bemprestado\\b" +
    "|\\bdeposit(a|ar|e|o)\\b" +
    "|\\bboleto\\b" +
    "|\\b(preciso|precisando|necessito) (de )?(um )?(dinheiro|grana|valor)\\b" +
    "|\\br\\$ ?\\d");

  /** Which scam, if any, the text looks like. Only meaningful for messages from people who are not contacts. */
  public static int checkMessage (@Nullable String text) {
    if (text == null || text.isEmpty()) {
      return MESSAGE_SAFE;
    }
    String normalized = normalize(text);
    if (VERIFICATION_CODE.matcher(normalized).find()) {
      return MESSAGE_VERIFICATION_CODE;
    }
    if (NEW_NUMBER.matcher(normalized).find()) {
      return MESSAGE_NEW_NUMBER;
    }
    if (MONEY.matcher(normalized).find() || PixCode.findFirst(text) != null) {
      return MESSAGE_MONEY;
    }
    return MESSAGE_SAFE;
  }

  /** Lower case, no accents, single spaces — so one pattern covers "Código", "codigo" and "CÓDIGO". */
  static String normalize (String text) {
    return PixCode.stripAccents(text).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  // ── Links ───────────────────────────────────────────────────────────────

  public static final int LINK_SAFE = 0,
    LINK_LOOKALIKE = 1,      // a bank or government name on a domain that is not theirs
    LINK_PUNYCODE = 2,       // characters that imitate others (xn--)
    LINK_HIDDEN_HOST = 3,    // "banco.com.br@golpe.com": the real site is after the @
    LINK_IP_ADDRESS = 4,     // a bare number instead of a name
    LINK_SCAM_WORDS = 5,     // "desbloqueio", "resgate", "premio" in the address
    LINK_SHORTENER = 6;      // the real destination is hidden

  public static final class LinkRisk {
    public final int reason;
    public final String host;
    /** For LINK_LOOKALIKE: the name being imitated. */
    public final @Nullable String imitated;

    LinkRisk (int reason, String host, @Nullable String imitated) {
      this.reason = reason;
      this.host = host;
      this.imitated = imitated;
    }
  }

  private static final class Brand {
    final String name;
    final boolean matchInsideWords;
    final List<String> officialDomains;

    Brand (String name, boolean matchInsideWords, String... officialDomains) {
      this.name = name;
      this.matchInsideWords = matchInsideWords;
      this.officialDomains = Arrays.asList(officialDomains);
    }
  }

  private static final String GOV_BR = "gov.br";

  /**
   * Token → brand. Short or common tokens ("pix", "gov", "inter") only match a whole piece of the
   * address, so "pixel.com" or "internet.com.br" are not flagged; distinctive names also match inside
   * a word ("itauseguro.com").
   */
  private static final Map<String, Brand> BRANDS = new HashMap<>();

  private static void brand (String token, Brand brand) {
    BRANDS.put(token, brand);
  }

  static {
    Brand itau = new Brand("Itaú", true, "itau.com.br", "itau.com");
    brand("itau", itau);
    brand("bradesco", new Brand("Bradesco", true, "bradesco.com.br", "bradesco"));
    brand("santander", new Brand("Santander", true, "santander.com.br", "santander.com"));
    Brand caixa = new Brand("Caixa", false, "caixa.gov.br");
    brand("caixa", caixa);
    brand("caixatem", new Brand("Caixa Tem", true, "caixa.gov.br"));
    Brand bb = new Brand("Banco do Brasil", true, "bb.com.br");
    brand("bancodobrasil", bb);
    brand("bb", new Brand("Banco do Brasil", false, "bb.com.br"));
    brand("nubank", new Brand("Nubank", true, "nubank.com.br", "nu.com.br"));
    brand("bancointer", new Brand("Banco Inter", true, "bancointer.com.br", "inter.co"));
    brand("picpay", new Brand("PicPay", true, "picpay.com"));
    brand("mercadopago", new Brand("Mercado Pago", true, "mercadopago.com.br", "mercadopago.com"));
    brand("mercadolivre", new Brand("Mercado Livre", true, "mercadolivre.com.br", "mercadolibre.com"));
    brand("c6bank", new Brand("C6 Bank", true, "c6bank.com.br"));
    brand("sicoob", new Brand("Sicoob", true, "sicoob.com.br"));
    brand("sicredi", new Brand("Sicredi", true, "sicredi.com.br"));
    brand("banrisul", new Brand("Banrisul", true, "banrisul.com.br"));
    brand("correios", new Brand("Correios", true, "correios.com.br"));
    brand("serasa", new Brand("Serasa", true, "serasa.com.br"));
    Brand gov = new Brand("gov.br", false, GOV_BR);
    for (String token : new String[] {"gov", "govbr", "receita", "receitafederal", "detran", "inss", "fgts", "cpf", "cnh", "enem", "sus"}) {
      brand(token, gov);
    }
    brand("pix", new Brand("Pix", false, "bcb.gov.br", GOV_BR));
    brand("whatsapp", new Brand("WhatsApp", true, "whatsapp.com", "whatsapp.net", "wa.me"));
    brand("telegram", new Brand("Telegram", true, "telegram.org", "telegram.me", "t.me", "telegra.ph"));
    brand("netflix", new Brand("Netflix", true, "netflix.com"));
    brand("shopee", new Brand("Shopee", true, "shopee.com.br"));
    brand("magalu", new Brand("Magalu", true, "magazineluiza.com.br", "magalu.com"));
    brand("magazineluiza", new Brand("Magalu", true, "magazineluiza.com.br", "magalu.com"));
    brand("americanas", new Brand("Americanas", true, "americanas.com.br"));
    brand("citmax", new Brand("CITmax", true, "citmax.com.br"));
    brand("citmovel", new Brand("CITmax", true, "citmax.com.br"));
  }

  private static final Set<String> SHORTENERS = new HashSet<>(Arrays.asList(
    "bit.ly", "bitly.com", "tinyurl.com", "cutt.ly", "rb.gy", "is.gd", "v.gd", "ow.ly", "shorturl.at", "tiny.cc",
    "t.ly", "encurtador.com.br", "abre.ai", "rebrand.ly", "s.id", "goo.su", "u.to", "bl.ink", "shorte.st",
    "adf.ly", "migre.me", "kutt.it", "x.gd", "surl.li", "l1nk.dev", "tinu.be", "1url.com.br", "encurta.net"
  ));

  private static final Pattern SCAM_WORDS = Pattern.compile(
    "desbloque|resgat|premio|premiad|premiacao|indeniza|restitui|regulariz|reembols|bloqueio|bloquead|suspens|cancelament|" +
    "atualizacao-?cadastral|recadastr|pendencia|debito-?pendente|valores-?a-?receber|saque-?(fgts|pis)");

  private static final Set<String> BR_SECOND_LEVEL = new HashSet<>(Arrays.asList(
    "com", "net", "org", "gov", "edu", "art", "blog", "app", "dev", "ind", "inf", "jus", "leg", "mil", "mp",
    "nom", "ong", "rec", "srv", "tur", "tv", "adv", "eng", "med", "coop", "eco", "emp", "log", "social"
  ));

  private static final Pattern IP_ADDRESS = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$|^\\[.*]$");

  /** Risk of a link before opening it; LINK_SAFE for addresses that match none of the patterns. */
  public static LinkRisk checkLink (@Nullable String url) {
    String host = hostOf(url);
    if (host == null) {
      return new LinkRisk(LINK_SAFE, "", null);
    }
    if (url != null && hasCredentials(url)) {
      return new LinkRisk(LINK_HIDDEN_HOST, host, null);
    }
    if (IP_ADDRESS.matcher(host).matches()) {
      return new LinkRisk(LINK_IP_ADDRESS, host, null);
    }
    if (host.startsWith("xn--") || host.contains(".xn--")) {
      return new LinkRisk(LINK_PUNYCODE, host, null);
    }
    String registered = registeredDomain(host);
    String[] labels = host.split("\\.");
    int ownLabels = labels.length - registered.split("\\.").length + 1;
    for (int i = 0; i < ownLabels && i < labels.length; i++) {
      for (String token : labels[i].split("-")) {
        Brand brand = findBrand(token);
        if (brand != null && !isOfficial(brand, host, registered)) {
          return new LinkRisk(LINK_LOOKALIKE, host, brand.name);
        }
      }
    }
    if (!host.endsWith("." + GOV_BR) && SCAM_WORDS.matcher(host).find()) {
      return new LinkRisk(LINK_SCAM_WORDS, host, null);
    }
    if (SHORTENERS.contains(host)) {
      return new LinkRisk(LINK_SHORTENER, host, null);
    }
    return new LinkRisk(LINK_SAFE, host, null);
  }

  private static @Nullable Brand findBrand (String token) {
    if (token.isEmpty()) {
      return null;
    }
    Brand exact = BRANDS.get(token);
    if (exact != null) {
      return exact;
    }
    for (Map.Entry<String, Brand> entry : BRANDS.entrySet()) {
      if (entry.getValue().matchInsideWords && token.contains(entry.getKey())) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static boolean isOfficial (Brand brand, String host, String registered) {
    for (String domain : brand.officialDomains) {
      if (domain.equals(GOV_BR) ? (host.equals(GOV_BR) || host.endsWith("." + GOV_BR)) : registered.equals(domain) || host.equals(domain)) {
        return true;
      }
    }
    return false;
  }

  static @Nullable String hostOf (@Nullable String url) {
    if (url == null) {
      return null;
    }
    String value = url.trim();
    int scheme = value.indexOf("://");
    if (scheme != -1) {
      String protocol = value.substring(0, scheme).toLowerCase(Locale.ROOT);
      if (!protocol.equals("http") && !protocol.equals("https")) {
        return null;
      }
      value = value.substring(scheme + 3);
    } else if (value.contains(":") && !value.matches("^[^/]+:\\d+.*")) {
      return null; // tg:, mailto: and other non-web links
    }
    int end = value.length();
    for (char stop : new char[] {'/', '?', '#'}) {
      int index = value.indexOf(stop);
      if (index != -1 && index < end) {
        end = index;
      }
    }
    String authority = value.substring(0, end);
    int at = authority.lastIndexOf('@');
    if (at != -1) {
      authority = authority.substring(at + 1);
    }
    int port = authority.lastIndexOf(':');
    if (port != -1 && !authority.endsWith("]")) {
      authority = authority.substring(0, port);
    }
    String host = authority.toLowerCase(Locale.ROOT);
    if (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    if (host.startsWith("www.")) {
      host = host.substring(4);
    }
    return host.isEmpty() ? null : host;
  }

  private static boolean hasCredentials (String url) {
    String value = url.contains("://") ? url.substring(url.indexOf("://") + 3) : url;
    int end = value.length();
    for (char stop : new char[] {'/', '?', '#'}) {
      int index = value.indexOf(stop);
      if (index != -1 && index < end) {
        end = index;
      }
    }
    return value.substring(0, end).contains("@");
  }

  /** "login.itau.com.br" → "itau.com.br"; "golpe-itau.com" → "golpe-itau.com". */
  static String registeredDomain (String host) {
    String[] labels = host.split("\\.");
    if (labels.length >= 3 && labels[labels.length - 1].equals("br") && BR_SECOND_LEVEL.contains(labels[labels.length - 2])) {
      return labels[labels.length - 3] + "." + labels[labels.length - 2] + ".br";
    }
    if (labels.length >= 2) {
      return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }
    return host;
  }
}

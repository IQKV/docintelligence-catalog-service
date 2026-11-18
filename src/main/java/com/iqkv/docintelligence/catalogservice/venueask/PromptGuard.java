/*
 * Copyright 2026 iQKV Foundation Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iqkv.docintelligence.catalogservice.venueask;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Heuristic detector for prompt-injection attempts and non-English input.
 *
 * <p>Text is normalised (Unicode NFKC, zero-width and bidi controls removed) before being
 * matched against ordered rule groups covering instruction override, system-prompt
 * exfiltration, persona reset, role-delimiter smuggling and encoding probes. The rule
 * vocabulary covers English, German, French, Russian and Bulgarian.
 *
 * <p>Injection rules are evaluated before the script gate, so an attack written in a
 * Cyrillic language is reported as the attack it is rather than as plain foreign text.
 * Everything else that is not Latin script is reported as {@link Violation#NON_ENGLISH}.
 *
 * <p>This raises the cost of an attack, it does not make one impossible.
 * Recognising <em>which</em> Latin-script language a message is written in is out of scope:
 * a legitimate German or French sentence carrying no injection keyword passes through.
 * shortcut: keyword rules over a language classifier — upgrade to a model-backed
 * classifier once adversarial traffic is observed in production.
 */
public final class PromptGuard {

  /**
   * Shortest system-prompt fragment treated as a leak when echoed verbatim in a reply.
   */
  private static final int MIN_LEAK_FRAGMENT_CHARS = 40;

  private static final Pattern INVISIBLE =
      Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]");

  /**
   * Declared before {@link #RULES} — static initialisers run in source order, and the rules
   * need these flags at construction time. {@code UNICODE_CHARACTER_CLASS} is what makes
   * {@code \b} and {@code \w} aware of Cyrillic, so the word boundaries work in every language.
   */
  private static final int FLAGS =
      Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

  /**
   * Any letter outside the Latin script: Cyrillic, Greek, CJK, Arabic, Hebrew and so on.
   * The assistant is English-only, so this separates a language problem from an injection one.
   */
  private static final Pattern NON_LATIN_SCRIPT = Pattern.compile("[\\p{L}&&[^\\p{IsLatin}]]");

  // ---------------------------------------------------------------------------------
  // Multilingual rule vocabulary — English, German, French, Russian, Bulgarian.
  // Latin entries are stem-matched with a leading \b where a false positive is likely;
  // Cyrillic entries are bare stems. Trailing boundaries are deliberately omitted so
  // compounds such as the German "Systemprompt" still resolve into a reference plus a noun.
  // ---------------------------------------------------------------------------------

  /** Verbs asking the model to drop or replace what it was told. */
  private static final String OVERRIDE_VERBS =
      "\\b(?:ignore|disregard|forget|override|bypass|skip)\\b"
      + "|\\b(?:ignorier|vergiss|vergessen|missacht|umgeh|aufheb)"
      + "|\\b(?:ignor|oubl|passer outre)"
      + "|игнорир|забуд|забыть|обойд"
      + "|игнорир|забрав|забрави|заобикол";

  /** Words marking which instructions are meant: previous, original, all, and so on. */
  private static final String PRIOR_QUALIFIERS =
      "\\b(?:previous|prior|earlier|above|initial|original|system|all|these)\\b"
      + "|\\b(?:vorherig|obig|ursprüng|alle|bisherig)"
      + "|\\b(?:précédent|precedent|ci-dessus|toutes|tous)"
      + "|предыдущ|прежн|выше|исходн|все"
      + "|предишн|горн|всички|първоначалн";

  /** Nouns naming the text the model was given. */
  private static final String INSTRUCTION_NOUNS =
      "(?:instruction|prompt|rule|direction|guideline|message)s?\\b"
      + "|(?:anweisung|regel|richtlinie|vorgabe|instruktion)"
      + "|(?:instruction|consigne|règle|regle|directive|message)"
      + "|инструкц|указани|правил|промпт|директив|сообщени"
      + "|инструкц|указани|правил|промпт|директив|съобщени|заповед";

  /** Verbs asking the model to hand over what it was told. */
  private static final String EXFIL_VERBS =
      "\\b(?:reveal|show|print|repeat|output|display|expose|dump|leak|tell)\\b"
      + "|\\b(?:zeig|druck|wiederhol|verrat|offenbar|nenne)"
      + "|\\b(?:montr|affiche|imprime|répète|repete|révèle|revele|donne)"
      + "|покаж|показать|вывед|вывести|напечата|повтор|расскаж|раскро|назови"
      + "|покаж|извед|отпечата|повтор|разкри|кажи";

  /** References to the protected instructions the model holds. */
  private static final String SYSTEM_REFS =
      "\\b(?:system|initial|original|hidden|secret|developer|internal)"
      + "|\\b(?:anfang|ursprüng|versteckt|geheim|intern)"
      + "|\\b(?:système|systeme|initial|original|caché|cache|secret|interne)"
      + "|системн|исходн|первоначальн|скрыт|секретн|внутренн"
      + "|системн|първоначалн|скрит|тайн|вътрешн";

  /** Phrases trying to replace the assistant's identity or lift its limits. */
  private static final String RESET_PHRASES =
      "\\b(?:you are now|from now on you|pretend (?:to be|you are)|developer mode"
      + "|jailbreak|do anything now|dan mode|new (?:persona|personality|instructions)"
      + "|(?:without|no) restrictions|ignore your restrictions)\\b"
      + "|\\b(?:du bist jetzt|ab jetzt bist du|entwicklermodus|keine einschränkungen"
      + "|ohne einschränkungen|neue anweisungen|tu so als)\\b"
      + "|\\b(?:tu es maintenant|à partir de maintenant|mode développeur"
      + "|sans restrictions|aucune restriction|nouvelles instructions|fais comme si)\\b"
      + "|теперь ты|с этого момента|представь что ты|притворись|режим разработчика"
      + "|без ограничени|новые инструкции|новые правила"
      + "|сега ти си|от сега нататък|представи си че|режим на разработчик"
      + "|без ограничени|нови инструкции";

  private static final List<Rule> RULES = List.of(
      rule("instruction-override", eitherOrder(OVERRIDE_VERBS, PRIOR_QUALIFIERS, INSTRUCTION_NOUNS)),
      rule("system-prompt-exfiltration", eitherOrder(EXFIL_VERBS, SYSTEM_REFS, INSTRUCTION_NOUNS)),
      rule("system-prompt-exfiltration",
          "\\bwhat (?:are|were) your (?:system )?(?:prompt|instructions|rules|directives)\\b"),
      rule("system-prompt-exfiltration",
          "\\b(?:repeat|reprint|echo|copy)\\b[^.!?]{0,30}?\\b(?:verbatim|word for word|word-for-word)\\b"),
      rule("persona-reset", RESET_PHRASES),
      multilineRule("role-delimiter-smuggling",
          "(<\\|im_(?:start|end)\\|>|<\\|(?:system|user|assistant|endoftext|start_header_id|eot_id)\\|>"
          + "|\\[/?INST\\]|<<\\/?SYS>>|<\\/?system>"
          + "|^\\s*(?:system|assistant|developer)\\s*:|#{3,}\\s*(?:system|instruction))"),
      rule("encoding-probe",
          "(\\b[A-Za-z0-9+/]{200,}={0,2}\\b|(?:\\\\u[0-9a-fA-F]{4}){8,}|(?:\\\\x[0-9a-fA-F]{2}){8,})")
  );

  private PromptGuard() {
  }

  /**
   * Inspects one user message for prompt-injection attempts and non-English text.
   *
   * @param content raw user message, may be null
   * @return the verdict; never null
   */
  public static Verdict inspect(final String content) {
    if (content == null || content.isBlank()) {
      return Verdict.allowed();
    }
    final var normalised = normalise(content);
    for (final var rule : RULES) {
      if (rule.pattern().matcher(normalised).find()) {
        return new Verdict(Violation.INJECTION, rule.name());
      }
    }
    if (NON_LATIN_SCRIPT.matcher(normalised).find()) {
      return new Verdict(Violation.NON_ENGLISH, "non-english-input");
    }
    return Verdict.allowed();
  }

  /**
   * True when the message carries no visible text once invisible characters are stripped.
   *
   * <p>Input such as a zero-width space or another format character passes a plain blank
   * check but normalises to nothing, so the model would receive an empty turn.
   *
   * @param content raw user message, may be null
   * @return true when there is nothing meaningful to send
   */
  public static boolean isEffectivelyBlank(final String content) {
    return content == null || normalise(content).isBlank();
  }

  /**
   * Detects a reply that echoes the system prompt back to the user.
   *
   * <p>Any sentence of the system prompt at least forty characters long that reappears verbatim
   * in the reply is treated as a leak. Real answers never quote a whole instruction sentence, so
   * the threshold keeps false positives negligible.
   *
   * @param reply        model output, may be null
   * @param systemPrompt the full system prompt sent with the request, may be null
   * @return true when the reply appears to contain verbatim system-prompt text
   */
  public static boolean leaksSystemPrompt(final String reply, final String systemPrompt) {
    if (reply == null || systemPrompt == null || systemPrompt.isBlank()) {
      return false;
    }
    final var haystack = normalise(reply).toLowerCase(Locale.ROOT);
    return Arrays.stream(systemPrompt.split("[.!?\\n]"))
        .map(PromptGuard::normalise)
        .map(String::strip)
        .filter(fragment -> fragment.length() >= MIN_LEAK_FRAGMENT_CHARS)
        .anyMatch(fragment -> haystack.contains(fragment.toLowerCase(Locale.ROOT)));
  }

  private static String normalise(final String input) {
    return INVISIBLE.matcher(Normalizer.normalize(input, Normalizer.Form.NFKC)).replaceAll("");
  }

  private static Rule rule(final String name, final String regex) {
    return new Rule(name, Pattern.compile(regex, FLAGS));
  }

  private static Rule multilineRule(final String name, final String regex) {
    return new Rule(name, Pattern.compile(regex, FLAGS | Pattern.MULTILINE));
  }

  /**
   * Matches {@code lead}, then the remaining two parts in either order.
   * English puts the qualifier first ("previous instructions") while French puts it last
   * ("instructions précédentes"), so both orders have to be accepted.
   */
  private static String eitherOrder(final String lead, final String first, final String second) {
    return "(?:" + lead + ")[^.!?]{0,40}?(?:(?:" + first + ")[^.!?]{0,20}?(?:" + second + ")"
        + "|(?:" + second + ")[^.!?]{0,20}?(?:" + first + "))";
  }

  private record Rule(String name, Pattern pattern) {
  }

  /**
   * What a message violated.
   */
  public enum Violation {

    /** Nothing — the message may be sent to the model. */
    NONE,

    /** A prompt-injection attempt; the request is rejected. */
    INJECTION,

    /** Not English; the assistant answers with a fixed English-only reply. */
    NON_ENGLISH
  }

  /**
   * Outcome of a guard inspection.
   *
   * @param violation what was violated, never null
   * @param rule      name of the matched rule, null when nothing matched
   */
  public record Verdict(Violation violation, String rule) {

    private static final Verdict ALLOWED = new Verdict(Violation.NONE, null);

    public static Verdict allowed() {
      return ALLOWED;
    }

    /** True when the message must be rejected with an error rather than answered. */
    public boolean blocked() {
      return violation == Violation.INJECTION;
    }

    /** True when the message is not English and must not reach the model. */
    public boolean nonEnglish() {
      return violation == Violation.NON_ENGLISH;
    }
  }
}

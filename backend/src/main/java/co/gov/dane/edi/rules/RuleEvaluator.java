package co.gov.dane.edi.rules;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Motor autoritativo de saltos y validaciones (spec §5.2). Debe producir lo mismo que {@code
 * frontend/src/features/survey/rules/rule-evaluator.ts} sobre {@code shared/rules-fixtures}.
 */
public final class RuleEvaluator {

  private static final Pattern INTEGER = Pattern.compile("-?\\d+");
  private static final Pattern DECIMAL = Pattern.compile("-?\\d+(\\.\\d+)?");

  private RuleEvaluator() {}

  public static Evaluation evaluate(
      RuleSet rules, Map<String, String> answers, List<String> itemColumns) {
    SortedSet<String> disabled = new TreeSet<>();
    for (int round = 0; round <= rules.skips().size(); round++) {
      Map<String, String> effective = new HashMap<>(answers);
      disabled.forEach(effective::remove);
      SortedSet<String> next = new TreeSet<>();
      for (SkipRule rule : rules.skips()) {
        if (matches(rule.when(), effective)) {
          next.addAll(rule.then().questions());
        }
      }
      if (next.equals(disabled)) {
        break;
      }
      disabled = next;
    }
    SortedMap<String, String> errors = new TreeMap<>();
    for (String column : itemColumns) {
      if (disabled.contains(column)) {
        continue;
      }
      FieldValidation validation = rules.validations().get(column);
      validate(answers.get(column), validation).ifPresent(code -> errors.put(column, code));
    }
    return new Evaluation(disabled, errors);
  }

  private static Optional<String> validate(String rawValue, FieldValidation validation) {
    if (isBlank(rawValue)) {
      boolean required = validation == null || validation.isRequired();
      return required ? Optional.of(RuleErrorCodes.REQUIRED) : Optional.empty();
    }
    if (validation == null) {
      return Optional.empty();
    }
    String value = rawValue.strip();
    if ("entero".equals(validation.type()) && !INTEGER.matcher(value).matches()
        || "decimal".equals(validation.type()) && !DECIMAL.matcher(value).matches()) {
      return Optional.of(RuleErrorCodes.INVALID_TYPE);
    }
    if (validation.min() != null || validation.max() != null) {
      Optional<BigDecimal> number = toNumber(value);
      if (number.isEmpty()) {
        return Optional.of(RuleErrorCodes.INVALID_TYPE);
      }
      if (validation.min() != null && number.get().compareTo(validation.min()) < 0
          || validation.max() != null && number.get().compareTo(validation.max()) > 0) {
        return Optional.of(RuleErrorCodes.OUT_OF_RANGE);
      }
    }
    if (validation.maxLength() != null && value.length() > validation.maxLength()) {
      return Optional.of(RuleErrorCodes.TOO_LONG);
    }
    return Optional.empty();
  }

  static boolean matches(Condition condition, Map<String, String> answers) {
    if (condition.and() != null) {
      return condition.and().stream().allMatch(child -> matches(child, answers));
    }
    if (condition.or() != null) {
      return condition.or().stream().anyMatch(child -> matches(child, answers));
    }
    Predicate<String> test = column -> applies(condition, answers.get(column));
    if (condition.question() != null) {
      return test.test(condition.question());
    }
    if (condition.all() != null) {
      return condition.all().stream().allMatch(test);
    }
    return condition.any() != null && condition.any().stream().anyMatch(test);
  }

  private static boolean applies(Condition condition, String rawValue) {
    String value = isBlank(rawValue) ? "" : rawValue.strip();
    List<String> values = condition.values() == null ? List.of() : condition.values();
    return switch (condition.op()) {
      case "eq" -> value.equals(condition.value());
      case "ne" -> !value.isEmpty() && !value.equals(condition.value());
      case "in" -> values.contains(value);
      case "notIn" -> !value.isEmpty() && !values.contains(value);
      case "gt" -> compare(value, condition.value()).map(c -> c > 0).orElse(false);
      case "lt" -> compare(value, condition.value()).map(c -> c < 0).orElse(false);
      case "empty" -> value.isEmpty();
      case "notEmpty" -> !value.isEmpty();
      default -> false;
    };
  }

  private static Optional<Integer> compare(String left, String right) {
    Optional<BigDecimal> a = toNumber(left);
    Optional<BigDecimal> b = toNumber(right);
    return a.isPresent() && b.isPresent()
        ? Optional.of(a.get().compareTo(b.get()))
        : Optional.empty();
  }

  private static Optional<BigDecimal> toNumber(String value) {
    if (value == null || !DECIMAL.matcher(value.strip()).matches()) {
      return Optional.empty();
    }
    return Optional.of(new BigDecimal(value.strip()));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}

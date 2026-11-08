package co.gov.dane.edi.operation.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CredentialGeneratorTest {

  @Test
  void valuesHaveEightUppercaseLetters() {
    CredentialGenerator generator = new CredentialGenerator(new Random(20261107L));
    for (int index = 0; index < 1_000; index++) {
      assertThat(generator.next()).hasSize(8).matches("[A-Z]{8}");
    }
  }

  @Test
  void tenThousandValuesUseAllTwentySixLetters() {
    CredentialGenerator generator = new CredentialGenerator(new Random(42L));
    Set<Character> letters = new HashSet<>();
    for (int index = 0; index < 10_000; index++) {
      for (char letter : generator.next().toCharArray()) {
        letters.add(letter);
      }
    }
    assertThat(letters).hasSize(26);
  }

  @Test
  void sameSeedGivesTheSameSequence() {
    CredentialGenerator first = new CredentialGenerator(new Random(7L));
    CredentialGenerator second = new CredentialGenerator(new Random(7L));
    for (int index = 0; index < 10; index++) {
      assertThat(first.next()).isEqualTo(second.next());
    }
  }

  @Test
  void randomIsRequired() {
    assertThatThrownBy(() -> new CredentialGenerator(null))
        .isInstanceOf(NullPointerException.class);
  }
}

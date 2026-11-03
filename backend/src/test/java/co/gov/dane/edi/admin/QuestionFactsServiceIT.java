package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;

import co.gov.dane.edi.model.ColumnStatus;
import co.gov.dane.edi.model.InputType;
import co.gov.dane.edi.rules.QuestionFacts;
import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuestionFactsServiceIT extends OracleIntegrationTest {

  @Autowired private QuestionFactsService factsService;

  @Test
  void factsForDevYear() {
    Map<String, QuestionFacts> facts = factsService.facts("EDIN", 2026);

    QuestionFacts c01a = facts.get("C01A");
    assertThat(c01a.pageCode()).isEqualTo("C1");
    assertThat(c01a.inputType()).isEqualTo(InputType.SINGLE_CHOICE);
    assertThat(c01a.optionValues()).containsExactly("1", "2");
    assertThat(c01a.columnStatus()).isEqualTo(ColumnStatus.ACTIVE);
    assertThat(c01a.capacity().dataType()).isEqualTo("VARCHAR2");
    assertThat(c01a.capacity().charLength()).isEqualTo(1);
    assertThat(facts.get("C01B").inputType()).isEqualTo(InputType.SINGLE_CHOICE);
    assertThat(facts.get("B06").inputType()).isEqualTo(InputType.TEXT);
    assertThat(facts.get("B06").capacity().charLength()).isEqualTo(10);
  }

  @Test
  void factsFollowPositionOrderAcrossPages() {
    Map<String, QuestionFacts> facts = factsService.facts("EDIN", 2026);

    List<String> order = List.copyOf(facts.keySet());
    assertThat(order.indexOf("B02")).isLessThan(order.indexOf("C01A"));
    assertThat(order.indexOf("C01A")).isLessThan(order.indexOf("C01B"));
    assertThat(order.indexOf("C01B")).isLessThan(order.indexOf("C13"));
    for (int index = 0; index < order.size(); index++) {
      assertThat(facts.get(order.get(index)).position()).isEqualTo(index);
    }
  }

  @Test
  void questionWithoutColumnIsPendingWithoutCapacity() {
    QuestionFacts pending = factsService.facts("EDIN", 2026).get("B99");

    assertThat(pending.columnStatus()).isEqualTo(ColumnStatus.PENDING_COLUMN);
    assertThat(pending.capacity()).isNull();
  }
}

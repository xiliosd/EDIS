package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FrmIdAllocatorTest {

  @Test
  void refusesTheCompositeKeyTableBeforeTouchingTheDatabase() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);

    assertThatThrownBy(() -> new FrmIdAllocator(jdbc).next(FrmTable.RTA_GRUPO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("FRM_RTA_GRUPO");
    verifyNoInteractions(jdbc);
  }

  @Test
  void compositeKeyTableIsNotPartOfTheSnapshotNorFrmSql() {
    assertThat(FrmSnapshot.TABLES).doesNotContain(FrmTable.RTA_GRUPO);
  }
}

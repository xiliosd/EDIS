package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.gov.dane.edi.support.OracleIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class FrmIdAllocatorIT extends OracleIntegrationTest {

  private static final long BASE = 910000;
  private static final long HOLD_MILLIS = 1500;

  @Autowired private FrmIdAllocator allocator;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM FRM_PREGUNTA WHERE COD_PRE >= ?", BASE);
  }

  private long currentMaximum() {
    return jdbc.queryForObject("SELECT NVL(MAX(COD_PRE), 0) FROM FRM_PREGUNTA", Long.class);
  }

  private long allocateAndInsert(String text, Runnable afterInsert) {
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> {
              long id = allocator.next(FrmTable.PREGUNTA);
              jdbc.update(
                  "INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES (?, ?, 'L')",
                  id,
                  text);
              afterInsert.run();
              return id;
            });
  }

  @Test
  void allocatorReturnsMaxPlusOneAndIsSerialized() throws Exception {
    jdbc.update("INSERT INTO FRM_PREGUNTA (COD_PRE, PREGUNTA, TIPO) VALUES (?, 'x', 'L')", BASE);
    long expectedFirst = currentMaximum() + 1;
    CountDownLatch firstHoldsLock = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Long> first =
          pool.submit(
              () ->
                  allocateAndInsert(
                      "a",
                      () -> {
                        firstHoldsLock.countDown();
                        pause(HOLD_MILLIS);
                      }));
      assertThat(firstHoldsLock.await(30, TimeUnit.SECONDS)).isTrue();
      long secondStarted = System.nanoTime();
      Future<Long> second = pool.submit(() -> allocateAndInsert("b", () -> {}));

      assertThat(first.get(60, TimeUnit.SECONDS)).isEqualTo(expectedFirst);
      assertThat(second.get(60, TimeUnit.SECONDS)).isEqualTo(expectedFirst + 1);
      // La segunda transacción esperó el bloqueo mientras la primera seguía abierta.
      long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - secondStarted);
      assertThat(waitedMillis).isGreaterThanOrEqualTo(HOLD_MILLIS - 500);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void requiresAnActiveTransaction() {
    assertThatThrownBy(() -> allocator.next(FrmTable.PREGUNTA))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void worksOnEveryTableOfTheEnum() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              for (FrmTable table : FrmTable.values()) {
                assertThat(allocator.next(table)).isPositive();
              }
            });
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}

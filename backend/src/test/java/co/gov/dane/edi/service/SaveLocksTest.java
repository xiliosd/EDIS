package co.gov.dane.edi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SaveLocksTest {

  @Test
  void serializesActionsForTheSameKey() throws Exception {
    SaveLocks locks = new SaveLocks();
    AtomicInteger inside = new AtomicInteger();
    AtomicInteger maxInside = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 8; i++) {
      pool.submit(
          () -> {
            start.await();
            return locks.withLock(
                "1000000001",
                () -> {
                  maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                  Thread.onSpinWait();
                  return inside.decrementAndGet();
                });
          });
    }
    start.countDown();
    pool.shutdown();
    assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    assertThat(maxInside.get()).isEqualTo(1);
  }

  @Test
  void returnsTheActionResult() {
    assertThat(new SaveLocks().withLock("x", () -> 42)).isEqualTo(42);
  }
}

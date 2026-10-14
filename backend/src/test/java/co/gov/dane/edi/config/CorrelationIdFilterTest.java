package co.gov.dane.edi.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();

  @Test
  void generatesIdWhenRequestHasNone() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> seenInMdc = new AtomicReference<>();
    filter.doFilter(new MockHttpServletRequest(), response, capturing(seenInMdc));

    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    assertThat(seenInMdc.get()).isEqualTo(response.getHeader(CorrelationIdFilter.HEADER));
    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void keepsSafeIncomingId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "abc12345-front");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc12345-front");
  }

  @Test
  void replacesIdThatCouldForgeLogLines() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.HEADER, "abc12345\nNIVEL=ERROR falso");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    assertThat(response.getHeader(CorrelationIdFilter.HEADER)).doesNotContain("\n").hasSize(36);
  }

  private static MockFilterChain capturing(AtomicReference<String> seenInMdc) {
    return new MockFilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        seenInMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));
      }
    };
  }
}

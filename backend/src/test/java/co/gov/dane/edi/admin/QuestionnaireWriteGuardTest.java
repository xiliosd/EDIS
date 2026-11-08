package co.gov.dane.edi.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

class QuestionnaireWriteGuardTest {

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void startupMessageSaysOperationalWritesStillWork(CapturedOutput output) {
    new QuestionnaireWriteGuard(new QuestionnaireWritesProperties(false));

    assertThat(output.getAll())
        .contains("Cuestionario en solo lectura")
        .contains("cortes de cobertura, novedades y claves");
  }

  private static final QuestionnaireWriteGuard READ_ONLY =
      new QuestionnaireWriteGuard(new QuestionnaireWritesProperties(false));
  private static final QuestionnaireWriteGuard WRITABLE =
      new QuestionnaireWriteGuard(new QuestionnaireWritesProperties(true));

  @Test
  void readOnlyModeRejectsEveryMutatingMapping() throws Exception {
    for (String name : new String[] {"post", "put", "patch", "delete", "requestPost"}) {
      HandlerMethod handler = handler(name);
      assertThat(QuestionnaireWriteGuard.isWrite(handler)).as(name).isTrue();
      assertThatThrownBy(() -> READ_ONLY.preHandle(request("POST"), response(), handler))
          .as(name)
          .isInstanceOf(QuestionnaireReadOnlyException.class);
    }
  }

  @Test
  void readOnlyModeLetsReadsThrough() throws Exception {
    for (String name : new String[] {"get", "requestGet"}) {
      HandlerMethod handler = handler(name);
      assertThat(QuestionnaireWriteGuard.isWrite(handler)).as(name).isFalse();
      assertThat(READ_ONLY.preHandle(request("GET"), response(), handler)).as(name).isTrue();
    }
  }

  @Test
  void readOnlyModeLetsTheExemptPackageImportThrough() throws Exception {
    HandlerMethod handler = handler("exemptPost");

    assertThat(QuestionnaireWriteGuard.isWrite(handler)).isFalse();
    assertThat(READ_ONLY.preHandle(request("POST"), response(), handler)).isTrue();
  }

  /** Un @RequestMapping sin método acepta POST: se trata como escritura. */
  @Test
  void mappingWithoutMethodsCountsAsWrite() throws Exception {
    assertThat(QuestionnaireWriteGuard.isWrite(handler("anyMethod"))).isTrue();
  }

  @Test
  void writableModeLetsEverythingThrough() throws Exception {
    for (String name : new String[] {"post", "put", "patch", "delete", "get"}) {
      assertThat(WRITABLE.preHandle(request("POST"), response(), handler(name))).as(name).isTrue();
    }
  }

  /** Recursos estáticos u otros manejadores que no son métodos de controlador no se revisan. */
  @Test
  void ignoresHandlersThatAreNotControllerMethods() throws Exception {
    assertThat(READ_ONLY.preHandle(request("POST"), response(), new Object())).isTrue();
  }

  @Test
  void coversTheWholeAdminApiOnly() {
    assertThat(QuestionnaireWriteGuard.covers(List.of("/api/v1/admin/years/{encuesta}"))).isTrue();
    assertThat(QuestionnaireWriteGuard.covers(List.of("/api/v1/admin/environment"))).isTrue();
    assertThat(
            QuestionnaireWriteGuard.covers(
                List.of("/api/v1/admin/questionnaire/{encuesta}/{anio}/rules/import")))
        .isTrue();
    assertThat(QuestionnaireWriteGuard.covers(List.of("/api/v1/survey/finish"))).isFalse();
    assertThat(QuestionnaireWriteGuard.covers(List.of("/api/v1/auth/login"))).isFalse();
    assertThat(QuestionnaireWriteGuard.covers(List.of("/api/v1/administrador"))).isFalse();
  }

  @Test
  void exceptionCarriesTheUserFacingMessage() {
    assertThat(new QuestionnaireReadOnlyException().getMessage())
        .isEqualTo(
            "En este ambiente el cuestionario es de solo lectura: los cambios se hacen en QA y"
                + " llegan con un paquete de promoción.");
  }

  private static HandlerMethod handler(String name) throws NoSuchMethodException {
    Method method = SampleController.class.getDeclaredMethod(name);
    return new HandlerMethod(new SampleController(), method);
  }

  private static MockHttpServletRequest request(String method) {
    return new MockHttpServletRequest(method, "/api/v1/admin/questionnaire/EDIN/2026");
  }

  private static MockHttpServletResponse response() {
    return new MockHttpServletResponse();
  }

  @SuppressWarnings("unused")
  static class SampleController {
    @GetMapping
    void get() {}

    @RequestMapping(method = RequestMethod.GET)
    void requestGet() {}

    @PostMapping
    void post() {}

    @PutMapping
    void put() {}

    @PatchMapping
    void patch() {}

    @DeleteMapping
    void delete() {}

    @RequestMapping(method = RequestMethod.POST)
    void requestPost() {}

    @RequestMapping
    void anyMethod() {}

    @AllowedInReadOnlyQuestionnaire
    @PostMapping
    void exemptPost() {}
  }
}

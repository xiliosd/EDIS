// Prueba de carga del flujo del encuestado (ver loadtest/README.md). Datos FICTICIOS.
// Flujo: csrf -> login -> reglas -> avance -> (GET página + PUT respuestas) x 9 -> terminar.
import http from 'k6/http';
import { check, fail, group, sleep } from 'k6';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const USER_COUNT = Number(__ENV.USER_COUNT || 200);
// Pausa de "lectura" por página: una persona real no responde en milisegundos.
const THINK_MIN_SECONDS = Number(__ENV.THINK_MIN || 4);
const THINK_MAX_SECONDS = Number(__ENV.THINK_MAX || 8);
const PAGES = ['B1', 'C1', 'C2', 'D1', 'D2', 'E1', 'F1', 'G1', 'H1'];
// Valores válidos por columna de texto según la semilla de reglas (B06 entero 15–99; D05 VARCHAR2(2)).
const TEXT_ANSWERS = { B06: '30', D05: '5' };
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

export const options = {
  scenarios: {
    encuestados: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: __ENV.RAMP || '1m', target: Number(__ENV.VUS || 50) },
        { duration: __ENV.HOLD || '2m', target: Number(__ENV.VUS || 50) },
      ],
      gracefulRampDown: '0s',
      gracefulStop: '2m',
    },
  },
  // Informativos: el informe se genera aunque se incumplan.
  thresholds: {
    http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
    'http_req_duration{endpoint:put_answers}': [{ threshold: 'p(95)<1000', abortOnFail: false }],
    'http_req_duration{endpoint:get_page}': [{ threshold: 'p(95)<500', abortOnFail: false }],
    // Umbrales siempre verdaderos: solo obligan a k6 a reportar percentiles por endpoint.
    'http_req_duration{endpoint:csrf}': ['max>=0'],
    'http_req_duration{endpoint:login}': ['max>=0'],
    'http_req_duration{endpoint:rules}': ['max>=0'],
    'http_req_duration{endpoint:progress}': ['max>=0'],
    'http_req_duration{endpoint:finish}': ['max>=0'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

function jsonHeaders(extra) {
  return { headers: Object.assign({ 'Content-Type': 'application/json' }, extra || {}) };
}

// Igual que api-client.ts: el token de la cookie se reenvía en la cabecera en cada método no seguro.
function csrfHeader() {
  const cookies = http.cookieJar().cookiesForURL(BASE_URL + '/');
  const token = cookies[CSRF_COOKIE] && cookies[CSRF_COOKIE][0];
  return token ? { [CSRF_HEADER]: decodeURIComponent(token) } : {};
}

function tag(endpoint) {
  return { tags: { endpoint, name: endpoint } };
}

function withTags(params, endpoint) {
  return Object.assign({}, params, tag(endpoint));
}

// Construye respuestas válidas: primera opción en combos, "1" en casillas y valor fijo en textos.
function buildAnswers(page) {
  const answers = {};
  for (const entry of page.entries || []) {
    const spec = entry.answer;
    if (!spec || spec.columnStatus !== 'ACTIVE') continue;
    if (spec.inputType === 'SINGLE_CHOICE' && spec.options && spec.options.length > 0) {
      answers[spec.columnCode] = spec.options[0].value;
    } else if (spec.inputType === 'CHECKBOX') {
      answers[spec.columnCode] = '1';
    } else if (spec.inputType === 'TEXT') {
      answers[spec.columnCode] = TEXT_ANSWERS[spec.columnCode] || '1';
    }
  }
  return answers;
}

function userForIteration() {
  // Cada iteración usa un encuestado distinto (cada página se guarda una sola vez).
  const index = exec.scenario.iterationInTest;
  if (index >= USER_COUNT) {
    exec.test.abort(
      'Se agotaron los ' + USER_COUNT + ' usuarios CARGA; reduzca VUS/HOLD o suba THINK_MIN.',
    );
  }
  const number = String(index + 1).padStart(3, '0');
  return { username: 'CARGA' + number, password: 'Cg#' + number.padStart(5, '0') };
}

export default function () {
  http.cookieJar().clear(BASE_URL);
  const user = userForIteration();

  group('inicio de sesión', () => {
    const csrf = http.get(BASE_URL + '/api/v1/auth/csrf', tag('csrf'));
    if (!check(csrf, { 'csrf 200': (r) => r.status === 200 })) fail('sin token CSRF');
    const login = http.post(
      BASE_URL + '/api/v1/auth/login',
      JSON.stringify({ accountType: 'RESPONDENT', username: user.username, password: user.password }),
      withTags(jsonHeaders(csrfHeader()), 'login'),
    );
    if (!check(login, { 'login 200': (r) => r.status === 200 })) fail('login falló ' + login.status);
  });

  check(http.get(BASE_URL + '/api/v1/survey/rules', tag('rules')), {
    'reglas 200': (r) => r.status === 200,
  });
  check(http.get(BASE_URL + '/api/v1/survey/progress', tag('progress')), {
    'avance 200': (r) => r.status === 200,
  });

  for (const pageCode of PAGES) {
    const pageResponse = http.get(BASE_URL + '/api/v1/survey/pages/' + pageCode, tag('get_page'));
    if (!check(pageResponse, { 'página 200': (r) => r.status === 200 })) fail('GET ' + pageCode);
    sleep(THINK_MIN_SECONDS + Math.random() * (THINK_MAX_SECONDS - THINK_MIN_SECONDS));

    const saved = http.put(
      BASE_URL + '/api/v1/survey/pages/' + pageCode + '/answers',
      JSON.stringify({ answers: buildAnswers(pageResponse.json()) }),
      withTags(jsonHeaders(csrfHeader()), 'put_answers'),
    );
    if (!check(saved, { 'guardado 200': (r) => r.status === 200 })) {
      fail('PUT ' + pageCode + ' -> ' + saved.status + ' ' + saved.body);
    }
  }

  const finished = http.post(
    BASE_URL + '/api/v1/survey/finish',
    JSON.stringify({ observations: 'Observación ficticia de la prueba de carga.' }),
    withTags(jsonHeaders(csrfHeader()), 'finish'),
  );
  check(finished, { 'terminar 200': (r) => r.status === 200 });
}

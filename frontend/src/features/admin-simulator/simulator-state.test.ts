import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';
import { simulatorEntry, simulatorView } from '../../test/simulator-fixtures';
import type { RuleSet, SimulatorView, TestCase } from '../admin-rules/rules-api';
import { caseFromSimulation, simulatePage } from './simulator-state';

interface SimulatorFixture {
  reglas: RuleSet;
  columnas: string[];
  respuestas: Record<string, string>;
  caso: TestCase;
}

// El mismo archivo que CaseRunnerTest.sharedSimulatorFixturePasses ejecuta en Java como "pasa".
// import.meta.url no es de esquema file en jsdom; por eso se usa import.meta.dirname.
const FIXTURE_FILE =
  resolve(import.meta.dirname, '../../../../shared/rules-fixtures') + '/07-caso-simulador.json';

describe('simulator-state', () => {
  it('fixture 07 matches: el caso grabado es exactamente el del archivo compartido', () => {
    const fixture = JSON.parse(readFileSync(FIXTURE_FILE, 'utf-8')) as SimulatorFixture;
    const pageCode = fixture.caso.pagina ?? '';
    const view: SimulatorView = {
      rules: fixture.reglas,
      pages: [
        {
          pageCode,
          entries: fixture.columnas.map((column) =>
            simulatorEntry(column, pageCode, { inputType: 'TEXT' }),
          ),
        },
      ],
    };

    const result = simulatePage(view, pageCode, fixture.respuestas);
    const recorded = caseFromSimulation(
      fixture.caso.id ?? '',
      fixture.caso.nombre ?? '',
      pageCode,
      fixture.respuestas,
      result,
      view,
    );

    expect(recorded).toEqual(fixture.caso);
  });

  it('hidden indica qué reglas ocultan cada pregunta, también las de otras páginas', () => {
    const result = simulatePage(simulatorView(), 'C2', { C01A: '2' });

    expect(result.hidden).toEqual({ C01B: ['c01a-no'] });
    // Solo cuentan los errores de las preguntas activas de la página elegida.
    expect(result.errors).toEqual({ C11: 'REQUERIDA' });
  });

  it('caseFromSimulation descarta respuestas vacías y de columnas que no son ítems activos', () => {
    const view = simulatorView();
    view.pages![0].entries!.push(simulatorEntry('C99', 'C1', { columnStatus: 'PENDING_COLUMN' }));
    const answers = { C01A: '2', C01B: '  ', C03: '0', C99: '1', Z00: '5' };
    const result = simulatePage(view, 'C1', answers);

    const recorded = caseFromSimulation('c-1', 'Caso', 'C1', answers, result, view);

    expect(recorded.respuestas).toEqual({ C01A: '2', C03: '0' });
    expect(recorded.esperado).toEqual({ ocultas: ['C01B'], errores: {} });
  });
});

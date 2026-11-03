import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import type { AnalysisReport } from './rules-api';
import { AnalysisSummary, FINDING_HEADINGS } from './AnalysisSummary';

const REPORT: AnalysisReport = {
  errors: [
    {
      code: 'CICLO',
      element: 'regla:a-b',
      questions: ['C01A'],
      message: 'Hay un ciclo entre dos reglas.',
    },
    {
      code: 'RANGO_INVALIDO',
      element: 'validacion:C11',
      questions: ['C11'],
      message: 'El mínimo es mayor que el máximo.',
    },
  ],
  warnings: [
    {
      code: 'PENDIENTE_COLUMNA',
      element: 'pregunta:C12',
      questions: ['C12'],
      message: 'La columna no existe todavía.',
    },
    {
      code: 'NUNCA_SE_CUMPLE',
      element: 'caso:caso-1',
      questions: [],
      message: 'El caso no se cumple.',
    },
  ],
};

function renderSummary(report: AnalysisReport, rejected = false) {
  return render(
    <MemoryRouter>
      <AnalysisSummary report={report} survey="EDIN" year={2027} rejected={rejected} />
    </MemoryRouter>,
  );
}

describe('AnalysisSummary', () => {
  it('muestra primero los errores y luego las advertencias con encabezados en español', () => {
    renderSummary(REPORT);

    const headings = screen.getAllByRole('heading', { level: 3 }).map((item) => item.textContent);
    expect(headings).toEqual(['Errores (2)', 'Advertencias (2)']);
    const items = screen.getAllByRole('listitem').map((item) => item.textContent ?? '');
    expect(items[0]).toContain('Ciclo de saltos');
    expect(items[0]).toContain('Hay un ciclo entre dos reglas.');
    expect(items[1]).toContain('Rango inválido');
    expect(items[2]).toContain('Pregunta pendiente de columna');
    expect(items[3]).toContain('Regla que nunca se cumple');
  });

  it('traduce todos los códigos de hallazgo', () => {
    expect(FINDING_HEADINGS).toEqual({
      ESTRUCTURA: 'Error de estructura',
      PREGUNTA_INEXISTENTE: 'Pregunta inexistente o inactiva',
      CICLO: 'Ciclo de saltos',
      VALOR_NO_ES_OPCION: 'Valor que no es una opción',
      RANGO_INVALIDO: 'Rango inválido',
      RANGO_EXCEDE_COLUMNA: 'Rango mayor que la columna',
      VALIDACION_EN_OPCION: 'Validación de rango en una pregunta de opciones',
      NUNCA_SE_MUESTRA: 'Pregunta que nunca se muestra',
      NUNCA_SE_CUMPLE: 'Regla que nunca se cumple',
      NO_ANALIZABLE: 'No se pudo analizar',
      DESTINO_ANTES: 'Destino antes del disparador',
      REGLAS_SOLAPADAS: 'Reglas solapadas',
      PENDIENTE_COLUMNA: 'Pregunta pendiente de columna',
    });
  });

  it('enlaza cada hallazgo con su elemento', () => {
    renderSummary(REPORT);

    expect(screen.getByRole('link', { name: 'Ir a la regla a-b' })).toHaveAttribute(
      'href',
      '#regla-a-b',
    );
    expect(screen.getByRole('link', { name: 'Ir a la pregunta C11' })).toHaveAttribute(
      'href',
      '#pregunta-C11',
    );
    expect(screen.getByRole('link', { name: 'Ir a la pregunta C12' })).toHaveAttribute(
      'href',
      '#pregunta-C12',
    );
    expect(screen.getByRole('link', { name: 'Ir al caso caso-1' })).toHaveAttribute(
      'href',
      '/admin/cuestionario/EDIN/2027/simulador#caso-caso-1',
    );
  });

  it('indica cuando no hay hallazgos', () => {
    renderSummary({ errors: [], warnings: [] });

    expect(screen.getByText('El análisis no encontró problemas.')).toBeVisible();
  });

  it('un rechazo muestra solo errores en una alerta con el foco', () => {
    renderSummary(REPORT, true);

    const alert = screen.getByRole('alert');
    expect(alert).toHaveFocus();
    expect(alert).toHaveTextContent('No se guardó');
    expect(alert).toHaveTextContent('Ciclo de saltos');
    expect(alert).not.toHaveTextContent('Pregunta pendiente de columna');
  });
});

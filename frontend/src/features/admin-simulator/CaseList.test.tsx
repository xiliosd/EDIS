import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { CaseResult, TestCase } from '../admin-rules/rules-api';
import { CaseList } from './CaseList';

const CASES: TestCase[] = [
  { id: 'caso-ok', nombre: 'Caso que pasa', pagina: 'C1', respuestas: { C01A: '1' } },
  { id: 'caso-mal', nombre: 'Caso que falla', pagina: 'C1', respuestas: { C01A: '2' } },
  { id: 'caso-viejo', nombre: 'Caso por revisar', pagina: 'C2', respuestas: { C11: '5' } },
];
const RESULTS: CaseResult[] = [
  { id: 'caso-ok', status: 'pasa', differences: [] },
  { id: 'caso-mal', status: 'falla', differences: ['Se esperaba oculta C01B y está visible.'] },
  { id: 'caso-viejo', status: 'por_revisar', differences: [] },
];

function renderList(props: Partial<Parameters<typeof CaseList>[0]> = {}) {
  const onOpen = vi.fn();
  const onDelete = vi.fn();
  render(
    <CaseList
      cases={CASES}
      results={RESULTS}
      editable
      onOpen={onOpen}
      onDelete={onDelete}
      {...props}
    />,
  );
  return { onOpen, onDelete, user: userEvent.setup() };
}

function rowOf(id: string): HTMLElement {
  return document.getElementById(`caso-${id}`)!;
}

describe('CaseList', () => {
  it('muestra cada estado con texto, no solo con color, y las diferencias de lo que falla', () => {
    renderList();

    expect(within(rowOf('caso-ok')).getByText('Pasa')).toBeVisible();
    expect(within(rowOf('caso-mal')).getByText('Falla')).toBeVisible();
    expect(
      within(rowOf('caso-mal')).getByText('Se esperaba oculta C01B y está visible.'),
    ).toBeVisible();
    expect(within(rowOf('caso-viejo')).getByText('Por revisar')).toBeVisible();
    expect(rowOf('caso-ok')).toHaveAttribute('tabindex', '-1');
  });

  it('abre el caso en el simulador', async () => {
    const { onOpen, user } = renderList();

    await user.click(
      within(rowOf('caso-viejo')).getByRole('button', { name: /Abrir en el simulador/ }),
    );

    expect(onOpen).toHaveBeenCalledWith(CASES[2]);
  });

  it('pide confirmación antes de eliminar', async () => {
    const { onDelete, user } = renderList();

    await user.click(screen.getByRole('button', { name: 'Eliminar caso caso-mal' }));
    expect(onDelete).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Eliminar' }));

    expect(onDelete).toHaveBeenCalledWith(CASES[1]);
  });

  it('sin permiso de edición no deja eliminar', () => {
    renderList({ editable: false });

    expect(screen.getByRole('button', { name: 'Eliminar caso caso-ok' })).toBeDisabled();
  });

  it('sin casos lo dice', () => {
    renderList({ cases: [], results: [] });

    expect(screen.getByText('Este año todavía no tiene casos de prueba.')).toBeVisible();
  });
});

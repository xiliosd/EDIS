import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import axe from 'axe-core';
import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as downloadFile from '../../services/download-file';
import { server } from '../../test/msw-server';
import { renderApp } from '../../test/render-app';
import type { NoveltyReportRow, PerformanceRow } from './reports-api';

const XLSX = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const SCOPE = {
  allDepartments: true,
  canManageKeys: false,
  canManageNovelties: false,
  defaultYear: 2026,
  departments: [{ code: 5, name: 'Antioquia' }],
};
const OPTIONS = { rules: { 'color-contrast': { enabled: false } } };

function serveBase() {
  server.use(
    http.get('*/api/v1/auth/me', () =>
      HttpResponse.json({ accountType: 'ADMINISTRATOR', roles: ['ADMIN_NACIONAL'] }),
    ),
    http.get('*/api/v1/auth/csrf', () => HttpResponse.json({ token: 'x' })),
    http.get('*/api/v1/admin/operation/scope', () => HttpResponse.json(SCOPE)),
    http.get('*/api/v1/admin/reports/years', () => HttpResponse.json([2026])),
  );
}

function novelty(index: number): NoveltyReportRow {
  return {
    complete: index === 0,
    departmentCode: 5,
    departmentName: 'Antioquia',
    entityCode: 900001,
    entityName: 'Entidad Ficticia Uno',
    level: 1,
    name: `Persona Ficticia ${index}`,
    noveltyCode: 8,
    noveltyName: 'Otra causa',
    otherCause: index === 0 ? 'Licencia &#241; ficticia' : undefined,
    respondentCode: `${2000000 + index}`,
  };
}

function performance(index: number, withGaps: boolean): PerformanceRow {
  const chapters = ['B', 'C', 'D', 'E', 'F', 'G', 'H'].map((chapter) =>
    withGaps && chapter !== 'B' ? { chapter } : { chapter, date: '2026-11-05', time: '10:01:00' },
  );
  return {
    chapters,
    entityCode: 900001,
    entityName: 'Entidad Ficticia Uno',
    level: 1,
    name: `Persona Ficticia ${index}`,
    respondentCode: `${3000000 + index}`,
  };
}

afterEach(() => vi.restoreAllMocks());

describe('NoveltyReportPage', () => {
  it('showsRowsWithoutDecodingOtherCauseAndPagesOnTheClient', async () => {
    serveBase();
    const rows = Array.from({ length: 150 }, (_, index) => novelty(index));
    server.use(http.get('*/api/v1/admin/reports/novelties', () => HttpResponse.json(rows)));
    renderApp('/admin/reportes/novedades');
    await screen.findByRole('table', { name: /Funcionarios con novedad/ });

    expect(screen.getAllByRole('row')).toHaveLength(101);
    expect(screen.getByText('Licencia &#241; ficticia')).toBeInTheDocument();
    expect(screen.getByText(/Página 1 de 2/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();

    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));

    expect(await screen.findByText(/Página 2 de 2/)).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(51);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('downloadsTheExcel', async () => {
    serveBase();
    server.use(
      http.get('*/api/v1/admin/reports/novelties', () => HttpResponse.json([novelty(0)])),
      http.get(
        '*/api/v1/admin/reports/novelties/excel',
        () => new HttpResponse('x', { headers: { 'Content-Type': XLSX } }),
      ),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    renderApp('/admin/reportes/novedades');
    await screen.findByRole('table', { name: /Funcionarios con novedad/ });

    await userEvent.click(screen.getByRole('button', { name: 'Exportar a Excel' }));

    await waitFor(() => expect(download).toHaveBeenCalled());
  });

  it('hasNoAxeViolations', async () => {
    serveBase();
    server.use(http.get('*/api/v1/admin/reports/novelties', () => HttpResponse.json([novelty(0)])));
    renderApp('/admin/reportes/novedades');
    await screen.findByRole('table', { name: /Funcionarios con novedad/ });

    expect((await axe.run(document.body, OPTIONS)).violations).toEqual([]);
  });
});

describe('PerformancePage', () => {
  it('showsADashForChaptersNotSaved', async () => {
    serveBase();
    server.use(
      http.get('*/api/v1/admin/reports/performance', () =>
        HttpResponse.json([performance(0, true)]),
      ),
    );
    renderApp('/admin/reportes/rendimientos');
    await screen.findByRole('table', { name: /Rendimientos por capítulo/ });

    const row = screen.getByRole('row', { name: /Persona Ficticia 0/ });
    expect(within(row).getByText('05/11/2026 10:01:00')).toBeInTheDocument();
    expect(within(row).getAllByText('—')).toHaveLength(6);
  });

  it('pagesOnTheClientByHundreds', async () => {
    serveBase();
    const rows = Array.from({ length: 101 }, (_, index) => performance(index, false));
    server.use(http.get('*/api/v1/admin/reports/performance', () => HttpResponse.json(rows)));
    renderApp('/admin/reportes/rendimientos');
    await screen.findByRole('table', { name: /Rendimientos por capítulo/ });

    expect(screen.getAllByRole('row')).toHaveLength(101);
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(await screen.findByText(/Página 2 de 2/)).toBeInTheDocument();
    expect(screen.getAllByRole('row')).toHaveLength(2);
  });

  it('downloadsTheExcel', async () => {
    serveBase();
    server.use(
      http.get('*/api/v1/admin/reports/performance', () =>
        HttpResponse.json([performance(0, false)]),
      ),
      http.get(
        '*/api/v1/admin/reports/performance/excel',
        () => new HttpResponse('x', { headers: { 'Content-Type': XLSX } }),
      ),
    );
    const download = vi.spyOn(downloadFile, 'downloadBlobFile').mockImplementation(() => {});
    renderApp('/admin/reportes/rendimientos');
    await screen.findByRole('table', { name: /Rendimientos por capítulo/ });

    await userEvent.click(screen.getByRole('button', { name: 'Exportar a Excel' }));

    await waitFor(() => expect(download).toHaveBeenCalled());
  });

  it('hasNoAxeViolations', async () => {
    serveBase();
    server.use(
      http.get('*/api/v1/admin/reports/performance', () =>
        HttpResponse.json([performance(0, true)]),
      ),
    );
    renderApp('/admin/reportes/rendimientos');
    await screen.findByRole('table', { name: /Rendimientos por capítulo/ });

    expect((await axe.run(document.body, OPTIONS)).violations).toEqual([]);
  });
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from './ConfirmDialog';

describe('ConfirmDialog', () => {
  it('muestra el diálogo solo cuando está abierto y responde a cada botón', async () => {
    const onConfirm = vi.fn();
    const onCancel = vi.fn();
    const { rerender } = render(
      <ConfirmDialog
        open={false}
        title="¿Guardar?"
        confirmLabel="Sí"
        cancelLabel="No"
        onConfirm={onConfirm}
        onCancel={onCancel}
      >
        Texto
      </ConfirmDialog>,
    );
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    rerender(
      <ConfirmDialog
        open
        title="¿Guardar?"
        confirmLabel="Sí"
        cancelLabel="No"
        onConfirm={onConfirm}
        onCancel={onCancel}
      >
        Texto
      </ConfirmDialog>,
    );
    expect(screen.getByRole('dialog', { name: '¿Guardar?' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sí' }));
    await userEvent.click(screen.getByRole('button', { name: 'No' }));
    expect(onConfirm).toHaveBeenCalledOnce();
    expect(onCancel).toHaveBeenCalledOnce();
  });
});

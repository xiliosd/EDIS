import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
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

  it('describe el diálogo con su texto y no se cierra con Esc mientras guarda', () => {
    const onCancel = vi.fn();
    const { rerender } = render(
      <ConfirmDialog
        open
        busy
        title="¿Guardar?"
        confirmLabel="Sí"
        cancelLabel="No"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      >
        Texto
      </ConfirmDialog>,
    );
    const dialog = screen.getByRole('dialog', { name: '¿Guardar?' });
    expect(dialog).toHaveAccessibleDescription('Texto');

    const busyEscape = new Event('cancel', { cancelable: true });
    fireEvent(dialog, busyEscape);
    expect(busyEscape.defaultPrevented).toBe(true);
    expect(onCancel).not.toHaveBeenCalled();

    rerender(
      <ConfirmDialog
        open
        title="¿Guardar?"
        confirmLabel="Sí"
        cancelLabel="No"
        onConfirm={vi.fn()}
        onCancel={onCancel}
      >
        Texto
      </ConfirmDialog>,
    );
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(onCancel).toHaveBeenCalledOnce();
  });

  it('sin etiqueta de cancelar solo muestra el botón de confirmar', () => {
    render(
      <ConfirmDialog
        open
        title="Aviso"
        confirmLabel="Cerrar"
        onConfirm={vi.fn()}
        onCancel={vi.fn()}
      >
        Texto
      </ConfirmDialog>,
    );

    expect(screen.getAllByRole('button')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Cerrar' })).toBeInTheDocument();
  });

  describe('foco al cerrar', () => {
    function Harness({ unmountOnClose }: { unmountOnClose: boolean }) {
      const [open, setOpen] = useState(false);
      const dialog = (
        <ConfirmDialog
          open={open}
          title="¿Seguro?"
          confirmLabel="Aceptar"
          cancelLabel="Cancelar"
          onConfirm={() => setOpen(false)}
          onCancel={() => setOpen(false)}
        >
          Texto
        </ConfirmDialog>
      );
      return (
        <>
          <button onClick={() => setOpen(true)}>Abrir</button>
          {unmountOnClose ? open && dialog : dialog}
        </>
      );
    }

    it.each([
      ['cancelar', 'Cancelar', true],
      ['confirmar', 'Aceptar', true],
      ['cancelar', 'Cancelar', false],
      ['confirmar', 'Aceptar', false],
    ])(
      'devuelve el foco al botón que lo abrió al %s (desmontado: %s)',
      async (_n, label, unmount) => {
        render(<Harness unmountOnClose={unmount as boolean} />);
        const user = userEvent.setup();

        await user.click(screen.getByRole('button', { name: 'Abrir' }));
        await user.click(screen.getByRole('button', { name: label as string }));

        expect(screen.getByRole('button', { name: 'Abrir' })).toHaveFocus();
      },
    );
  });
});

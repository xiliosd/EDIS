import type { HTMLAttributes } from 'react';

interface ScrollableRegionProps extends HTMLAttributes<HTMLDivElement> {
  /** Nombre de la región (el de la tabla que contiene): los lectores de pantalla lo anuncian. */
  label: string;
}

/**
 * Contenedor con desplazamiento horizontal para tablas anchas. Es una región con nombre y entra
 * en el orden de tabulación: quien navega con teclado puede desplazarla (WCAG 2.1.1).
 */
export function ScrollableRegion({
  label,
  className = '',
  children,
  ...props
}: ScrollableRegionProps) {
  return (
    <div
      {...props}
      role="region"
      // Una región desplazable debe ser enfocable (regla axe scrollable-region-focusable).
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      aria-label={label}
      className={`overflow-x-auto focus-visible:outline-2 focus-visible:outline-primary ${className}`}
    >
      {children}
    </div>
  );
}

import { Link, type LinkProps } from 'react-router';

type LinkButtonVariant = 'primary' | 'secondary';

const VARIANT_CLASSES: Record<LinkButtonVariant, string> = {
  primary: 'bg-primary text-on-primary hover:bg-primary-strong',
  secondary: 'border border-primary text-primary hover:bg-primary/10',
};

/** Navegación con apariencia de botón: sigue siendo un enlace para el lector de pantalla. */
export function LinkButton({
  variant = 'primary',
  className = '',
  ...props
}: LinkProps & { variant?: LinkButtonVariant }) {
  return (
    <Link
      className={`inline-flex min-h-11 items-center justify-center rounded-lg px-5 font-medium transition-colors ${VARIANT_CLASSES[variant]} ${className}`}
      {...props}
    />
  );
}

import { TextField } from '../../../components/TextField';
import { fieldId, type QuestionProps } from './QuestionField';

function rangeHint(min?: number | null, max?: number | null): string {
  if (min != null && max != null) {
    return ` (entre ${min} y ${max})`;
  }
  if (min != null) {
    return ` (desde ${min})`;
  }
  return max != null ? ` (hasta ${max})` : '';
}

export function TextQuestion({ column, label, error, register, rules }: QuestionProps) {
  const validation = rules?.validaciones?.[column];
  const numeric = validation?.tipo === 'entero' || validation?.tipo === 'decimal';
  return (
    <TextField
      id={fieldId(column)}
      label={`${label}${rangeHint(validation?.min, validation?.max)}`}
      error={error}
      inputMode={numeric ? (validation?.tipo === 'entero' ? 'numeric' : 'decimal') : undefined}
      maxLength={validation?.longitudMax ?? undefined}
      autoComplete="off"
      {...register(column)}
    />
  );
}

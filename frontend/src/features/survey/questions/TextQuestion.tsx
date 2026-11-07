import { TextField } from '../../../components/TextField';
import { fieldId, useQuestionHelp, type QuestionProps } from './QuestionField';

function rangeHint(min?: number | null, max?: number | null): string {
  if (min != null && max != null) {
    return ` (entre ${min} y ${max})`;
  }
  if (min != null) {
    return ` (desde ${min})`;
  }
  return max != null ? ` (hasta ${max})` : '';
}

export function TextQuestion(props: QuestionProps) {
  const { column, error, register, rules } = props;
  const validation = rules?.validaciones?.[column];
  const numeric = validation?.tipo === 'entero' || validation?.tipo === 'decimal';
  // El término se busca solo en la pregunta: nunca dentro de "(entre 15 y 99)".
  const hint = rangeHint(validation?.min, validation?.max);
  const { content, details, accessibleName } = useQuestionHelp(props);
  return (
    <TextField
      id={fieldId(column)}
      label={
        <>
          {content}
          {hint}
        </>
      }
      afterLabel={details}
      aria-label={accessibleName && `${accessibleName}${hint}`}
      error={error}
      inputMode={numeric ? (validation?.tipo === 'entero' ? 'numeric' : 'decimal') : undefined}
      maxLength={validation?.longitudMax ?? undefined}
      autoComplete="off"
      {...register(column)}
    />
  );
}

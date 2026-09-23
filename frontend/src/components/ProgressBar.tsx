interface ProgressBarProps {
  value: number;
  label?: string;
  /** Renders the bar in the "late" tone; the caller decides what counts as late. */
  late?: boolean;
}

/** Progress bar with an accessible role so screen readers announce a real value, not a width. */
export function ProgressBar({ value, label, late = false }: ProgressBarProps) {
  const clamped = Math.min(100, Math.max(0, Math.round(value)));
  const modifier = clamped >= 100 ? ' progress__bar--done' : late ? ' progress__bar--late' : '';
  return (
    <div>
      <div
        className="progress"
        role="progressbar"
        aria-valuenow={clamped}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={label ?? 'Progress'}
      >
        <div className={`progress__bar${modifier}`} style={{ width: `${clamped}%` }} />
      </div>
      {label ? (
        <div className="row row--between small muted" style={{ marginTop: 4 }}>
          <span>{label}</span>
          <span>{clamped}%</span>
        </div>
      ) : null}
    </div>
  );
}

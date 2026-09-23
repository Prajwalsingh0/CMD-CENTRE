interface StatCardProps {
  label: string;
  value: number | string;
  foot?: string;
  tone?: 'default' | 'danger' | 'success';
}

/** Headline metric. The value is always a real number passed in from the API. */
export function StatCard({ label, value, foot, tone = 'default' }: StatCardProps) {
  const colour = tone === 'danger' ? 'var(--danger)' : tone === 'success' ? 'var(--success)' : undefined;
  return (
    <div className="stat">
      <div className="stat__label">{label}</div>
      <div className="stat__value" style={{ color: colour }}>
        {value}
      </div>
      {foot ? <div className="stat__foot">{foot}</div> : null}
    </div>
  );
}

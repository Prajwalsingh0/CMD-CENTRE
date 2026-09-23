import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { researchApi } from '../api/endpoints';
import type { ResearchResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';

function Section({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) {
    return null;
  }
  return (
    <section className="card">
      <h2 className="card__title">{title}</h2>
      <ul style={{ margin: 0, paddingLeft: 20 }}>
        {items.map((item) => (
          <li key={item} style={{ marginBottom: 4 }}>
            {item}
          </li>
        ))}
      </ul>
    </section>
  );
}

export function ResearchDetailPage() {
  const { id } = useParams<{ id: string }>();
  const reportId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);

  const { data, loading, error, reload } = useAsync<ResearchResponse>(() => researchApi.get(reportId), [reportId]);

  if (!Number.isFinite(reportId)) {
    return <ErrorState error={new Error('That report id is not a number.')} />;
  }
  if (loading) {
    return <LoadingState label="Loading report…" />;
  }
  if (error) {
    return <ErrorState error={error} onRetry={reload} />;
  }
  if (!data) {
    return null;
  }

  return (
    <div>
      <p className="small">
        <Link to="/research">← All reports</Link>
      </p>

      <div className="page-head">
        <div>
          <h1>{data.topic}</h1>
          <p className="muted">
            {data.provider} · {formatDateTime(data.createdAt)}
          </p>
        </div>
        <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
          Delete
        </button>
      </div>

      {/* The disclosure is rendered prominently, not buried: the reader must know exactly what
          this report is and is not based on. */}
      <div className={`alert ${data.grounded ? 'alert--success' : 'alert--warning'}`} style={{ marginBottom: 16 }}>
        <strong>{data.grounded ? 'Grounded in your documents' : 'Not grounded in any source'}</strong>
        <div style={{ marginTop: 4 }}>{data.disclosure}</div>
      </div>

      <div className="grid grid--sidebar">
        <div className="stack">
          <section className="card">
            <h2 className="card__title">Overview</h2>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.overview}</p>
          </section>

          <Section title="Key findings" items={data.keyFindings} />
          <Section title="Important concepts" items={data.concepts} />
          <Section title="Practical recommendations" items={data.recommendations} />

          <section className="card">
            <h2 className="card__title">Summary</h2>
            <p style={{ margin: 0 }}>{data.summary}</p>
          </section>
        </div>

        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Sources</h2>
            <Badge tone={data.grounded ? 'success' : 'neutral'}>{data.sources.length}</Badge>
          </div>
          {data.sources.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No passage matched this topic, so this report cites nothing. Upload a relevant document
              and create the report again to get citations.
            </p>
          ) : (
            <ul className="list">
              {data.sources.map((source) => (
                <li key={source} className="list__item">
                  <div className="list__main">
                    <div className="list__title">{source}</div>
                    <div className="list__meta">From your indexed documents</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Delete this report?"
        message={`“${data.topic}” will be removed permanently.`}
        onConfirm={async () => {
          await researchApi.remove(reportId);
          toast.success('Report deleted');
          navigate('/research');
        }}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}

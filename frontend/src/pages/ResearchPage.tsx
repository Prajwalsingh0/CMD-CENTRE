import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { researchApi } from '../api/endpoints';
import type { ResearchResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime, truncate } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

export function ResearchPage() {
  const toast = useToast();
  const [topic, setTopic] = useState('');
  const [depth, setDepth] = useState<'quick' | 'deep'>('quick');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const { data, loading, error: loadError, reload } = useAsync<ResearchResponse[]>(() => researchApi.list(), []);
  useDataRefresh(reload);

  const create = async (event: FormEvent) => {
    event.preventDefault();
    const value = topic.trim();
    if (value.length < 3 || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const report = await researchApi.create({ topic: value, depth });
      toast.success(
        'Research report created',
        report.grounded
          ? `Grounded in ${report.sources.length} passage(s) from your documents.`
          : 'No matching documents found — the report says so on its face.',
      );
      setTopic('');
      reload();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The report could not be created.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Research agent</h1>
          <p>
            Reports are built from your own documents. This build performs no live web retrieval, and
            every report states that on its face instead of implying otherwise.
          </p>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <form onSubmit={(event) => void create(event)}>
          <div className="grid grid--2">
            <div className="field">
              <label htmlFor="research-topic">Topic</label>
              <input
                id="research-topic"
                type="text"
                required
                minLength={3}
                maxLength={240}
                value={topic}
                placeholder="Spring Boot authentication best practices"
                onChange={(event) => setTopic(event.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="research-depth">Depth</label>
              <select id="research-depth" value={depth} onChange={(event) => setDepth(event.target.value as 'quick' | 'deep')}>
                <option value="quick">Quick (4 passages)</option>
                <option value="deep">Deep (10 passages)</option>
              </select>
            </div>
          </div>
          {error ? (
            <div className="alert alert--danger" role="alert" style={{ marginBottom: 12 }}>
              {error}
            </div>
          ) : null}
          <button type="submit" className="btn btn--primary" disabled={busy || topic.trim().length < 3}>
            {busy ? 'Researching…' : 'Create report'}
          </button>
        </form>
      </div>

      {loading ? <LoadingState label="Loading reports…" /> : null}
      {loadError ? <ErrorState error={loadError} onRetry={reload} /> : null}

      {!loading && !loadError && data ? (
        data.length === 0 ? (
          <EmptyState
            title="No research reports yet"
            message="Upload documents about a topic, then ask for a report here and each finding will be traceable to a passage."
          />
        ) : (
          <div className="grid grid--2">
            {data.map((report) => (
              <section key={report.id} className="card">
                <div className="row row--between">
                  <Link to={`/research/${report.id}`}>
                    <strong>{report.topic}</strong>
                  </Link>
                  <Badge tone={report.grounded ? 'success' : 'neutral'}>
                    {report.grounded ? `${report.sources.length} source(s)` : 'No sources'}
                  </Badge>
                </div>
                <p className="small muted" style={{ marginTop: 6 }}>
                  {truncate(report.summary || report.overview, 220)}
                </p>
                <div className="row row--between">
                  <span className="small subtle">
                    {report.provider} · {formatDateTime(report.createdAt)}
                  </span>
                  <Link className="btn btn--sm" to={`/research/${report.id}`}>
                    Read
                  </Link>
                </div>
              </section>
            ))}
          </div>
        )
      ) : null}
    </div>
  );
}

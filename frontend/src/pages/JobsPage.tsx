import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { jobApi } from '../api/endpoints';
import type { JobAnalysisResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime, truncate } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

export function JobsPage() {
  const toast = useToast();
  const [description, setDescription] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [company, setCompany] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const [pendingDelete, setPendingDelete] = useState<JobAnalysisResponse | null>(null);

  const { data, loading, error: loadError, reload } = useAsync<JobAnalysisResponse[]>(() => jobApi.list(), []);
  useDataRefresh(reload);

  const analyze = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const analysis = await jobApi.analyze({
        description: description.trim(),
        ...(jobTitle.trim() ? { jobTitle: jobTitle.trim() } : {}),
        ...(company.trim() ? { company: company.trim() } : {}),
      });
      toast.success(
        'Job description analysed',
        `Match score ${analysis.matchScore}% — ${analysis.missingSkills.length} gap(s) found.`,
      );
      setDescription('');
      setJobTitle('');
      setCompany('');
      reload();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Analysis failed.'));
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await jobApi.remove(pendingDelete.id);
      toast.success('Analysis deleted');
      setPendingDelete(null);
      reload();
    } catch (caught) {
      toast.failure('Could not delete the analysis', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const fieldIssues = error?.fieldIssues ?? {};

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Job intelligence</h1>
          <p>
            Paste a job description. Skills are matched against the profile you declared — the
            system never assumes experience you have not recorded.
          </p>
        </div>
        <Link className="btn" to="/profile">
          Update my skills
        </Link>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <form onSubmit={(event) => void analyze(event)} noValidate>
          <div className="grid grid--2">
            <div className="field">
              <label htmlFor="job-title">Job title (optional)</label>
              <input id="job-title" type="text" value={jobTitle} onChange={(event) => setJobTitle(event.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="job-company">Company (optional)</label>
              <input id="job-company" type="text" value={company} onChange={(event) => setCompany(event.target.value)} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="job-description">Job description</label>
            <textarea
              id="job-description"
              required
              minLength={40}
              maxLength={20000}
              style={{ minHeight: 200 }}
              placeholder="Paste the full job description, including the requirements and responsibilities sections."
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              aria-invalid={Boolean(fieldIssues.description)}
            />
            <span className="field__hint">
              {description.trim().length} / 20000 characters — at least 40 are required.
            </span>
            {fieldIssues.description ? <span className="field__error">{fieldIssues.description}</span> : null}
          </div>
          {error ? (
            <div className="alert alert--danger" role="alert" style={{ marginBottom: 12 }}>
              {error.message}
            </div>
          ) : null}
          <button type="submit" className="btn btn--primary" disabled={busy || description.trim().length < 40}>
            {busy ? 'Analysing…' : 'Analyse this job'}
          </button>
        </form>
      </div>

      {loading ? <LoadingState label="Loading analyses…" /> : null}
      {loadError ? <ErrorState error={loadError} onRetry={reload} /> : null}

      {!loading && !loadError && data ? (
        data.length === 0 ? (
          <EmptyState
            title="No analyses yet"
            message="Paste a job description above to see your match score and gaps."
          />
        ) : (
          <div className="card card--flush">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Role</th>
                  <th scope="col">Company</th>
                  <th scope="col" className="numeric">
                    Match
                  </th>
                  <th scope="col" className="numeric">
                    Gaps
                  </th>
                  <th scope="col">Analysed</th>
                  <th scope="col" className="numeric">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.map((analysis) => (
                  <tr key={analysis.id}>
                    <td>
                      <Link to={`/jobs/${analysis.id}`}>{analysis.jobTitle ?? 'Untitled role'}</Link>
                      {analysis.technologies.length > 0 ? (
                        <div className="small muted">{truncate(analysis.technologies.slice(0, 6).join(', '), 80)}</div>
                      ) : null}
                    </td>
                    <td>{analysis.company ?? '—'}</td>
                    <td className="numeric">
                      <Badge
                        tone={
                          analysis.matchScore >= 70 ? 'success' : analysis.matchScore >= 40 ? 'warning' : 'danger'
                        }
                      >
                        {analysis.matchScore}%
                      </Badge>
                    </td>
                    <td className="numeric">{analysis.missingSkills.length}</td>
                    <td>{formatDateTime(analysis.createdAt)}</td>
                    <td className="numeric">
                      <div className="row row--end" style={{ flexWrap: 'nowrap' }}>
                        <Link className="btn btn--sm" to={`/jobs/${analysis.id}`}>
                          Open
                        </Link>
                        <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(analysis)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Delete this analysis?"
        message={`The analysis for “${pendingDelete?.jobTitle ?? 'this role'}” will be removed.`}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

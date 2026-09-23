import { useState } from 'react';
import { aiApi } from '../api/endpoints';
import type { ActivityStatus, AiActivityResponse } from '../api/types';
import { activityTone, Badge } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime, relativeTime } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

const FILTERS: (ActivityStatus | '')[] = ['', 'SUCCESS', 'REJECTED', 'FAILED'];

export function ActivityPage() {
  const toast = useToast();
  const [filter, setFilter] = useState<ActivityStatus | ''>('');
  const [clearing, setClearing] = useState(false);

  const { data, loading, error, reload } = useAsync<AiActivityResponse[]>(() => aiApi.activity(200), []);
  useDataRefresh(reload);

  const rows = (data ?? []).filter((activity) => filter === '' || activity.status === filter);

  const clear = async () => {
    try {
      await aiApi.clearActivity();
      toast.success('Activity history cleared');
      setClearing(false);
      reload();
    } catch {
      toast.failure('Could not clear the history');
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>AI activity</h1>
          <p>
            Every command is recorded with what it was interpreted as and what happened. Rejected
            commands are kept too — that is the point of the trail.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => setClearing(true)} disabled={(data ?? []).length === 0}>
          Clear history
        </button>
      </div>

      <div className="row" style={{ marginBottom: 16 }}>
        {FILTERS.map((value) => (
          <button
            key={value || 'all'}
            type="button"
            className={filter === value ? 'btn btn--primary btn--sm' : 'btn btn--sm'}
            onClick={() => setFilter(value)}
          >
            {value === '' ? 'All' : value}
          </button>
        ))}
      </div>

      {loading ? <LoadingState label="Loading activity…" rows={5} /> : null}
      {error ? <ErrorState error={error} onRetry={reload} /> : null}

      {!loading && !error && data ? (
        rows.length === 0 ? (
          <EmptyState
            title={data.length === 0 ? 'No AI activity yet' : 'Nothing matches this filter'}
            message={
              data.length === 0
                ? 'Run something from the command bar and it will appear here with its outcome.'
                : 'Try a different status filter.'
            }
          />
        ) : (
          <div className="card card--flush">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Command</th>
                  <th scope="col">Intent</th>
                  <th scope="col">Status</th>
                  <th scope="col">Result</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((activity) => (
                  <tr key={activity.id}>
                    <td title={formatDateTime(activity.createdAt)}>{relativeTime(activity.createdAt)}</td>
                    <td className="mono">{activity.command}</td>
                    <td className="mono small">{activity.intent}</td>
                    <td>
                      <Badge tone={activityTone(activity.status)}>{activity.status}</Badge>
                    </td>
                    <td>{activity.resultSummary}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )
      ) : null}

      <ConfirmDialog
        open={clearing}
        title="Clear the activity history?"
        message="Every recorded AI action will be deleted. Your goals, tasks and documents are not affected."
        confirmLabel="Clear history"
        onConfirm={clear}
        onCancel={() => setClearing(false)}
      />
    </div>
  );
}

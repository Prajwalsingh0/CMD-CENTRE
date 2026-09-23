import { useState } from 'react';
import { analyticsApi } from '../api/endpoints';
import type { AnalyticsResponse, DayPoint, GoalStatus } from '../api/types';
import { Badge, goalTone } from '../components/Badge';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { ProgressBar } from '../components/ProgressBar';
import { StatCard } from '../components/StatCard';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

const CHART_WIDTH = 760;
const CHART_HEIGHT = 170;

/** Hand-rolled bar chart. No charting dependency, and every bar is a real row count. */
function BarChart({ points, tone, label }: { points: DayPoint[]; tone: 'accent' | 'success'; label: string }) {
  if (points.length === 0) {
    return <p className="muted">No data in this window.</p>;
  }
  const max = Math.max(1, ...points.map((point) => point.count));
  const step = CHART_WIDTH / points.length;
  const barWidth = Math.max(2, step - 3);
  const baseline = CHART_HEIGHT - 22;

  return (
    <svg className="chart" viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`} role="img" aria-label={label}>
      <title>{label}</title>
      {[0, 0.5, 1].map((fraction) => (
        <g key={fraction}>
          <line
            className="gridline"
            x1={0}
            x2={CHART_WIDTH}
            y1={baseline - (baseline - 10) * fraction}
            y2={baseline - (baseline - 10) * fraction}
          />
          <text x={0} y={baseline - (baseline - 10) * fraction - 3}>
            {Math.round(max * fraction)}
          </text>
        </g>
      ))}
      {points.map((point, index) => {
        const height = ((baseline - 10) * point.count) / max;
        return (
          <g key={point.date}>
            <rect
              className={tone === 'success' ? 'bar bar--alt' : 'bar'}
              x={index * step + 1.5}
              y={baseline - height}
              width={barWidth}
              height={Math.max(point.count > 0 ? 2 : 0, height)}
              rx={2}
            >
              <title>{`${point.date}: ${point.count}`}</title>
            </rect>
          </g>
        );
      })}
      <line className="axis" x1={0} x2={CHART_WIDTH} y1={baseline} y2={baseline} />
      <text x={0} y={CHART_HEIGHT - 8}>
        {points[0]?.date}
      </text>
      <text x={CHART_WIDTH} y={CHART_HEIGHT - 8} textAnchor="end">
        {points[points.length - 1]?.date}
      </text>
    </svg>
  );
}

function Breakdown({ title, items }: { title: string; items: { label: string; count: number }[] }) {
  const max = Math.max(1, ...items.map((item) => item.count));
  return (
    <section className="card">
      <h2 className="card__title">{title}</h2>
      {items.length === 0 ? (
        <p className="muted" style={{ margin: 0 }}>
          Nothing recorded yet.
        </p>
      ) : (
        <div className="stack">
          {items.map((item) => (
            <div key={item.label}>
              <div className="row row--between small">
                <span>{item.label.replace('_', ' ')}</span>
                <span className="muted">{item.count}</span>
              </div>
              <div className="progress" style={{ marginTop: 4 }}>
                <div className="progress__bar" style={{ width: `${(item.count / max) * 100}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

export function AnalyticsPage() {
  const [days, setDays] = useState(30);
  const { data, loading, error, reload } = useAsync<AnalyticsResponse>(() => analyticsApi.load(days), [days]);
  useDataRefresh(reload);

  if (loading) {
    return <LoadingState label="Crunching your numbers…" rows={4} />;
  }
  if (error) {
    return <ErrorState error={error} onRetry={reload} />;
  }
  if (!data) {
    return <EmptyState title="Nothing to analyse yet" message="Create a task to get started." />;
  }

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Analytics</h1>
          <p>Every figure below is computed from your own rows over the selected window.</p>
        </div>
        <div className="field" style={{ marginBottom: 0, minWidth: 160 }}>
          <label htmlFor="analytics-days">Window</label>
          <select id="analytics-days" value={days} onChange={(event) => setDays(Number(event.target.value))}>
            <option value={7}>Last 7 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
          </select>
        </div>
      </div>

      <div className="grid grid--4" style={{ marginBottom: 16 }}>
        <StatCard label="Completion rate" value={`${data.completionRate}%`} foot={`${data.completedTasks} of ${data.totalTasks} tasks`} tone={data.completionRate >= 50 ? 'success' : 'default'} />
        <StatCard label="Open" value={data.openTasks} foot={`${data.cancelledTasks} cancelled`} />
        <StatCard label="Overdue" value={data.overdueTasks} tone={data.overdueTasks > 0 ? 'danger' : 'default'} foot="Still open, past due" />
        <StatCard label="AI actions" value={data.aiActions} foot={`${data.documents} document(s) indexed`} />
      </div>

      <div className="stack">
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Tasks completed per day</h2>
            <span className="card__hint">{days}-day window</span>
          </div>
          <BarChart points={data.tasksCompleted} tone="success" label="Tasks completed per day" />
        </section>

        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Tasks created per day</h2>
            <span className="card__hint">{days}-day window</span>
          </div>
          <BarChart points={data.tasksCreated} tone="accent" label="Tasks created per day" />
        </section>

        <section className="card">
          <div className="card__head">
            <h2 className="card__title">AI actions per day</h2>
            <span className="card__hint">{days}-day window</span>
          </div>
          <BarChart points={data.aiActivity} tone="accent" label="AI actions per day" />
        </section>

        <div className="grid grid--2">
          <Breakdown title="Tasks by status" items={data.tasksByStatus} />
          <Breakdown title="Tasks by priority" items={data.tasksByPriority} />
        </div>

        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Goal progress</h2>
            <span className="card__hint">{data.activeGoals} active · {data.completedGoals} completed</span>
          </div>
          {data.goalProgress.length === 0 ? (
            <p className="muted" style={{ margin: 0 }}>
              No goals yet.
            </p>
          ) : (
            <div className="stack">
              {data.goalProgress.map((goal) => (
                <div key={goal.id}>
                  <div className="row row--between">
                    <span>{goal.title}</span>
                    <span className="row">
                      {goal.overdue ? <Badge tone="danger">Past deadline</Badge> : null}
                      <Badge tone={goalTone(goal.status as GoalStatus)}>{goal.status}</Badge>
                    </span>
                  </div>
                  <ProgressBar
                    value={goal.progressPercent}
                    late={goal.overdue}
                    label={`${goal.completedTasks} of ${goal.totalTasks} tasks`}
                  />
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="card">
          <h2 className="card__title">Other workspace activity</h2>
          <div className="grid grid--3">
            <StatCard label="Documents" value={data.documents} foot={`${data.indexedChunks} passage(s)`} />
            <StatCard label="Job analyses" value={data.jobAnalyses} />
            <StatCard label="Research reports" value={data.researchReports} />
          </div>
        </section>
      </div>
    </div>
  );
}

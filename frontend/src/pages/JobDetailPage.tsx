import { Link, useNavigate, useParams } from 'react-router-dom';
import { jobApi } from '../api/endpoints';
import type { JobAnalysisResponse, SkillMatch } from '../api/types';
import { Badge, skillMark, skillTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';
import { useState } from 'react';

/** One row of the gap table. The mark is derived from the state, never chosen by hand. */
function SkillRow({ match }: { match: SkillMatch }) {
  return (
    <tr>
      <td>
        <span aria-hidden="true" style={{ marginRight: 8 }}>
          {skillMark(match.status)}
        </span>
        {match.skill}
      </td>
      <td>
        <Badge tone={skillTone(match.status)}>{match.status}</Badge>
      </td>
      <td>{match.required ? 'Required' : 'Preferred'}</td>
      <td>{match.userLevel ?? '—'}</td>
    </tr>
  );
}

export function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const analysisId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const [regenerating, setRegenerating] = useState(false);

  const { data, loading, error, reload } = useAsync<JobAnalysisResponse>(() => jobApi.get(analysisId), [analysisId]);

  if (!Number.isFinite(analysisId)) {
    return <ErrorState error={new Error('That analysis id is not a number.')} />;
  }
  if (loading) {
    return <LoadingState label="Loading analysis…" />;
  }
  if (error) {
    return <ErrorState error={error} onRetry={reload} />;
  }
  if (!data) {
    return null;
  }

  const regenerate = async () => {
    setRegenerating(true);
    try {
      await jobApi.regeneratePrep(analysisId);
      toast.success('Preparation regenerated');
      reload();
    } catch {
      toast.failure('Could not regenerate the preparation material');
    } finally {
      setRegenerating(false);
    }
  };

  const required = data.skillMatches.filter((match) => match.required);
  const preferred = data.skillMatches.filter((match) => !match.required);

  return (
    <div>
      <p className="small">
        <Link to="/jobs">← All analyses</Link>
      </p>

      <div className="page-head">
        <div>
          <h1>{data.jobTitle ?? 'Untitled role'}</h1>
          <p className="muted">
            {data.company ?? 'Unknown company'} · analysed {formatDateTime(data.createdAt)}
          </p>
        </div>
        <div className="row">
          <Badge tone={data.matchScore >= 70 ? 'success' : data.matchScore >= 40 ? 'warning' : 'danger'}>
            {data.matchScore}% match
          </Badge>
          <button type="button" className="btn" onClick={() => void regenerate()} disabled={regenerating}>
            {regenerating ? 'Regenerating…' : 'Regenerate preparation'}
          </button>
          <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
            Delete
          </button>
        </div>
      </div>

      <div className="grid grid--sidebar">
        <div className="stack">
          <section className="card">
            <h2 className="card__title">Requirement check</h2>
            <p className="small muted">
              ✓ declared and verified · △ declared but not verified · ✗ not in your profile
            </p>
            <div className="card card--flush" style={{ marginTop: 8 }}>
              <table className="table">
                <thead>
                  <tr>
                    <th scope="col">Skill</th>
                    <th scope="col">State</th>
                    <th scope="col">Ask</th>
                    <th scope="col">Your level</th>
                  </tr>
                </thead>
                <tbody>
                  {required.map((match) => (
                    <SkillRow key={`required-${match.skill}`} match={match} />
                  ))}
                  {preferred.map((match) => (
                    <SkillRow key={`preferred-${match.skill}`} match={match} />
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          <section className="card">
            <h2 className="card__title">Gap analysis</h2>
            <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>{data.gapAnalysis}</p>
            {data.missingSkills.length > 0 ? (
              <div className="row" style={{ marginTop: 10 }}>
                {data.missingSkills.map((skill) => (
                  <span key={skill} className="tag">
                    {skill}
                  </span>
                ))}
              </div>
            ) : null}
          </section>

          <section className="card">
            <h2 className="card__title">Interview questions</h2>
            {data.interviewQuestions.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                No questions were generated. Try regenerating the preparation material.
              </p>
            ) : (
              <ol style={{ margin: 0, paddingLeft: 20 }}>
                {data.interviewQuestions.map((question) => (
                  <li key={question} style={{ marginBottom: 6 }}>
                    {question}
                  </li>
                ))}
              </ol>
            )}
          </section>

          <section className="card">
            <h2 className="card__title">Responsibilities found</h2>
            {data.responsibilities.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                The description did not contain a recognisable responsibilities section.
              </p>
            ) : (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {data.responsibilities.map((item) => (
                  <li key={item} style={{ marginBottom: 4 }}>
                    {item}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>

        <div className="stack">
          <section className="card">
            <h2 className="card__title">Extracted</h2>
            <dl className="kv">
              <dt>Experience</dt>
              <dd>{data.experience ?? '—'}</dd>
              <dt>Education</dt>
              <dd>{data.education ?? '—'}</dd>
              <dt>Required skills</dt>
              <dd>{data.requiredSkills.length}</dd>
              <dt>Preferred skills</dt>
              <dd>{data.preferredSkills.length}</dd>
            </dl>
            {data.technologies.length > 0 ? (
              <>
                <hr className="divider" />
                <div className="row">
                  {data.technologies.map((technology) => (
                    <span key={technology} className="tag">
                      {technology}
                    </span>
                  ))}
                </div>
              </>
            ) : null}
          </section>

          <section className="card">
            <h2 className="card__title">Interview topics</h2>
            {data.interviewTopics.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing generated yet.
              </p>
            ) : (
              <ul style={{ margin: 0, paddingLeft: 20 }}>
                {data.interviewTopics.map((topic) => (
                  <li key={topic} style={{ marginBottom: 4 }}>
                    {topic}
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section className="card">
            <h2 className="card__title">Suggested learning plan</h2>
            {data.learningPlan.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing generated yet.
              </p>
            ) : (
              <ol style={{ margin: 0, paddingLeft: 20 }}>
                {data.learningPlan.map((step) => (
                  <li key={step} style={{ marginBottom: 6 }}>
                    {step}
                  </li>
                ))}
              </ol>
            )}
          </section>
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Delete this analysis?"
        message="The stored analysis and its preparation material will be removed."
        onConfirm={async () => {
          await jobApi.remove(analysisId);
          toast.success('Analysis deleted');
          navigate('/jobs');
        }}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}

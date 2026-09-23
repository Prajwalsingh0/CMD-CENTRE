import { useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { userApi } from '../api/endpoints';
import type { SkillResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatDateTime } from '../format';
import { useAuth } from '../auth/AuthProvider';
import { useAsync } from '../hooks/useasync';

const LEVELS = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT'];

export function ProfilePage() {
  const toast = useToast();
  const { user, refreshUser } = useAuth();
  const skills = useAsync<SkillResponse[]>(() => userApi.skills(), []);

  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [headline, setHeadline] = useState(user?.headline ?? '');
  const [savingProfile, setSavingProfile] = useState(false);

  const [skill, setSkill] = useState('');
  const [level, setLevel] = useState('INTERMEDIATE');
  const [verified, setVerified] = useState(false);
  const [savingSkill, setSavingSkill] = useState(false);
  const [skillError, setSkillError] = useState<ApiError | null>(null);
  const [pendingDelete, setPendingDelete] = useState<SkillResponse | null>(null);

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault();
    setSavingProfile(true);
    try {
      await userApi.updateProfile({ displayName: displayName.trim(), headline: headline.trim() });
      await refreshUser();
      toast.success('Profile updated');
    } catch (caught) {
      toast.failure('Could not save your profile', caught instanceof ApiError ? caught.message : undefined);
    } finally {
      setSavingProfile(false);
    }
  };

  const addSkill = async (event: FormEvent) => {
    event.preventDefault();
    if (skill.trim().length === 0) {
      return;
    }
    setSavingSkill(true);
    setSkillError(null);
    try {
      await userApi.addSkill({ skill: skill.trim(), level, verified });
      toast.success(`“${skill.trim()}” added to your profile`);
      setSkill('');
      setVerified(false);
      skills.reload();
    } catch (caught) {
      setSkillError(caught instanceof ApiError ? caught : new ApiError(0, 'UNKNOWN', 'Could not add the skill.'));
    } finally {
      setSavingSkill(false);
    }
  };

  const confirmRemove = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await userApi.removeSkill(pendingDelete.id);
      toast.success('Skill removed');
      setPendingDelete(null);
      skills.reload();
    } catch {
      toast.failure('Could not remove the skill');
    }
  };

  const fieldIssues = skillError?.fieldIssues ?? {};

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Profile</h1>
          <p>
            The skills below are the <strong>only</strong> source of truth for job analysis. Nothing
            is ever assumed about your experience.
          </p>
        </div>
      </div>

      <div className="grid grid--sidebar">
        <div className="stack">
          <section className="card">
            <h2 className="card__title">Account</h2>
            <form onSubmit={(event) => void saveProfile(event)}>
              <div className="field">
                <label htmlFor="profile-name">Display name</label>
                <input
                  id="profile-name"
                  type="text"
                  required
                  maxLength={120}
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="profile-headline">Headline</label>
                <input
                  id="profile-headline"
                  type="text"
                  maxLength={200}
                  placeholder="Backend engineer focused on Java and distributed systems"
                  value={headline}
                  onChange={(event) => setHeadline(event.target.value)}
                />
              </div>
              <dl className="kv" style={{ marginBottom: 12 }}>
                <dt>Email</dt>
                <dd>{user?.email}</dd>
                <dt>Member since</dt>
                <dd>{formatDateTime(user?.createdAt)}</dd>
              </dl>
              <button type="submit" className="btn btn--primary" disabled={savingProfile}>
                {savingProfile ? 'Saving…' : 'Save profile'}
              </button>
            </form>
          </section>

          <section className="card">
            <h2 className="card__title">Declare a skill</h2>
            <form onSubmit={(event) => void addSkill(event)} noValidate>
              <div className="grid grid--2">
                <div className="field">
                  <label htmlFor="skill-name">Skill</label>
                  <input
                    id="skill-name"
                    type="text"
                    required
                    maxLength={80}
                    placeholder="Java, Spring Boot, PostgreSQL…"
                    value={skill}
                    onChange={(event) => setSkill(event.target.value)}
                    aria-invalid={Boolean(fieldIssues.skill)}
                  />
                  {fieldIssues.skill ? <span className="field__error">{fieldIssues.skill}</span> : null}
                </div>
                <div className="field">
                  <label htmlFor="skill-level">Level</label>
                  <select id="skill-level" value={level} onChange={(event) => setLevel(event.target.value)}>
                    {LEVELS.map((value) => (
                      <option key={value} value={value}>
                        {value}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="field">
                <label htmlFor="skill-verified" className="row">
                  <input
                    id="skill-verified"
                    type="checkbox"
                    checked={verified}
                    onChange={(event) => setVerified(event.target.checked)}
                    style={{ width: 'auto', marginRight: 8 }}
                  />
                  <span>I can back this up (verified)</span>
                </label>
                <span className="field__hint">
                  Job analysis marks a skill ✓ when it is verified, △ when it is only declared, and ✗
                  when it is absent.
                </span>
              </div>
              <button type="submit" className="btn btn--primary" disabled={savingSkill || skill.trim().length === 0}>
                {savingSkill ? 'Adding…' : 'Add skill'}
              </button>
            </form>
          </section>
        </div>

        <section className="card">
          <div className="card__head">
            <h2 className="card__title">Your skills</h2>
            <span className="card__hint">{(skills.data ?? []).length}</span>
          </div>
          {skills.loading ? <LoadingState label="Loading skills…" rows={2} /> : null}
          {skills.error ? <ErrorState error={skills.error} onRetry={skills.reload} /> : null}
          {skills.data && skills.data.length === 0 ? (
            <EmptyState
              title="No skills declared"
              message="Add the technologies you can genuinely defend in an interview — they drive your job match score."
            />
          ) : null}
          {skills.data && skills.data.length > 0 ? (
            <ul className="list">
              {skills.data.map((item) => (
                <li key={item.id} className="list__item">
                  <div className="list__main">
                    <div className="list__title">{item.skill}</div>
                    <div className="list__meta">{item.level}</div>
                  </div>
                  <Badge tone={item.verified ? 'success' : 'warning'}>{item.verified ? 'Verified' : 'Unverified'}</Badge>
                  <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(item)}>
                    Remove
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
        </section>
      </div>

      <ConfirmDialog
        open={pendingDelete !== null}
        title="Remove this skill?"
        message={`“${pendingDelete?.skill ?? ''}” will no longer count towards job match scores.`}
        confirmLabel="Remove"
        onConfirm={confirmRemove}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

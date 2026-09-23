import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { aiApi } from '../api/endpoints';
import type { AnswerSource, CommandResultResponse } from '../api/types';
import { notifyDataChanged } from '../hooks/usedatarefresh';
import { activityTone, Badge } from './Badge';

type Mode = 'command' | 'chat';

interface ChatOutcome {
  reply: string;
  grounded: boolean;
  sources: AnswerSource[];
  provider: string;
}

/**
 * The signature feature: a persistent natural-language bar in the top bar.
 *
 * Two modes, both honest about what happened. Command mode renders the backend's step checklist and
 * surfaces a rejection as a warning with the reason the tool gave, never as a generic failure. Chat
 * mode shows whether the answer was grounded in the user's own documents and lists its sources.
 *
 * A successful command dispatches a data-changed event so the visible page re-fetches — the bar can
 * create a goal from the Analytics screen, and the screen must not lie about it afterwards.
 */
export function CommandBar() {
  const [mode, setMode] = useState<Mode>('command');
  const [value, setValue] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<CommandResultResponse | null>(null);
  const [chat, setChat] = useState<ChatOutcome | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [examples, setExamples] = useState<string[]>([]);
  const [showExamples, setShowExamples] = useState(false);

  useEffect(() => {
    let cancelled = false;
    aiApi
      .examples()
      .then((list) => {
        if (!cancelled) {
          setExamples(list);
        }
      })
      .catch(() => setExamples([]));
    return () => {
      cancelled = true;
    };
  }, []);

  const reset = () => {
    setResult(null);
    setChat(null);
    setError(null);
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const text = value.trim();
    if (text.length === 0 || busy) {
      return;
    }
    setBusy(true);
    reset();
    try {
      if (mode === 'command') {
        const outcome = await aiApi.command(text);
        setResult(outcome);
        if (outcome.status === 'SUCCESS') {
          notifyDataChanged();
          setValue('');
        }
      } else {
        const outcome = await aiApi.chat(text);
        setChat(outcome);
        setValue('');
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The request could not be completed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="commandbar">
      <form className="commandbar__form" onSubmit={(event) => void submit(event)}>
        <label className="sr-only" htmlFor="command-bar-input" style={{ position: 'absolute', left: -9999 }}>
          AI command
        </label>
        <input
          id="command-bar-input"
          className="commandbar__input"
          type="text"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          placeholder={
            mode === 'command'
              ? 'Ask the command bar: create a task, plan 14 days, list overdue tasks…'
              : 'Ask a question about your documents…'
          }
          aria-describedby="command-bar-mode"
          maxLength={2000}
        />
        <button type="button" className="btn btn--sm" onClick={() => setMode(mode === 'command' ? 'chat' : 'command')} id="command-bar-mode" aria-label="Switch between command and chat mode">
          {mode === 'command' ? 'Command' : 'Chat'}
        </button>
        <button type="submit" className="btn btn--primary btn--sm" disabled={busy || value.trim().length === 0}>
          {busy ? 'Working…' : mode === 'command' ? 'Run' : 'Ask'}
        </button>
        {examples.length > 0 ? (
          <button type="button" className="btn btn--ghost btn--sm" onClick={() => setShowExamples((open) => !open)} aria-expanded={showExamples}>
            Examples
          </button>
        ) : null}
      </form>

      {showExamples && examples.length > 0 ? (
        <div className="card commandbar__panel" style={{ padding: 12 }}>
          <div className="small subtle">Real commands this build understands</div>
          <ul className="list">
            {examples.map((example) => (
              <li key={example} className="list__item" style={{ borderBottom: 'none', padding: '4px 0' }}>
                <button
                  type="button"
                  className="btn btn--ghost btn--sm"
                  onClick={() => {
                    setValue(example);
                    setShowExamples(false);
                  }}
                >
                  {example}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      {error ? (
        <div className="alert alert--danger commandbar__panel" role="alert">
          {error}
        </div>
      ) : null}

      {result ? (
        <div className="card commandbar__panel">
          <div className="row row--between">
            <div className="row">
              <Badge tone={activityTone(result.status)}>{result.status}</Badge>
              <span className="mono small">{result.intent}</span>
              <span className="small subtle">
                via {result.provider} · parsed by {result.parsedBy}
              </span>
            </div>
            <button type="button" className="btn btn--ghost btn--sm" onClick={reset} aria-label="Close result">
              ✕
            </button>
          </div>

          <p style={{ marginTop: 10, marginBottom: 0 }}>{result.summary}</p>
          {result.rationale ? <p className="small muted">“{result.rationale}”</p> : null}

          {result.steps.length > 0 ? (
            <ul className="commandbar__steps">
              {result.steps.map((step, index) => (
                <li key={`${index}-${step}`}>{step}</li>
              ))}
            </ul>
          ) : null}

          {result.status === 'REJECTED' ? (
            <div className="alert alert--warning" style={{ marginTop: 10 }}>
              Nothing was changed. Rephrase the command, or use the page for that action.
            </div>
          ) : null}
        </div>
      ) : null}

      {chat ? (
        <div className="card commandbar__panel">
          <div className="row row--between">
            <div className="row">
              <Badge tone={chat.grounded ? 'success' : 'neutral'}>
                {chat.grounded ? 'From your documents' : 'No document context'}
              </Badge>
              <span className="small subtle">via {chat.provider}</span>
            </div>
            <button type="button" className="btn btn--ghost btn--sm" onClick={reset} aria-label="Close answer">
              ✕
            </button>
          </div>
          <div className="pre" style={{ marginTop: 10 }}>
            {chat.reply}
          </div>
          {chat.sources.length > 0 ? (
            <ul className="list" style={{ marginTop: 8 }}>
              {chat.sources.map((source) => (
                <li key={`${source.documentId}-${source.chunkIndex}`} className="list__item">
                  <div className="list__main">
                    <div className="list__title">{source.documentName}</div>
                    <div className="list__meta">
                      part {source.chunkIndex + 1} · relevance {Math.round(source.score * 100)}%
                    </div>
                    <div className="small muted">{source.excerpt}</div>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

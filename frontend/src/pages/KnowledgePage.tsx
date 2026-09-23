import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { knowledgeApi } from '../api/endpoints';
import type { AnswerResponse } from '../api/types';
import { Badge } from '../components/Badge';
import { useToast } from '../components/ToastProvider';

export function KnowledgePage() {
  const toast = useToast();
  const [question, setQuestion] = useState('');
  const [answer, setAnswer] = useState<AnswerResponse | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ask = async (event: FormEvent) => {
    event.preventDefault();
    const text = question.trim();
    if (text.length === 0 || busy) {
      return;
    }
    setBusy(true);
    setError(null);
    setAnswer(null);
    try {
      const response = await knowledgeApi.ask({ question: text });
      setAnswer(response);
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : 'The question could not be answered.');
    } finally {
      setBusy(false);
    }
  };

  const reindex = async () => {
    try {
      const result = await knowledgeApi.reindex();
      toast.success('Knowledge base re-embedded', `${result.chunksEmbedded} passage(s) with ${result.provider}.`);
    } catch (caught) {
      toast.failure('Reindex failed', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Knowledge base</h1>
          <p>
            Questions are answered only from the documents you indexed. Retrieval is scoped to your
            account before anything is ranked, and every answer lists the passages it used.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => void reindex()}>
          Re-embed everything
        </button>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <form onSubmit={(event) => void ask(event)}>
          <div className="field">
            <label htmlFor="knowledge-question">Your question</label>
            <textarea
              id="knowledge-question"
              value={question}
              maxLength={1000}
              placeholder="What did I write about deployment? Which of my skills are relevant to a backend role?"
              onChange={(event) => setQuestion(event.target.value)}
            />
            <span className="field__hint">
              If nothing relevant is indexed, the answer will say so instead of guessing.
            </span>
          </div>
          <button type="submit" className="btn btn--primary" disabled={busy || question.trim().length === 0}>
            {busy ? 'Searching…' : 'Ask the knowledge base'}
          </button>
        </form>
      </div>

      {error ? (
        <div className="alert alert--danger" role="alert" style={{ marginBottom: 16 }}>
          {error}
        </div>
      ) : null}

      {answer ? (
        <div className="grid grid--sidebar">
          <section className="card">
            <div className="card__head">
              <h2 className="card__title">Answer</h2>
              <Badge tone={answer.grounded ? 'success' : 'neutral'}>
                {answer.grounded ? `${answer.sources.length} source(s)` : 'No sources found'}
              </Badge>
            </div>
            <div className="pre">{answer.answer}</div>
            {!answer.grounded ? (
              <div className="alert alert--info" style={{ marginTop: 12 }}>
                The retrieval step found no matching passage, so this answer is not based on your
                documents. Upload something relevant and ask again.
              </div>
            ) : null}
            <p className="small subtle" style={{ marginTop: 10, marginBottom: 0 }}>
              Provider: {answer.provider}
              {answer.remoteProvider ? ' (external model)' : ' (deterministic, no external call)'}
            </p>
          </section>

          <section className="card">
            <h2 className="card__title">Slides used</h2>
            {answer.sources.length === 0 ? (
              <p className="muted" style={{ margin: 0 }}>
                Nothing was retrieved.
              </p>
            ) : (
              <ul className="list">
                {answer.sources.map((source) => (
                  <li key={`${source.documentId}-${source.chunkIndex}`} className="list__item">
                    <div className="list__main">
                      <Link to={`/documents/${source.documentId}`}>{source.documentName}</Link>
                      <div className="list__meta">
                        passage {source.chunkIndex + 1} · relevance {Math.round(source.score * 100)}%
                      </div>
                      <div className="small muted">{source.excerpt}</div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      ) : null}

      {!answer ? (
        <div className="state">
          <h3>Ask something about your own material</h3>
          <p>
            The knowledge base contains only what you uploaded. Open{' '}
            <Link to="/documents">Documents</Link> to add a PDF, TXT or Markdown file.
          </p>
        </div>
      ) : null}
    </div>
  );
}

import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { documentApi } from '../api/endpoints';
import type { DocumentContentResponse, DocumentInsightResponse, DocumentResponse, InsightOperation } from '../api/types';
import { Badge, documentTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatBytes, formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';

const OPERATIONS: { value: InsightOperation; label: string }[] = [
  { value: 'SUMMARY', label: 'Summarise' },
  { value: 'KEY_POINTS', label: 'Key points' },
  { value: 'NOTES', label: 'Generate notes' },
  { value: 'QUESTIONS', label: 'Questions to study' },
];

export function DocumentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const documentId = Number(id);
  const navigate = useNavigate();
  const toast = useToast();

  const [insight, setInsight] = useState<DocumentInsightResponse | null>(null);
  const [busyOperation, setBusyOperation] = useState<InsightOperation | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [showFullText, setShowFullText] = useState(false);

  const document = useAsync<DocumentResponse>(() => documentApi.get(documentId), [documentId]);
  const content = useAsync<DocumentContentResponse>(() => documentApi.content(documentId), [documentId]);

  if (!Number.isFinite(documentId)) {
    return <ErrorState error={new Error('That document id is not a number.')} />;
  }
  if (document.loading) {
    return <LoadingState label="Loading document…" />;
  }
  if (document.error) {
    return <ErrorState error={document.error} onRetry={document.reload} />;
  }
  if (!document.data) {
    return null;
  }

  const data = document.data;

  const runInsight = async (operation: InsightOperation) => {
    setBusyOperation(operation);
    setInsight(null);
    try {
      const result = await documentApi.insight(documentId, operation);
      setInsight(result);
    } catch (caught) {
      toast.failure('The document could not be analysed', caught instanceof ApiError ? caught.message : undefined);
    } finally {
      setBusyOperation(null);
    }
  };

  const remove = async () => {
    try {
      await documentApi.remove(documentId);
      toast.success('Document deleted');
      navigate('/documents');
    } catch (caught) {
      toast.failure('Could not delete the document', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  return (
    <div>
      <p className="small">
        <Link to="/documents">← All documents</Link>
      </p>

      <div className="page-head">
        <div>
          <h1>{data.name}</h1>
          <div className="row" style={{ marginTop: 6 }}>
            <Badge tone={documentTone(data.status)}>{data.status}</Badge>
            <span className="small subtle">
              {formatBytes(data.sizeBytes)} · {data.chunkCount} passage(s) ·{' '}
              {data.extractedChars.toLocaleString()} characters extracted
            </span>
          </div>
        </div>
        <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
          Delete
        </button>
      </div>

      {data.failureReason ? (
        <div className="alert alert--warning" style={{ marginBottom: 16 }}>
          <strong>This file could not be indexed.</strong> {data.failureReason}
        </div>
      ) : null}

      <div className="grid grid--sidebar">
        <section className="card">
          <div className="card__head">
            <h2 className="card__title">AI insights</h2>
            <span className="card__hint">Generated from the extracted text only</span>
          </div>
          <div className="row">
            {OPERATIONS.map((operation) => (
              <button
                key={operation.value}
                type="button"
                className="btn btn--sm"
                onClick={() => void runInsight(operation.value)}
                disabled={busyOperation !== null || data.status !== 'READY'}
              >
                {busyOperation === operation.value ? 'Working…' : operation.label}
              </button>
            ))}
          </div>

          {data.status !== 'READY' ? (
            <p className="small muted" style={{ marginTop: 10 }}>
              Insights need extracted text. This document is {data.status.toLowerCase()}.
            </p>
          ) : null}

          {insight ? (
            <>
              <hr className="divider" />
              <div className="row row--between">
                <strong>{insight.operation.replace('_', ' ')}</strong>
                <span className="small subtle">
                  {insight.provider} · {formatDateTime(insight.generatedAt)}
                </span>
              </div>
              <div className="pre" style={{ marginTop: 8 }}>
                {insight.content}
              </div>
            </>
          ) : null}

          <hr className="divider" />
          <div className="card__head">
            <h2 className="card__title">Extracted text</h2>
            <button type="button" className="btn btn--ghost btn--sm" onClick={() => setShowFullText((open) => !open)}>
              {showFullText ? 'Collapse' : 'Expand'}
            </button>
          </div>
          {content.loading ? <LoadingState label="Reading text…" rows={2} /> : null}
          {content.error ? <ErrorState error={content.error} onRetry={content.reload} /> : null}
          {content.data ? (
            <>
              {content.data.truncated ? (
                <p className="small muted">
                  Showing the first {content.data.text.length.toLocaleString()} of{' '}
                  {content.data.totalChars.toLocaleString()} characters.
                </p>
              ) : null}
              <div className="pre" style={{ maxHeight: showFullText ? 640 : 220 }}>
                {content.data.text.length > 0 ? content.data.text : 'No text was extracted from this file.'}
              </div>
            </>
          ) : null}
        </section>

        <div className="stack">
          <section className="card">
            <h2 className="card__title">Metadata</h2>
            <dl className="kv">
              <dt>Content type</dt>
              <dd className="mono">{data.contentType}</dd>
              <dt>Uploaded</dt>
              <dd>{formatDateTime(data.createdAt)}</dd>
              <dt>Updated</dt>
              <dd>{formatDateTime(data.updatedAt)}</dd>
              <dt>Passages</dt>
              <dd>{data.chunkCount}</dd>
            </dl>
          </section>

          <section className="card">
            <h2 className="card__title">Ask about this document</h2>
            <p className="small muted">
              The Knowledge page answers questions from every indexed document and cites the exact
              passages it used.
            </p>
            <Link className="btn btn--sm" to="/knowledge">
              Open the knowledge base
            </Link>
          </section>
        </div>
      </div>

      <ConfirmDialog
        open={confirming}
        title="Delete this document?"
        message={`“${data.name}” and all ${data.chunkCount} indexed passage(s) will be removed permanently.`}
        onConfirm={remove}
        onCancel={() => setConfirming(false)}
      />
    </div>
  );
}

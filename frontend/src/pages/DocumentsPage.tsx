import { useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { documentApi, knowledgeApi } from '../api/endpoints';
import type { DocumentResponse, DocumentStatus } from '../api/types';
import { Badge, documentTone } from '../components/Badge';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { EmptyState, ErrorState, LoadingState } from '../components/LoadingState';
import { useToast } from '../components/ToastProvider';
import { formatBytes, formatDateTime } from '../format';
import { useAsync } from '../hooks/useasync';
import { useDataRefresh } from '../hooks/usedatarefresh';

const STATUSES: DocumentStatus[] = ['PENDING', 'PROCESSING', 'READY', 'FAILED'];

export function DocumentsPage() {
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);
  const [statusFilter, setStatusFilter] = useState<DocumentStatus | ''>('');
  const [search, setSearch] = useState('');
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [pendingDelete, setPendingDelete] = useState<DocumentResponse | null>(null);

  const { data, loading, error, reload } = useAsync<DocumentResponse[]>(
    () => documentApi.list({ status: statusFilter, search }),
    [statusFilter, search],
  );
  useDataRefresh(reload);

  const upload = async (file: File) => {
    setUploading(true);
    try {
      const created = await documentApi.upload(file);
      if (created.status === 'READY') {
        toast.success(`Indexed “${created.name}”`, `${created.chunkCount} passage(s) are now searchable.`);
      } else {
        toast.failure(
          `“${created.name}” could not be indexed`,
          created.failureReason ?? 'The file was stored but its text could not be extracted.',
        );
      }
      reload();
    } catch (caught) {
      toast.failure(
        'Upload rejected',
        caught instanceof ApiError ? caught.message : 'The file could not be uploaded.',
      );
    } finally {
      setUploading(false);
      if (inputRef.current) {
        inputRef.current.value = '';
      }
    }
  };

  const onFileChosen = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      void upload(file);
    }
  };

  const onDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      void upload(file);
    }
  };

  const reindex = async () => {
    try {
      const result = await knowledgeApi.reindex();
      toast.success(
        'Knowledge base re-embedded',
        `${result.chunksEmbedded} passage(s) across ${result.documentsProcessed} document(s) using ${result.provider}.`,
      );
      reload();
    } catch (caught) {
      toast.failure('Reindex failed', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  const confirmDelete = async () => {
    if (!pendingDelete) {
      return;
    }
    try {
      await documentApi.remove(pendingDelete.id);
      toast.success('Document deleted');
      setPendingDelete(null);
      reload();
    } catch (caught) {
      toast.failure('Could not delete the document', caught instanceof ApiError ? caught.message : undefined);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Documents</h1>
          <p>
            PDF, TXT and Markdown up to 8 MB. Files are validated by extension, content type and
            magic bytes before anything is stored.
          </p>
        </div>
        <button type="button" className="btn" onClick={() => void reindex()}>
          Re-embed knowledge base
        </button>
      </div>

      <div
        className="card"
        style={{
          marginBottom: 16,
          borderStyle: dragging ? 'dashed' : 'solid',
          borderColor: dragging ? 'var(--accent)' : undefined,
          background: dragging ? 'var(--accent-soft)' : undefined,
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
      >
        <div className="row row--between">
          <div>
            <strong>Upload a document</strong>
            <div className="small muted">Drag a file here, or choose one from disk.</div>
          </div>
          <div className="row">
            <input
              ref={inputRef}
              id="document-file"
              type="file"
              accept=".pdf,.txt,.md,.markdown"
              onChange={onFileChosen}
              disabled={uploading}
              aria-label="Choose a document to upload"
            />
            <button type="button" className="btn btn--primary" onClick={() => inputRef.current?.click()} disabled={uploading}>
              {uploading ? 'Uploading…' : 'Choose file'}
            </button>
          </div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: 16 }}>
        <div className="filters">
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="document-status">Status</label>
            <select
              id="document-status"
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value as DocumentStatus | '')}
            >
              <option value="">Any</option>
              {STATUSES.map((value) => (
                <option key={value} value={value}>
                  {value}
                </option>
              ))}
            </select>
          </div>
          <div className="field" style={{ marginBottom: 0 }}>
            <label htmlFor="document-search">Search by name</label>
            <input
              id="document-search"
              type="text"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
          </div>
        </div>
      </div>

      {loading ? <LoadingState label="Loading documents…" /> : null}
      {error ? <ErrorState error={error} onRetry={reload} /> : null}

      {!loading && !error && data ? (
        data.length === 0 ? (
          <EmptyState
            title="No documents yet"
            message="Upload a resume, a spec or your notes — then ask questions about them from the Knowledge page."
          />
        ) : (
          <div className="card card--flush">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Status</th>
                  <th scope="col">Size</th>
                  <th scope="col">Passages</th>
                  <th scope="col">Uploaded</th>
                  <th scope="col" className="numeric">
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.map((document) => (
                  <tr key={document.id}>
                    <td>
                      <Link to={`/documents/${document.id}`}>{document.name}</Link>
                      {document.failureReason ? (
                        <div className="small" style={{ color: 'var(--danger)' }}>
                          {document.failureReason}
                        </div>
                      ) : null}
                    </td>
                    <td>
                      <Badge tone={documentTone(document.status)}>{document.status}</Badge>
                    </td>
                    <td>{formatBytes(document.sizeBytes)}</td>
                    <td>{document.chunkCount}</td>
                    <td>{formatDateTime(document.createdAt)}</td>
                    <td className="numeric">
                      <div className="row row--end" style={{ flexWrap: 'nowrap' }}>
                        <Link className="btn btn--sm" to={`/documents/${document.id}`}>
                          Open
                        </Link>
                        <button type="button" className="btn btn--sm btn--ghost" onClick={() => setPendingDelete(document)}>
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
        title="Delete this document?"
        message={`“${pendingDelete?.name ?? ''}” and every passage indexed from it will be removed.`}
        onConfirm={confirmDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

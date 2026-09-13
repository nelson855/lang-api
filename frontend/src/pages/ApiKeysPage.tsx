import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { PortalApiError } from '../api/envelope';
import { Button } from '../components/ui/Button';
import { DataTable } from '../components/ui/DataTable';
import { Dialog } from '../components/ui/Dialog';
import { Empty } from '../components/feedback/Feedback';
import { Pagination } from '../components/ui/Pagination';
import { useAuthProfile } from '../features/auth/authState';
import { ApiKeyForm } from '../features/apiKeys/ApiKeyForm';
import { RevealDialog } from '../features/apiKeys/RevealDialog';
import { useApiKeysQuery, useCreateApiKey, useDeleteApiKey, useSetApiKeyStatus, useUpdateApiKey } from '../features/apiKeys/useApiKeys';
import { StatusBadge } from '../features/apiKeys/StatusBadge';
import type { ApiKey, CreateApiKeyInput, UpdateApiKeyInput } from '../api/apiKeys';
import './ApiKeysPage.css';

const PAGE_SIZE = 20;
const STATUSES = ['enabled', 'disabled', 'expired', 'exhausted'] as const;

type DialogState = { type: 'create' } | { type: 'edit'; key: ApiKey } | null;
type ConfirmState = { type: 'status'; key: ApiKey; enabled: boolean } | { type: 'delete'; key: ApiKey } | null;

function serverMessage(error: unknown, fallback: string): string {
  if (error instanceof PortalApiError) {
    return error.message;
  }
  return fallback;
}

export function ApiKeysPage() {
  const { t } = useTranslation();
  const profile = useAuthProfile();
  const userId = profile?.id ?? 0;
  const [page, setPage] = useState(1);
  const [nameInput, setNameInput] = useState('');
  const [name, setName] = useState('');
  const [status, setStatus] = useState('');
  const [dialog, setDialog] = useState<DialogState>(null);
  const [confirm, setConfirm] = useState<ConfirmState>(null);
  const [revealKey, setRevealKey] = useState<ApiKey | null>(null);
  const [guide, setGuide] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setName((current) => {
        if (current !== nameInput.trim()) {
          setPage(1);
        }
        return nameInput.trim();
      });
    }, 300);
    return () => window.clearTimeout(timer);
  }, [nameInput]);

  const query = useApiKeysQuery(userId, { page, pageSize: PAGE_SIZE, name, status });
  const createMutation = useCreateApiKey(userId);
  const updateMutation = useUpdateApiKey(userId);
  const statusMutation = useSetApiKeyStatus(userId);
  const deleteMutation = useDeleteApiKey(userId);
  const data = query.data?.data;
  const error = query.error instanceof PortalApiError ? query.error : null;

  const onStatusChange = (value: string) => {
    setStatus(value);
    setPage(1);
  };

  const closeDialog = () => setDialog(null);

  const submitCreate = (input: CreateApiKeyInput) => {
    createMutation.mutate(input, {
      onSuccess: () => {
        setDialog(null);
        setNameInput('');
        setName('');
        setStatus('');
        setPage(1);
        setGuide(true);
      },
    });
  };

  const submitEdit = (id: number, input: UpdateApiKeyInput) => {
    updateMutation.mutate(
      { id, patch: input },
      {
        onSuccess: () => {
          setDialog(null);
        },
      },
    );
  };

  const submitConfirm = () => {
    if (confirm?.type === 'status') {
      const target = confirm;
      statusMutation.mutate(
        { id: target.key.id, enabled: target.enabled },
        {
          onSuccess: () => {
            setConfirm(null);
          },
        },
      );
    } else if (confirm?.type === 'delete') {
      const target = confirm;
      deleteMutation.mutate(target.key.id, {
        onSuccess: () => {
          setConfirm(null);
        },
      });
    }
  };

  const confirmPending = statusMutation.isPending || deleteMutation.isPending;
  const confirmError =
    statusMutation.error instanceof PortalApiError
      ? statusMutation.error
      : deleteMutation.error instanceof PortalApiError
        ? deleteMutation.error
        : null;
  const confirmErrorText = (error: PortalApiError): string => {
    if (error.code === 'OPERATION_RESULT_UNKNOWN') {
      return t('pages.apiKeys.unknownHint', { requestId: error.requestId ?? '' });
    }
    return error.message;
  };

  return (
    <>
      <h1>{t('pages.apiKeys.title')}</h1>
      <div className="apikeys-toolbar">
        <label className="apikeys-field" htmlFor="apikeys-search">
          {t('pages.apiKeys.searchLabel')}
        </label>
        <input
          id="apikeys-search"
          className="input"
          type="search"
          placeholder={t('pages.apiKeys.searchPlaceholder')}
          value={nameInput}
          onChange={(event) => setNameInput(event.target.value)}
        />
        <label className="apikeys-field" htmlFor="apikeys-status">
          {t('pages.apiKeys.statusLabel')}
        </label>
        <select
          id="apikeys-status"
          className="input"
          value={status}
          onChange={(event) => onStatusChange(event.target.value)}
        >
          <option value="">{t('pages.apiKeys.statusAll')}</option>
          {STATUSES.map((item) => (
            <option key={item} value={item}>
              {t(`pages.apiKeys.status_${item}`)}
            </option>
          ))}
        </select>
        <Button type="button" variant="primary" onClick={() => setDialog({ type: 'create' })}>
          {t('pages.apiKeys.create')}
        </Button>
      </div>
      {guide ? (
        <div className="apikeys-notice" role="status">
          <p>{t('pages.apiKeys.guideTitle')}</p>
          <p>{t('pages.apiKeys.createdHint')}</p>
          <Button type="button" variant="secondary" onClick={() => setGuide(false)}>
            {t('pages.apiKeys.guideDismiss')}
          </Button>
        </div>
      ) : null}
      {query.isPending ? <p>{t('pages.apiKeys.loading')}</p> : null}
      {error?.code === 'OPERATION_RESULT_UNKNOWN' ? (
        <div className="apikeys-notice" role="status">
          <p>{t('pages.apiKeys.unknownTitle')}</p>
          <p>{t('pages.apiKeys.unknownHint', { requestId: error.requestId ?? '' })}</p>
        </div>
      ) : null}
      {query.isError && error?.code !== 'OPERATION_RESULT_UNKNOWN' ? (
        <div className="apikeys-notice" role="alert">
          <p>{t('pages.apiKeys.loadError')}</p>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => query.refetch()}>
            {t('common.retry')}
          </button>
        </div>
      ) : null}
      {data ? (
        data.total === 0 ? (
          <Empty
            description={
              name === '' && status === ''
                ? t('pages.apiKeys.emptyFirst')
                : t('pages.apiKeys.emptyNoMatch')
            }
          />
        ) : (
          <>
            <DataTable<ApiKey>
              columns={[
                { key: 'name', header: t('pages.apiKeys.colName'), render: (row) => row.name },
                { key: 'maskedKey', header: t('pages.apiKeys.colKey'), render: (row) => <code>{row.maskedKey}</code> },
                {
                  key: 'status',
                  header: t('pages.apiKeys.colStatus'),
                  render: (row) => <StatusBadge status={row.status} />,
                },
                {
                  key: 'expiresAt',
                  header: t('pages.apiKeys.colExpires'),
                  render: (row) => row.expiresAt ?? t('pages.apiKeys.neverExpires'),
                },
                {
                  key: 'actions',
                  header: t('pages.apiKeys.colActions'),
                  render: (row) => (
                    <>
                      <Button
                        type="button"
                        variant="quiet"
                        aria-label={`${t('pages.apiKeys.edit')} ${row.name}`}
                        onClick={() => setDialog({ type: 'edit', key: row })}
                      >
                        {t('pages.apiKeys.edit')}
                      </Button>
                      {row.status === 'enabled' || row.status === 'disabled' ? (
                        <Button
                          type="button"
                          variant="quiet"
                          aria-label={`${row.status === 'enabled' ? t('pages.apiKeys.disable') : t('pages.apiKeys.enable')} ${row.name}`}
                          onClick={() =>
                            setConfirm({ type: 'status', key: row, enabled: row.status !== 'enabled' })
                          }
                        >
                          {row.status === 'enabled' ? t('pages.apiKeys.disable') : t('pages.apiKeys.enable')}
                        </Button>
                      ) : null}
                      <Button
                        type="button"
                        variant="quiet"
                        aria-label={`${t('pages.apiKeys.delete')} ${row.name}`}
                        onClick={() => setConfirm({ type: 'delete', key: row })}
                      >
                        {t('pages.apiKeys.delete')}
                      </Button>
                      <Button
                        type="button"
                        variant="quiet"
                        aria-label={`${t('pages.apiKeys.reveal')} ${row.name}`}
                        onClick={() => setRevealKey(row)}
                      >
                        {t('pages.apiKeys.reveal')}
                      </Button>
                    </>
                  ),
                },
              ]}
              rows={data.items}
              rowKey={(row) => String(row.id)}
            />
            <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={setPage} />
          </>
        )
      ) : null}
      <Dialog
        open={dialog?.type === 'create'}
        onOpenChange={(open) => {
          if (!open) {
            closeDialog();
          }
        }}
        title={t('pages.apiKeys.create')}
      >
        {dialog?.type === 'create' ? (
          <ApiKeyForm
            mode="create"
            pending={createMutation.isPending}
            serverError={createMutation.error ? serverMessage(createMutation.error, t('states.error.title')) : null}
            onSubmit={submitCreate}
            onCancel={closeDialog}
          />
        ) : null}
      </Dialog>
      <Dialog
        open={dialog?.type === 'edit'}
        onOpenChange={(open) => {
          if (!open) {
            closeDialog();
          }
        }}
        title={t('pages.apiKeys.edit')}
      >
        {dialog?.type === 'edit' ? (
          <ApiKeyForm
            mode="edit"
            initial={{
              name: dialog.key.name,
              unlimited: dialog.key.quota.unlimited,
              remaining: dialog.key.quota.remaining,
              expiresAt: dialog.key.expiresAt,
              models: dialog.key.modelRestrictions.models,
              ips: dialog.key.allowedIps,
            }}
            pending={updateMutation.isPending}
            serverError={updateMutation.error ? serverMessage(updateMutation.error, t('states.error.title')) : null}
            onSubmit={(input) => submitEdit(dialog.key.id, input)}
            onCancel={closeDialog}
          />
        ) : null}
      </Dialog>
      <Dialog
        open={confirm !== null}
        onOpenChange={(open) => {
          if (!open && !confirmPending) {
            setConfirm(null);
          }
        }}
        title={
          confirm?.type === 'status'
            ? confirm.enabled
              ? t('pages.apiKeys.enableTitle')
              : t('pages.apiKeys.disableTitle')
            : t('pages.apiKeys.deleteTitle')
        }
      >
        {confirm ? (
          <>
            <p>{confirm.key.name}</p>
            <p>
              {confirm.type === 'status'
                ? confirm.enabled
                  ? t('pages.apiKeys.enableRisk')
                  : t('pages.apiKeys.disableRisk')
                : t('pages.apiKeys.deleteRisk')}
            </p>
            {confirmError ? <p role="alert">{confirmErrorText(confirmError)}</p> : null}
            <div className="form-actions">
              <Button type="button" variant="primary" disabled={confirmPending} onClick={submitConfirm}>
                {confirm.type === 'status'
                  ? confirm.enabled
                    ? t('pages.apiKeys.confirmEnable')
                    : t('pages.apiKeys.confirmDisable')
                  : t('pages.apiKeys.confirmDelete')}
              </Button>
            </div>
          </>
        ) : null}
      </Dialog>
      {revealKey ? <RevealDialog apiKey={revealKey} onClose={() => setRevealKey(null)} /> : null}
    </>
  );
}

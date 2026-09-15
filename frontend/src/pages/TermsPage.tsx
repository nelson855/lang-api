import { useEffect } from 'react';
import { useOutletContext } from 'react-router';
import { LEGAL_TERMS_PATH, useLegalDocument } from '../api/legal';
import { usePublicConfigData } from '../app/providers/publicConfigGate';
import { LegalDocumentView } from '../components/legal/LegalDocumentView';

export function TermsPage() {
  const query = useLegalDocument(LEGAL_TERMS_PATH);
  const { supportUrl } = usePublicConfigData();
  const outlet = useOutletContext<{ setLegalIndexable?: (available: boolean) => void } | null>();
  useEffect(() => {
    if (query.data) {
      outlet?.setLegalIndexable?.(true);
    } else if (query.isError) {
      outlet?.setLegalIndexable?.(false);
    }
  }, [query.data, query.isError, outlet]);
  return (
    <main>
      <LegalDocumentView query={query} supportUrl={supportUrl} />
    </main>
  );
}

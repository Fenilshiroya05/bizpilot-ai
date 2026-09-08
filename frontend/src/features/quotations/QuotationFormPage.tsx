import { useNavigate } from 'react-router-dom'

import { QuotationForm } from '@/features/quotations/components/QuotationForm'
import { PageHeader } from '@/components/layout/PageHeader'

export function QuotationFormPage() {
  const navigate = useNavigate()

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="New quotation"
        breadcrumb={[{ label: 'Quotations', to: '/quotations' }, { label: 'New' }]}
      />
      <QuotationForm onSaved={(quotation) => navigate(`/quotations/${quotation.id}`)} onCancel={() => navigate('/quotations')} />
    </div>
  )
}

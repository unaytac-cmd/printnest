import { useState } from 'react';
import {
  DollarSign,
  TrendingUp,
  TrendingDown,
  Calendar,
  Download,
  CheckCircle,
  XCircle,
  Clock,
  Building2,
  ArrowUpRight,
  ArrowDownRight,
  RefreshCw,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface Transaction {
  id: string;
  tenantName: string;
  tenantSlug: string;
  type: 'subscription' | 'overage' | 'refund' | 'addon';
  amount: number;
  status: 'completed' | 'pending' | 'failed';
  description: string;
  date: string;
}

interface Subscription {
  id: string;
  tenantName: string;
  tenantSlug: string;
  plan: 'starter' | 'professional' | 'enterprise';
  amount: number;
  status: 'active' | 'canceled' | 'past_due';
  nextBilling: string;
  startDate: string;
}

const mockTransactions: Transaction[] = [
  {
    id: 'txn_1',
    tenantName: 'Acme Print Co',
    tenantSlug: 'acme',
    type: 'subscription',
    amount: 199,
    status: 'completed',
    description: 'Enterprise plan - Monthly',
    date: '2024-03-15T10:30:00Z',
  },
  {
    id: 'txn_2',
    tenantName: 'Best POD Shop',
    tenantSlug: 'bestpod',
    type: 'subscription',
    amount: 79,
    status: 'completed',
    description: 'Professional plan - Monthly',
    date: '2024-03-14T15:45:00Z',
  },
  {
    id: 'txn_3',
    tenantName: 'Cool Tees',
    tenantSlug: 'cooltees',
    type: 'subscription',
    amount: 29,
    status: 'pending',
    description: 'Starter plan - Monthly',
    date: '2024-03-14T09:00:00Z',
  },
  {
    id: 'txn_4',
    tenantName: 'PrintMaster Pro',
    tenantSlug: 'printmaster',
    type: 'overage',
    amount: 45,
    status: 'completed',
    description: 'Order overage - 450 extra orders',
    date: '2024-03-13T11:00:00Z',
  },
  {
    id: 'txn_5',
    tenantName: 'Design Hub',
    tenantSlug: 'designhub',
    type: 'refund',
    amount: -50,
    status: 'completed',
    description: 'Partial refund - Service credit',
    date: '2024-03-12T14:30:00Z',
  },
];

const mockSubscriptions: Subscription[] = [
  {
    id: 'sub_1',
    tenantName: 'Acme Print Co',
    tenantSlug: 'acme',
    plan: 'enterprise',
    amount: 199,
    status: 'active',
    nextBilling: '2024-04-15',
    startDate: '2024-01-15',
  },
  {
    id: 'sub_2',
    tenantName: 'Best POD Shop',
    tenantSlug: 'bestpod',
    plan: 'professional',
    amount: 79,
    status: 'active',
    nextBilling: '2024-04-14',
    startDate: '2024-02-14',
  },
  {
    id: 'sub_3',
    tenantName: 'PrintMaster Pro',
    tenantSlug: 'printmaster',
    plan: 'professional',
    amount: 79,
    status: 'past_due',
    nextBilling: '2024-03-10',
    startDate: '2024-01-10',
  },
];

export default function BillingPage() {
  const [activeTab, setActiveTab] = useState<'transactions' | 'subscriptions'>('transactions');
  const [dateRange, setDateRange] = useState('30d');

  const totalRevenue = mockTransactions
    .filter(t => t.status === 'completed' && t.amount > 0)
    .reduce((sum, t) => sum + t.amount, 0);

  const pendingAmount = mockTransactions
    .filter(t => t.status === 'pending')
    .reduce((sum, t) => sum + t.amount, 0);

  const refundAmount = mockTransactions
    .filter(t => t.type === 'refund')
    .reduce((sum, t) => sum + Math.abs(t.amount), 0);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'completed':
      case 'active':
        return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
      case 'pending':
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
      case 'failed':
      case 'canceled':
      case 'past_due':
        return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'completed':
      case 'active':
        return <CheckCircle className="w-3 h-3" />;
      case 'pending':
        return <Clock className="w-3 h-3" />;
      case 'failed':
      case 'canceled':
      case 'past_due':
        return <XCircle className="w-3 h-3" />;
      default:
        return null;
    }
  };

  const getTypeColor = (type: string) => {
    switch (type) {
      case 'subscription':
        return 'text-blue-600';
      case 'overage':
        return 'text-purple-600';
      case 'refund':
        return 'text-red-600';
      case 'addon':
        return 'text-green-600';
      default:
        return 'text-gray-600';
    }
  };

  const getPlanColor = (plan: string) => {
    switch (plan) {
      case 'enterprise':
        return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400';
      case 'professional':
        return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
      case 'starter':
        return 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-300';
      default:
        return 'bg-gray-100 text-gray-800';
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Billing Overview</h1>
          <p className="text-muted-foreground">
            Track revenue, subscriptions, and transactions
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={dateRange}
            onChange={(e) => setDateRange(e.target.value)}
            className="px-3 py-2 border border-border rounded-lg bg-background"
          >
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="90d">Last 90 days</option>
            <option value="1y">Last year</option>
          </select>
          <button className="flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors">
            <Download className="w-4 h-4" />
            Export
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Total Revenue</p>
              <p className="text-3xl font-bold mt-2">{formatCurrency(totalRevenue)}</p>
              <div className="flex items-center gap-1 mt-2 text-sm text-green-600">
                <ArrowUpRight className="w-4 h-4" />
                +18.2% vs last month
              </div>
            </div>
            <div className="w-12 h-12 rounded-xl bg-green-100 dark:bg-green-900/30 flex items-center justify-center">
              <DollarSign className="w-6 h-6 text-green-600" />
            </div>
          </div>
        </div>

        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground">MRR</p>
              <p className="text-3xl font-bold mt-2">$48,392</p>
              <div className="flex items-center gap-1 mt-2 text-sm text-green-600">
                <ArrowUpRight className="w-4 h-4" />
                +12 new subscriptions
              </div>
            </div>
            <div className="w-12 h-12 rounded-xl bg-blue-100 dark:bg-blue-900/30 flex items-center justify-center">
              <TrendingUp className="w-6 h-6 text-blue-600" />
            </div>
          </div>
        </div>

        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Pending</p>
              <p className="text-3xl font-bold mt-2">{formatCurrency(pendingAmount)}</p>
              <div className="flex items-center gap-1 mt-2 text-sm text-yellow-600">
                <Clock className="w-4 h-4" />
                3 transactions
              </div>
            </div>
            <div className="w-12 h-12 rounded-xl bg-yellow-100 dark:bg-yellow-900/30 flex items-center justify-center">
              <RefreshCw className="w-6 h-6 text-yellow-600" />
            </div>
          </div>
        </div>

        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Refunds</p>
              <p className="text-3xl font-bold mt-2">{formatCurrency(refundAmount)}</p>
              <div className="flex items-center gap-1 mt-2 text-sm text-red-600">
                <ArrowDownRight className="w-4 h-4" />
                1.2% refund rate
              </div>
            </div>
            <div className="w-12 h-12 rounded-xl bg-red-100 dark:bg-red-900/30 flex items-center justify-center">
              <TrendingDown className="w-6 h-6 text-red-600" />
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-border">
        <div className="flex gap-4">
          <button
            onClick={() => setActiveTab('transactions')}
            className={cn(
              'px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              activeTab === 'transactions'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            Recent Transactions
          </button>
          <button
            onClick={() => setActiveTab('subscriptions')}
            className={cn(
              'px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors',
              activeTab === 'subscriptions'
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            )}
          >
            Active Subscriptions
          </button>
        </div>
      </div>

      {/* Content */}
      {activeTab === 'transactions' && (
        <div className="bg-card rounded-xl border border-border overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Tenant</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Type</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Description</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Amount</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Status</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Date</th>
              </tr>
            </thead>
            <tbody>
              {mockTransactions.map((txn) => (
                <tr key={txn.id} className="border-b border-border hover:bg-muted/50 transition-colors">
                  <td className="py-4 px-4">
                    <div className="flex items-center gap-2">
                      <Building2 className="w-4 h-4 text-muted-foreground" />
                      <span className="font-medium">{txn.tenantName}</span>
                    </div>
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn('capitalize font-medium', getTypeColor(txn.type))}>
                      {txn.type}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-muted-foreground">{txn.description}</td>
                  <td className="py-4 px-4 text-right">
                    <span className={cn('font-medium', txn.amount < 0 ? 'text-red-600' : 'text-green-600')}>
                      {txn.amount < 0 ? '-' : '+'}{formatCurrency(Math.abs(txn.amount))}
                    </span>
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn('inline-flex items-center gap-1 px-2 py-1 text-xs rounded-full capitalize', getStatusColor(txn.status))}>
                      {getStatusIcon(txn.status)}
                      {txn.status}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-muted-foreground">
                    <div className="flex items-center gap-1">
                      <Calendar className="w-3 h-3" />
                      {formatDate(txn.date)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {activeTab === 'subscriptions' && (
        <div className="bg-card rounded-xl border border-border overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Tenant</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Plan</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Amount</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Status</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Next Billing</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Started</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {mockSubscriptions.map((sub) => (
                <tr key={sub.id} className="border-b border-border hover:bg-muted/50 transition-colors">
                  <td className="py-4 px-4">
                    <div className="flex items-center gap-2">
                      <Building2 className="w-4 h-4 text-muted-foreground" />
                      <span className="font-medium">{sub.tenantName}</span>
                    </div>
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn('px-2 py-1 text-xs rounded-full capitalize', getPlanColor(sub.plan))}>
                      {sub.plan}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-right font-medium">
                    {formatCurrency(sub.amount)}/mo
                  </td>
                  <td className="py-4 px-4">
                    <span className={cn('inline-flex items-center gap-1 px-2 py-1 text-xs rounded-full capitalize', getStatusColor(sub.status))}>
                      {getStatusIcon(sub.status)}
                      {sub.status.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="py-4 px-4 text-muted-foreground">
                    {formatDate(sub.nextBilling)}
                  </td>
                  <td className="py-4 px-4 text-muted-foreground">
                    {formatDate(sub.startDate)}
                  </td>
                  <td className="py-4 px-4">
                    <button className="text-sm text-primary hover:underline">Manage</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

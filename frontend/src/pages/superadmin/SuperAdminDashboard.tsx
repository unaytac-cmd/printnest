import {
  Building2,
  Users,
  DollarSign,
  TrendingUp,
  Package,
  AlertCircle,
  CheckCircle,
  Clock,
  ArrowUpRight,
  ArrowDownRight
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface StatCardProps {
  title: string;
  value: string;
  change: string;
  changeType: 'positive' | 'negative' | 'neutral';
  icon: React.ComponentType<{ className?: string }>;
  subtitle?: string;
}

function StatCard({ title, value, change, changeType, icon: Icon, subtitle }: StatCardProps) {
  return (
    <div className="bg-card rounded-xl border border-border p-6">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{title}</p>
          <p className="text-3xl font-bold mt-2">{value}</p>
          {subtitle && (
            <p className="text-sm text-muted-foreground mt-1">{subtitle}</p>
          )}
          <div className={cn(
            'flex items-center gap-1 mt-3 text-sm',
            changeType === 'positive' && 'text-green-600',
            changeType === 'negative' && 'text-red-600',
            changeType === 'neutral' && 'text-muted-foreground'
          )}>
            {changeType === 'positive' ? (
              <ArrowUpRight className="w-4 h-4" />
            ) : changeType === 'negative' ? (
              <ArrowDownRight className="w-4 h-4" />
            ) : null}
            {change}
          </div>
        </div>
        <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center">
          <Icon className="w-6 h-6 text-primary" />
        </div>
      </div>
    </div>
  );
}

interface TenantRowProps {
  name: string;
  slug: string;
  plan: 'starter' | 'professional' | 'enterprise';
  status: 'active' | 'suspended' | 'trial';
  orders: number;
  revenue: string;
  createdAt: string;
}

function TenantRow({ name, slug, plan, status, orders, revenue, createdAt }: TenantRowProps) {
  const planColors = {
    starter: 'bg-gray-100 text-gray-800 dark:bg-gray-800 dark:text-gray-200',
    professional: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    enterprise: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
  };

  const statusColors = {
    active: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    suspended: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    trial: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
  };

  const statusIcons = {
    active: CheckCircle,
    suspended: AlertCircle,
    trial: Clock,
  };

  const StatusIcon = statusIcons[status];

  return (
    <tr className="border-b border-border hover:bg-muted/50 transition-colors">
      <td className="py-4 px-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
            <Building2 className="w-5 h-5 text-primary" />
          </div>
          <div>
            <p className="font-medium">{name}</p>
            <p className="text-sm text-muted-foreground">{slug}.printnest.com</p>
          </div>
        </div>
      </td>
      <td className="py-4 px-4">
        <span className={cn('px-2 py-1 text-xs rounded-full capitalize', planColors[plan])}>
          {plan}
        </span>
      </td>
      <td className="py-4 px-4">
        <span className={cn('inline-flex items-center gap-1 px-2 py-1 text-xs rounded-full capitalize', statusColors[status])}>
          <StatusIcon className="w-3 h-3" />
          {status}
        </span>
      </td>
      <td className="py-4 px-4 text-right">{orders.toLocaleString()}</td>
      <td className="py-4 px-4 text-right font-medium">{revenue}</td>
      <td className="py-4 px-4 text-muted-foreground">{createdAt}</td>
      <td className="py-4 px-4">
        <button className="text-sm text-primary hover:underline">Manage</button>
      </td>
    </tr>
  );
}

export default function SuperAdminDashboard() {
  const stats: StatCardProps[] = [
    {
      title: 'Total Tenants',
      value: '247',
      change: '+12 this month',
      changeType: 'positive',
      icon: Building2,
      subtitle: '189 active, 58 trial',
    },
    {
      title: 'Total Users',
      value: '1,847',
      change: '+156 this month',
      changeType: 'positive',
      icon: Users,
    },
    {
      title: 'Monthly Revenue',
      value: '$48,392',
      change: '+18.2% vs last month',
      changeType: 'positive',
      icon: DollarSign,
    },
    {
      title: 'Total Orders',
      value: '12,456',
      change: '+2,341 this month',
      changeType: 'positive',
      icon: Package,
    },
  ];

  const recentTenants: TenantRowProps[] = [
    {
      name: 'Acme Print Co',
      slug: 'acme',
      plan: 'enterprise',
      status: 'active',
      orders: 1245,
      revenue: '$12,456',
      createdAt: '2024-01-15',
    },
    {
      name: 'Best POD Shop',
      slug: 'bestpod',
      plan: 'professional',
      status: 'active',
      orders: 856,
      revenue: '$8,234',
      createdAt: '2024-02-20',
    },
    {
      name: 'Cool Tees',
      slug: 'cooltees',
      plan: 'starter',
      status: 'trial',
      orders: 23,
      revenue: '$456',
      createdAt: '2024-03-01',
    },
    {
      name: 'PrintMaster Pro',
      slug: 'printmaster',
      plan: 'professional',
      status: 'active',
      orders: 567,
      revenue: '$5,678',
      createdAt: '2024-01-28',
    },
    {
      name: 'Design Hub',
      slug: 'designhub',
      plan: 'enterprise',
      status: 'active',
      orders: 2341,
      revenue: '$23,456',
      createdAt: '2023-11-10',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Platform Overview</h1>
          <p className="text-muted-foreground">
            Welcome to PrintNest Super Admin Dashboard
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button className="px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors">
            Export Report
          </button>
          <button className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors">
            Add Tenant
          </button>
        </div>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <StatCard key={stat.title} {...stat} />
        ))}
      </div>

      {/* Revenue Chart Placeholder */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-card rounded-xl border border-border p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-lg font-semibold">Revenue Overview</h2>
            <select className="px-3 py-1.5 border border-border rounded-lg bg-background text-sm">
              <option>Last 30 days</option>
              <option>Last 90 days</option>
              <option>Last 12 months</option>
            </select>
          </div>
          <div className="h-64 flex items-center justify-center border-2 border-dashed border-border rounded-lg">
            <div className="text-center text-muted-foreground">
              <TrendingUp className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>Revenue chart will be displayed here</p>
            </div>
          </div>
        </div>

        {/* Plan Distribution */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-6">Plan Distribution</h2>
          <div className="space-y-4">
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm">Enterprise</span>
                <span className="text-sm font-medium">45 (18%)</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div className="h-full bg-purple-500 rounded-full" style={{ width: '18%' }} />
              </div>
            </div>
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm">Professional</span>
                <span className="text-sm font-medium">112 (45%)</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div className="h-full bg-blue-500 rounded-full" style={{ width: '45%' }} />
              </div>
            </div>
            <div>
              <div className="flex items-center justify-between mb-2">
                <span className="text-sm">Starter</span>
                <span className="text-sm font-medium">90 (37%)</span>
              </div>
              <div className="h-2 bg-muted rounded-full overflow-hidden">
                <div className="h-full bg-gray-500 rounded-full" style={{ width: '37%' }} />
              </div>
            </div>
          </div>

          <div className="mt-6 pt-6 border-t border-border">
            <h3 className="text-sm font-medium mb-3">Quick Stats</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Avg. Revenue/Tenant</span>
                <span className="font-medium">$196/mo</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Churn Rate</span>
                <span className="font-medium text-green-600">2.3%</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Trial Conversion</span>
                <span className="font-medium">34%</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Tenants Table */}
      <div className="bg-card rounded-xl border border-border">
        <div className="p-6 border-b border-border">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Recent Tenants</h2>
            <a href="/superadmin/tenants" className="text-sm text-primary hover:underline">
              View all tenants
            </a>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Tenant</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Plan</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Status</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Orders</th>
                <th className="text-right py-3 px-4 text-sm font-medium text-muted-foreground">Revenue</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Created</th>
                <th className="text-left py-3 px-4 text-sm font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {recentTenants.map((tenant) => (
                <TenantRow key={tenant.slug} {...tenant} />
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

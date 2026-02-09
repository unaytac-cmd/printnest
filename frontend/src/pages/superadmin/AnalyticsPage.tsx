import { useState } from 'react';
import {
  BarChart3,
  TrendingUp,
  TrendingDown,
  Users,
  Building2,
  Package,
  DollarSign,
  Activity,
  ArrowUpRight,
  ArrowDownRight,
  Calendar,
  Globe,
  Layers,
} from 'lucide-react';
import { cn } from '@/lib/utils';

interface MetricCardProps {
  title: string;
  value: string;
  change: string;
  changeType: 'positive' | 'negative' | 'neutral';
  icon: React.ComponentType<{ className?: string }>;
  sparkline?: number[];
}

function MetricCard({ title, value, change, changeType, icon: Icon, sparkline }: MetricCardProps) {
  return (
    <div className="bg-card rounded-xl border border-border p-6">
      <div className="flex items-start justify-between mb-4">
        <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
          <Icon className="w-5 h-5 text-primary" />
        </div>
        <div className={cn(
          'flex items-center gap-1 text-sm',
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
      <p className="text-sm text-muted-foreground">{title}</p>
      <p className="text-2xl font-bold mt-1">{value}</p>
      {sparkline && (
        <div className="flex items-end gap-1 mt-4 h-8">
          {sparkline.map((val, i) => (
            <div
              key={i}
              className="flex-1 bg-primary/20 rounded-t"
              style={{ height: `${val}%` }}
            />
          ))}
        </div>
      )}
    </div>
  );
}

export default function AnalyticsPage() {
  const [timeRange, setTimeRange] = useState('30d');

  const metrics: MetricCardProps[] = [
    {
      title: 'Total Tenants',
      value: '247',
      change: '+12 (5.1%)',
      changeType: 'positive',
      icon: Building2,
      sparkline: [40, 55, 45, 60, 70, 65, 80, 75, 90, 85, 95, 100],
    },
    {
      title: 'Active Users',
      value: '1,847',
      change: '+156 (9.2%)',
      changeType: 'positive',
      icon: Users,
      sparkline: [30, 40, 35, 50, 45, 60, 55, 70, 75, 80, 85, 90],
    },
    {
      title: 'Monthly Orders',
      value: '12,456',
      change: '+2,341 (23.1%)',
      changeType: 'positive',
      icon: Package,
      sparkline: [20, 30, 40, 35, 50, 60, 55, 70, 80, 75, 90, 100],
    },
    {
      title: 'Revenue',
      value: '$48,392',
      change: '+$7,234 (17.6%)',
      changeType: 'positive',
      icon: DollarSign,
      sparkline: [45, 50, 55, 60, 58, 65, 70, 72, 78, 82, 88, 92],
    },
    {
      title: 'Gangsheets Created',
      value: '3,456',
      change: '+456 (15.2%)',
      changeType: 'positive',
      icon: Layers,
      sparkline: [35, 40, 50, 45, 55, 60, 65, 70, 75, 80, 85, 95],
    },
    {
      title: 'Avg. Session Duration',
      value: '12m 34s',
      change: '-45s (5.6%)',
      changeType: 'negative',
      icon: Activity,
      sparkline: [80, 75, 70, 72, 68, 65, 63, 60, 58, 55, 52, 50],
    },
  ];

  const topTenants = [
    { name: 'Acme Print Co', orders: 2341, revenue: '$23,456', growth: '+15%' },
    { name: 'Design Hub', orders: 1856, revenue: '$18,234', growth: '+12%' },
    { name: 'PrintMaster Pro', orders: 1245, revenue: '$12,456', growth: '+8%' },
    { name: 'Best POD Shop', orders: 987, revenue: '$9,876', growth: '+22%' },
    { name: 'Cool Tees', orders: 654, revenue: '$6,543', growth: '+5%' },
  ];

  const topRegions = [
    { name: 'United States', tenants: 156, percentage: 63 },
    { name: 'United Kingdom', tenants: 34, percentage: 14 },
    { name: 'Canada', tenants: 28, percentage: 11 },
    { name: 'Australia', tenants: 18, percentage: 7 },
    { name: 'Other', tenants: 11, percentage: 5 },
  ];

  const planDistribution = [
    { name: 'Enterprise', count: 45, percentage: 18, revenue: '$8,955', color: 'bg-purple-500' },
    { name: 'Professional', count: 112, percentage: 45, revenue: '$8,848', color: 'bg-blue-500' },
    { name: 'Starter', count: 90, percentage: 37, revenue: '$2,610', color: 'bg-gray-500' },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Platform Analytics</h1>
          <p className="text-muted-foreground">
            Comprehensive platform metrics and insights
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 px-3 py-2 border border-border rounded-lg">
            <Calendar className="w-4 h-4 text-muted-foreground" />
            <select
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              className="bg-transparent border-none focus:outline-none"
            >
              <option value="7d">Last 7 days</option>
              <option value="30d">Last 30 days</option>
              <option value="90d">Last 90 days</option>
              <option value="1y">Last 12 months</option>
            </select>
          </div>
        </div>
      </div>

      {/* Metrics Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {metrics.map((metric) => (
          <MetricCard key={metric.title} {...metric} />
        ))}
      </div>

      {/* Charts Row */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Revenue Chart */}
        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-lg font-semibold">Revenue Trend</h2>
            <div className="flex items-center gap-4 text-sm">
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-primary" />
                <span className="text-muted-foreground">Revenue</span>
              </div>
              <div className="flex items-center gap-2">
                <div className="w-3 h-3 rounded-full bg-green-500" />
                <span className="text-muted-foreground">Orders</span>
              </div>
            </div>
          </div>
          <div className="h-64 flex items-center justify-center border-2 border-dashed border-border rounded-lg">
            <div className="text-center text-muted-foreground">
              <BarChart3 className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>Revenue chart visualization</p>
              <p className="text-sm">Integrate with charting library</p>
            </div>
          </div>
        </div>

        {/* User Activity */}
        <div className="bg-card rounded-xl border border-border p-6">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-lg font-semibold">User Activity</h2>
            <span className="text-sm text-muted-foreground">Last 24 hours</span>
          </div>
          <div className="h-64 flex items-center justify-center border-2 border-dashed border-border rounded-lg">
            <div className="text-center text-muted-foreground">
              <Activity className="w-12 h-12 mx-auto mb-2 opacity-50" />
              <p>Activity heatmap</p>
              <p className="text-sm">Shows active users by hour</p>
            </div>
          </div>
        </div>
      </div>

      {/* Bottom Row */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Top Tenants */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-4">Top Tenants</h2>
          <div className="space-y-4">
            {topTenants.map((tenant, index) => (
              <div key={tenant.name} className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-lg bg-primary/10 flex items-center justify-center text-sm font-medium text-primary">
                    {index + 1}
                  </div>
                  <div>
                    <p className="font-medium">{tenant.name}</p>
                    <p className="text-sm text-muted-foreground">{tenant.orders} orders</p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-medium">{tenant.revenue}</p>
                  <p className="text-sm text-green-600">{tenant.growth}</p>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Geographic Distribution */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-4">Geographic Distribution</h2>
          <div className="space-y-4">
            {topRegions.map((region) => (
              <div key={region.name}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <Globe className="w-4 h-4 text-muted-foreground" />
                    <span className="text-sm">{region.name}</span>
                  </div>
                  <span className="text-sm font-medium">{region.tenants} ({region.percentage}%)</span>
                </div>
                <div className="h-2 bg-muted rounded-full overflow-hidden">
                  <div
                    className="h-full bg-primary rounded-full"
                    style={{ width: `${region.percentage}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Plan Distribution */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-4">Plan Distribution</h2>
          <div className="space-y-4">
            {planDistribution.map((plan) => (
              <div key={plan.name}>
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <div className={cn('w-3 h-3 rounded-full', plan.color)} />
                    <span className="text-sm">{plan.name}</span>
                  </div>
                  <span className="text-sm font-medium">{plan.count} tenants</span>
                </div>
                <div className="h-2 bg-muted rounded-full overflow-hidden">
                  <div
                    className={cn('h-full rounded-full', plan.color)}
                    style={{ width: `${plan.percentage}%` }}
                  />
                </div>
                <p className="text-xs text-muted-foreground mt-1">
                  MRR: {plan.revenue}
                </p>
              </div>
            ))}
          </div>

          <div className="mt-6 pt-6 border-t border-border">
            <div className="flex items-center justify-between text-sm">
              <span className="text-muted-foreground">Total MRR</span>
              <span className="font-bold text-lg">$20,413</span>
            </div>
          </div>
        </div>
      </div>

      {/* Key Insights */}
      <div className="bg-card rounded-xl border border-border p-6">
        <h2 className="text-lg font-semibold mb-4">Key Insights</h2>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="p-4 bg-green-50 dark:bg-green-900/20 rounded-lg border border-green-200 dark:border-green-800">
            <div className="flex items-center gap-2 mb-2">
              <TrendingUp className="w-5 h-5 text-green-600" />
              <span className="font-medium text-green-800 dark:text-green-400">Growth Opportunity</span>
            </div>
            <p className="text-sm text-green-700 dark:text-green-300">
              Professional plan has highest conversion rate (34%). Consider targeted upsell campaigns.
            </p>
          </div>
          <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
            <div className="flex items-center gap-2 mb-2">
              <Activity className="w-5 h-5 text-blue-600" />
              <span className="font-medium text-blue-800 dark:text-blue-400">Engagement</span>
            </div>
            <p className="text-sm text-blue-700 dark:text-blue-300">
              Peak usage hours: 9AM-12PM EST. Schedule maintenance outside these windows.
            </p>
          </div>
          <div className="p-4 bg-yellow-50 dark:bg-yellow-900/20 rounded-lg border border-yellow-200 dark:border-yellow-800">
            <div className="flex items-center gap-2 mb-2">
              <TrendingDown className="w-5 h-5 text-yellow-600" />
              <span className="font-medium text-yellow-800 dark:text-yellow-400">Attention Needed</span>
            </div>
            <p className="text-sm text-yellow-700 dark:text-yellow-300">
              3 tenants at risk of churn (no login in 14+ days). Trigger re-engagement emails.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}

import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui';
import { formatCurrency } from '@/lib/utils';
import { cn } from '@/lib/utils';
import {
  ShoppingCartIcon,
  DollarSignIcon,
  ClockIcon,
  TruckIcon,
  TrendingUpIcon,
  TrendingDownIcon,
} from 'lucide-react';

interface StatCardProps {
  title: string;
  value: string | number;
  icon: React.ReactNode;
  change?: {
    value: number;
    label: string;
  };
  loading?: boolean;
}

function StatCard({ title, value, icon, change, loading = false }: StatCardProps) {
  const isPositive = change ? change.value >= 0 : true;

  if (loading) {
    return (
      <Card>
        <CardHeader className="flex flex-row items-center justify-between pb-2">
          <CardTitle className="text-sm font-medium text-muted-foreground">
            {title}
          </CardTitle>
          <div className="h-5 w-5 animate-pulse rounded bg-muted" />
        </CardHeader>
        <CardContent>
          <div className="h-8 w-24 animate-pulse rounded bg-muted" />
          <div className="mt-2 h-4 w-32 animate-pulse rounded bg-muted" />
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {title}
        </CardTitle>
        <div className="rounded-md bg-muted p-2">{icon}</div>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold">{value}</div>
        {change && (
          <div className="mt-1 flex items-center gap-1 text-sm">
            {isPositive ? (
              <TrendingUpIcon className="h-4 w-4 text-green-500" />
            ) : (
              <TrendingDownIcon className="h-4 w-4 text-red-500" />
            )}
            <span
              className={cn(
                'font-medium',
                isPositive ? 'text-green-500' : 'text-red-500'
              )}
            >
              {isPositive ? '+' : ''}{change.value}%
            </span>
            <span className="text-muted-foreground">{change.label}</span>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

interface DashboardStatsData {
  totalOrders: number;
  totalOrdersChange: number;
  revenue: number;
  revenueChange: number;
  pendingOrders: number;
  pendingOrdersChange: number;
  shippedToday: number;
  shippedTodayChange: number;
}

interface DashboardStatsProps {
  data?: DashboardStatsData;
  loading?: boolean;
  className?: string;
}

export function DashboardStats({
  data,
  loading = false,
  className,
}: DashboardStatsProps) {
  const stats = [
    {
      title: 'Total Orders',
      value: data?.totalOrders.toLocaleString() ?? '0',
      icon: <ShoppingCartIcon className="h-4 w-4 text-muted-foreground" />,
      change: data
        ? { value: data.totalOrdersChange, label: 'from last period' }
        : undefined,
    },
    {
      title: 'Revenue',
      value: formatCurrency(data?.revenue ?? 0),
      icon: <DollarSignIcon className="h-4 w-4 text-muted-foreground" />,
      change: data
        ? { value: data.revenueChange, label: 'from last period' }
        : undefined,
    },
    {
      title: 'Pending Orders',
      value: data?.pendingOrders.toLocaleString() ?? '0',
      icon: <ClockIcon className="h-4 w-4 text-muted-foreground" />,
      change: data
        ? { value: data.pendingOrdersChange, label: 'from last period' }
        : undefined,
    },
    {
      title: 'Shipped Today',
      value: data?.shippedToday.toLocaleString() ?? '0',
      icon: <TruckIcon className="h-4 w-4 text-muted-foreground" />,
      change: data
        ? { value: data.shippedTodayChange, label: 'from yesterday' }
        : undefined,
    },
  ];

  return (
    <div
      className={cn(
        'grid gap-4 md:grid-cols-2 lg:grid-cols-4',
        className
      )}
    >
      {stats.map((stat) => (
        <StatCard
          key={stat.title}
          title={stat.title}
          value={stat.value}
          icon={stat.icon}
          change={stat.change}
          loading={loading}
        />
      ))}
    </div>
  );
}

import { BarChart3, DollarSign, Package, ShoppingCart, TrendingUp, Users } from 'lucide-react';
import { cn } from '@/lib/utils';

interface StatCardProps {
  title: string;
  value: string;
  change: string;
  changeType: 'positive' | 'negative' | 'neutral';
  icon: React.ComponentType<{ className?: string }>;
}

function StatCard({ title, value, change, changeType, icon: Icon }: StatCardProps) {
  return (
    <div className="bg-card rounded-xl border border-border p-6 card-hover">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{title}</p>
          <p className="text-2xl font-bold mt-1">{value}</p>
          <p
            className={cn(
              'text-sm mt-2 flex items-center gap-1',
              changeType === 'positive' && 'text-green-600 dark:text-green-400',
              changeType === 'negative' && 'text-red-600 dark:text-red-400',
              changeType === 'neutral' && 'text-muted-foreground'
            )}
          >
            <TrendingUp
              className={cn(
                'w-4 h-4',
                changeType === 'negative' && 'rotate-180'
              )}
            />
            {change}
          </p>
        </div>
        <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center">
          <Icon className="w-6 h-6 text-primary" />
        </div>
      </div>
    </div>
  );
}

interface RecentOrderProps {
  id: string;
  customer: string;
  product: string;
  amount: string;
  status: 'pending' | 'processing' | 'shipped' | 'delivered';
}

function RecentOrder({ customer, product, amount, status }: RecentOrderProps) {
  const statusColors = {
    pending: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    processing: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    shipped: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    delivered: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  };

  return (
    <div className="flex items-center justify-between py-3 border-b border-border last:border-0">
      <div className="flex items-center gap-4">
        <div className="w-10 h-10 rounded-lg bg-muted flex items-center justify-center">
          <Package className="w-5 h-5 text-muted-foreground" />
        </div>
        <div>
          <p className="font-medium">{customer}</p>
          <p className="text-sm text-muted-foreground">{product}</p>
        </div>
      </div>
      <div className="text-right">
        <p className="font-medium">{amount}</p>
        <span
          className={cn(
            'inline-block px-2 py-0.5 text-xs rounded-full capitalize',
            statusColors[status]
          )}
        >
          {status}
        </span>
      </div>
    </div>
  );
}

export default function Dashboard() {
  const stats: StatCardProps[] = [
    {
      title: 'Total Revenue',
      value: '$12,456',
      change: '+12.5% from last month',
      changeType: 'positive',
      icon: DollarSign,
    },
    {
      title: 'Orders',
      value: '156',
      change: '+8.2% from last month',
      changeType: 'positive',
      icon: ShoppingCart,
    },
    {
      title: 'Products',
      value: '48',
      change: '+3 new this month',
      changeType: 'neutral',
      icon: Package,
    },
    {
      title: 'Customers',
      value: '1,234',
      change: '+24 new this week',
      changeType: 'positive',
      icon: Users,
    },
  ];

  const recentOrders: RecentOrderProps[] = [
    {
      id: 'ORD-001',
      customer: 'John Doe',
      product: 'Custom T-Shirt',
      amount: '$29.99',
      status: 'delivered',
    },
    {
      id: 'ORD-002',
      customer: 'Jane Smith',
      product: 'Hoodie - Black',
      amount: '$59.99',
      status: 'shipped',
    },
    {
      id: 'ORD-003',
      customer: 'Mike Johnson',
      product: 'Mug Set (3)',
      amount: '$34.99',
      status: 'processing',
    },
    {
      id: 'ORD-004',
      customer: 'Sarah Williams',
      product: 'Phone Case',
      amount: '$19.99',
      status: 'pending',
    },
    {
      id: 'ORD-005',
      customer: 'Chris Brown',
      product: 'Poster - Large',
      amount: '$24.99',
      status: 'processing',
    },
  ];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground">
          Welcome back! Here's what's happening with your store.
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {stats.map((stat) => (
          <StatCard key={stat.title} {...stat} />
        ))}
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Orders */}
        <div className="lg:col-span-2 bg-card rounded-xl border border-border p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">Recent Orders</h2>
            <a
              href="/orders"
              className="text-sm text-primary hover:underline"
            >
              View all
            </a>
          </div>
          <div className="space-y-1">
            {recentOrders.map((order) => (
              <RecentOrder key={order.id} {...order} />
            ))}
          </div>
        </div>

        {/* Quick Actions */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
          <div className="space-y-3">
            <a
              href="/products/new"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Package className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">Add New Product</p>
                <p className="text-sm text-muted-foreground">
                  Create a new product listing
                </p>
              </div>
            </a>
            <a
              href="/design-studio"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <BarChart3 className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">Design Studio</p>
                <p className="text-sm text-muted-foreground">
                  Create custom designs
                </p>
              </div>
            </a>
            <a
              href="/analytics"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <TrendingUp className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">View Analytics</p>
                <p className="text-sm text-muted-foreground">
                  Track your performance
                </p>
              </div>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}

import { BarChart3, DollarSign, Package, ShoppingCart, TrendingUp, TrendingDown, Users, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useDashboard } from '@/api/hooks/useDashboard';
import { useTenant } from '@/stores/tenantStore';

interface StatCardProps {
  title: string;
  value: string;
  change: number;
  icon: React.ComponentType<{ className?: string }>;
  loading?: boolean;
}

function StatCard({ title, value, change, icon: Icon, loading }: StatCardProps) {
  const isPositive = change >= 0;

  return (
    <div className="bg-card rounded-xl border border-border p-6 card-hover">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-muted-foreground">{title}</p>
          {loading ? (
            <div className="h-8 w-24 bg-muted animate-pulse rounded mt-1" />
          ) : (
            <p className="text-2xl font-bold mt-1">{value}</p>
          )}
          {!loading && (
            <p
              className={cn(
                'text-sm mt-2 flex items-center gap-1',
                isPositive ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'
              )}
            >
              {isPositive ? (
                <TrendingUp className="w-4 h-4" />
              ) : (
                <TrendingDown className="w-4 h-4" />
              )}
              {isPositive ? '+' : ''}{change}% from last month
            </p>
          )}
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
  status: 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled';
}

function RecentOrder({ customer, product, amount, status }: RecentOrderProps) {
  const statusColors = {
    pending: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    processing: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    shipped: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    delivered: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    cancelled: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
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

function EmptyState() {
  return (
    <div className="text-center py-12">
      <Package className="w-16 h-16 mx-auto text-muted-foreground/50 mb-4" />
      <h3 className="text-lg font-semibold mb-2">No orders yet</h3>
      <p className="text-muted-foreground mb-6">
        Start by connecting your ShipStation account to import orders.
      </p>
      <a
        href="/settings/integrations"
        className="inline-block px-6 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
      >
        Connect ShipStation
      </a>
    </div>
  );
}

export default function Dashboard() {
  const { data, isLoading } = useDashboard();
  const tenant = useTenant();

  const formatCurrency = (amount: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(amount);
  };

  const stats = data?.stats || {
    totalRevenue: 0,
    totalOrders: 0,
    totalProducts: 0,
    totalCustomers: 0,
    revenueChange: 0,
    ordersChange: 0,
    productsChange: 0,
    customersChange: 0,
  };

  const recentOrders = data?.recentOrders || [];

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground">
          Welcome back{tenant?.name ? ` to ${tenant.name}` : ''}! Here's what's happening with your store.
        </p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Revenue"
          value={formatCurrency(stats.totalRevenue)}
          change={stats.revenueChange}
          icon={DollarSign}
          loading={isLoading}
        />
        <StatCard
          title="Orders"
          value={stats.totalOrders.toLocaleString()}
          change={stats.ordersChange}
          icon={ShoppingCart}
          loading={isLoading}
        />
        <StatCard
          title="Products"
          value={stats.totalProducts.toLocaleString()}
          change={stats.productsChange}
          icon={Package}
          loading={isLoading}
        />
        <StatCard
          title="Customers"
          value={stats.totalCustomers.toLocaleString()}
          change={stats.customersChange}
          icon={Users}
          loading={isLoading}
        />
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

          {isLoading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
            </div>
          ) : recentOrders.length > 0 ? (
            <div className="space-y-1">
              {recentOrders.map((order) => (
                <RecentOrder
                  key={order.id}
                  id={order.id}
                  customer={order.customerName}
                  product={order.productTitle}
                  amount={formatCurrency(order.amount)}
                  status={order.status}
                />
              ))}
            </div>
          ) : (
            <EmptyState />
          )}
        </div>

        {/* Quick Actions */}
        <div className="bg-card rounded-xl border border-border p-6">
          <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
          <div className="space-y-3">
            <a
              href="/settings/integrations"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <ShoppingCart className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">Sync Orders</p>
                <p className="text-sm text-muted-foreground">
                  Import from ShipStation
                </p>
              </div>
            </a>
            <a
              href="/gangsheet"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <BarChart3 className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">Create Gangsheet</p>
                <p className="text-sm text-muted-foreground">
                  Generate print layouts
                </p>
              </div>
            </a>
            <a
              href="/products"
              className="flex items-center gap-3 p-3 rounded-lg bg-primary/5 hover:bg-primary/10 transition-colors"
            >
              <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <Package className="w-5 h-5 text-primary" />
              </div>
              <div>
                <p className="font-medium">Manage Products</p>
                <p className="text-sm text-muted-foreground">
                  Add or edit products
                </p>
              </div>
            </a>
          </div>
        </div>
      </div>
    </div>
  );
}

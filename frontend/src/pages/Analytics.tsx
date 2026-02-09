import { BarChart3, TrendingUp, TrendingDown, DollarSign, ShoppingCart, Users, Eye } from 'lucide-react';
import { cn } from '@/lib/utils';

interface MetricCardProps {
  title: string;
  value: string;
  change: number;
  icon: React.ComponentType<{ className?: string }>;
}

function MetricCard({ title, value, change, icon: Icon }: MetricCardProps) {
  const isPositive = change >= 0;

  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <div className="flex items-center justify-between mb-4">
        <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
          <Icon className="w-5 h-5 text-primary" />
        </div>
        <div
          className={cn(
            'flex items-center gap-1 text-sm',
            isPositive ? 'text-green-600' : 'text-red-600'
          )}
        >
          {isPositive ? (
            <TrendingUp className="w-4 h-4" />
          ) : (
            <TrendingDown className="w-4 h-4" />
          )}
          <span>{Math.abs(change)}%</span>
        </div>
      </div>
      <p className="text-2xl font-bold">{value}</p>
      <p className="text-sm text-muted-foreground">{title}</p>
    </div>
  );
}

function ChartPlaceholder({ title }: { title: string }) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h3 className="text-lg font-semibold mb-4">{title}</h3>
      <div className="h-64 flex items-center justify-center bg-muted/50 rounded-lg">
        <div className="text-center">
          <BarChart3 className="w-12 h-12 mx-auto text-muted-foreground mb-2" />
          <p className="text-sm text-muted-foreground">
            Chart visualization coming soon
          </p>
        </div>
      </div>
    </div>
  );
}

interface TopProductProps {
  rank: number;
  name: string;
  sales: number;
  revenue: number;
}

function TopProduct({ rank, name, sales, revenue }: TopProductProps) {
  return (
    <div className="flex items-center gap-4 py-3 border-b border-border last:border-0">
      <span
        className={cn(
          'w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold',
          rank === 1 && 'bg-yellow-100 text-yellow-800',
          rank === 2 && 'bg-gray-100 text-gray-800',
          rank === 3 && 'bg-orange-100 text-orange-800',
          rank > 3 && 'bg-muted text-muted-foreground'
        )}
      >
        {rank}
      </span>
      <div className="flex-1">
        <p className="font-medium">{name}</p>
        <p className="text-sm text-muted-foreground">{sales} sales</p>
      </div>
      <p className="font-semibold">${revenue.toFixed(2)}</p>
    </div>
  );
}

export default function Analytics() {
  const metrics: MetricCardProps[] = [
    { title: 'Total Revenue', value: '$24,567', change: 12.5, icon: DollarSign },
    { title: 'Total Orders', value: '456', change: 8.2, icon: ShoppingCart },
    { title: 'New Customers', value: '89', change: -3.1, icon: Users },
    { title: 'Page Views', value: '12,456', change: 15.8, icon: Eye },
  ];

  const topProducts: TopProductProps[] = [
    { rank: 1, name: 'Premium Hoodie - Black', sales: 156, revenue: 9359.44 },
    { rank: 2, name: 'Classic T-Shirt - White', sales: 134, revenue: 4016.66 },
    { rank: 3, name: 'Ceramic Mug - Custom', sales: 98, revenue: 1469.02 },
    { rank: 4, name: 'Phone Case - iPhone 15', sales: 76, revenue: 1519.24 },
    { rank: 5, name: 'Canvas Poster - Large', sales: 54, revenue: 2159.46 },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Analytics</h1>
          <p className="text-muted-foreground">
            Track your store performance and insights
          </p>
        </div>
        <div className="flex items-center gap-2">
          <select className="px-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring">
            <option value="7d">Last 7 days</option>
            <option value="30d">Last 30 days</option>
            <option value="90d">Last 90 days</option>
            <option value="1y">Last year</option>
          </select>
        </div>
      </div>

      {/* Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {metrics.map((metric) => (
          <MetricCard key={metric.title} {...metric} />
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <ChartPlaceholder title="Revenue Over Time" />
        <ChartPlaceholder title="Orders by Category" />
      </div>

      {/* Top Products & Traffic Sources */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Top Products */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h3 className="text-lg font-semibold mb-4">Top Products</h3>
          <div>
            {topProducts.map((product) => (
              <TopProduct key={product.rank} {...product} />
            ))}
          </div>
        </div>

        {/* Traffic Sources */}
        <div className="bg-card border border-border rounded-xl p-6">
          <h3 className="text-lg font-semibold mb-4">Traffic Sources</h3>
          <div className="space-y-4">
            {[
              { source: 'Direct', percentage: 35 },
              { source: 'Organic Search', percentage: 28 },
              { source: 'Social Media', percentage: 22 },
              { source: 'Referral', percentage: 10 },
              { source: 'Email', percentage: 5 },
            ].map((item) => (
              <div key={item.source}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-sm font-medium">{item.source}</span>
                  <span className="text-sm text-muted-foreground">
                    {item.percentage}%
                  </span>
                </div>
                <div className="h-2 bg-muted rounded-full overflow-hidden">
                  <div
                    className="h-full bg-primary rounded-full"
                    style={{ width: `${item.percentage}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

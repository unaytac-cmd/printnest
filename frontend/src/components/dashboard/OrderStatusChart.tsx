import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui';
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Legend,
  Tooltip,
} from 'recharts';

interface OrderStatusData {
  status: string;
  count: number;
  color: string;
}

interface OrderStatusChartProps {
  data?: OrderStatusData[];
  loading?: boolean;
  className?: string;
}

const defaultData: OrderStatusData[] = [
  { status: 'Pending', count: 0, color: '#f97316' },
  { status: 'In Production', count: 0, color: '#8b5cf6' },
  { status: 'Shipped', count: 0, color: '#22c55e' },
  { status: 'Cancelled', count: 0, color: '#ef4444' },
];

const CustomTooltip = ({
  active,
  payload,
}: {
  active?: boolean;
  payload?: Array<{ payload: OrderStatusData }>;
}) => {
  if (active && payload && payload.length) {
    const data = payload[0].payload;
    return (
      <div className="rounded-lg border bg-background p-3 shadow-lg">
        <p className="font-medium">{data.status}</p>
        <p className="text-sm text-muted-foreground">
          Orders: <span className="font-medium text-foreground">{data.count}</span>
        </p>
      </div>
    );
  }
  return null;
};

const CustomLegend = ({
  payload,
}: {
  payload?: Array<{ value: string; color: string; payload: OrderStatusData }>;
}) => {
  if (!payload) return null;

  return (
    <ul className="flex flex-wrap justify-center gap-4">
      {payload.map((entry, index) => (
        <li key={`item-${index}`} className="flex items-center gap-2">
          <span
            className="h-3 w-3 rounded-full"
            style={{ backgroundColor: entry.color }}
          />
          <span className="text-sm text-muted-foreground">
            {entry.value}{' '}
            <span className="font-medium text-foreground">
              ({entry.payload.count})
            </span>
          </span>
        </li>
      ))}
    </ul>
  );
};

export function OrderStatusChart({
  data = defaultData,
  loading = false,
  className,
}: OrderStatusChartProps) {
  const chartData = data.filter((d) => d.count > 0);
  const totalOrders = chartData.reduce((sum, d) => sum + d.count, 0);

  return (
    <Card className={className}>
      <CardHeader>
        <CardTitle>Order Status Distribution</CardTitle>
      </CardHeader>
      <CardContent>
        {loading ? (
          <div className="flex h-[300px] items-center justify-center">
            <div className="animate-pulse text-muted-foreground">
              Loading chart data...
            </div>
          </div>
        ) : chartData.length === 0 ? (
          <div className="flex h-[300px] items-center justify-center text-muted-foreground">
            No orders to display
          </div>
        ) : (
          <div className="h-[300px]">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={chartData}
                  cx="50%"
                  cy="45%"
                  innerRadius={60}
                  outerRadius={90}
                  paddingAngle={2}
                  dataKey="count"
                  nameKey="status"
                >
                  {chartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip content={<CustomTooltip />} />
                <Legend
                  content={<CustomLegend />}
                  verticalAlign="bottom"
                  height={50}
                />
              </PieChart>
            </ResponsiveContainer>

            {/* Center text */}
            <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
              <div className="text-center -translate-y-4">
                <p className="text-3xl font-bold">{totalOrders}</p>
                <p className="text-sm text-muted-foreground">Total Orders</p>
              </div>
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

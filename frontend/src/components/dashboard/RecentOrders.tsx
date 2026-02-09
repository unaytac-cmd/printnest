import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui';
import { Button } from '@/components/ui';
import { OrderStatusBadge, mapApiStatusToBadgeStatus } from '@/components/orders';
import { formatCurrency, formatDate } from '@/lib/utils';
import type { Order } from '@/types';
import { ArrowRightIcon } from 'lucide-react';
import { Link } from 'react-router-dom';

interface RecentOrdersProps {
  orders?: Order[];
  loading?: boolean;
  onViewOrder?: (order: Order) => void;
  className?: string;
}

function OrderRow({
  order,
  onView,
}: {
  order: Order;
  onView?: (order: Order) => void;
}) {
  return (
    <tr className="border-b last:border-b-0">
      <td className="py-3">
        <button
          onClick={() => onView?.(order)}
          className="font-medium text-primary hover:underline"
        >
          #{order.orderNumber}
        </button>
      </td>
      <td className="py-3">
        <div>
          <p className="font-medium">
            {order.customer.firstName} {order.customer.lastName}
          </p>
          <p className="text-sm text-muted-foreground">
            {order.customer.email}
          </p>
        </div>
      </td>
      <td className="py-3 text-right">
        {formatCurrency(order.total)}
      </td>
      <td className="py-3">
        <OrderStatusBadge
          status={mapApiStatusToBadgeStatus(order.status, order.paymentStatus)}
        />
      </td>
      <td className="py-3 text-right text-muted-foreground">
        {formatDate(order.createdAt)}
      </td>
    </tr>
  );
}

function LoadingRow() {
  return (
    <tr className="border-b last:border-b-0">
      <td className="py-3">
        <div className="h-4 w-16 animate-pulse rounded bg-muted" />
      </td>
      <td className="py-3">
        <div className="space-y-1">
          <div className="h-4 w-32 animate-pulse rounded bg-muted" />
          <div className="h-3 w-40 animate-pulse rounded bg-muted" />
        </div>
      </td>
      <td className="py-3">
        <div className="ml-auto h-4 w-16 animate-pulse rounded bg-muted" />
      </td>
      <td className="py-3">
        <div className="h-5 w-20 animate-pulse rounded-full bg-muted" />
      </td>
      <td className="py-3">
        <div className="ml-auto h-4 w-24 animate-pulse rounded bg-muted" />
      </td>
    </tr>
  );
}

export function RecentOrders({
  orders = [],
  loading = false,
  onViewOrder,
  className,
}: RecentOrdersProps) {
  return (
    <Card className={className}>
      <CardHeader className="flex flex-row items-center justify-between">
        <CardTitle>Recent Orders</CardTitle>
        <Link to="/orders">
          <Button variant="ghost" size="sm">
            View All
            <ArrowRightIcon className="ml-2 h-4 w-4" />
          </Button>
        </Link>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b text-left text-sm text-muted-foreground">
                <th className="pb-3 font-medium">Order</th>
                <th className="pb-3 font-medium">Customer</th>
                <th className="pb-3 text-right font-medium">Total</th>
                <th className="pb-3 font-medium">Status</th>
                <th className="pb-3 text-right font-medium">Date</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <>
                  <LoadingRow />
                  <LoadingRow />
                  <LoadingRow />
                  <LoadingRow />
                  <LoadingRow />
                </>
              ) : orders.length === 0 ? (
                <tr>
                  <td
                    colSpan={5}
                    className="py-8 text-center text-muted-foreground"
                  >
                    No recent orders
                  </td>
                </tr>
              ) : (
                orders.slice(0, 5).map((order) => (
                  <OrderRow
                    key={order.id}
                    order={order}
                    onView={onViewOrder}
                  />
                ))
              )}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

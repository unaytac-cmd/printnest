import { useState, useMemo } from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import { Search, Package, CheckCircle, Clock, XCircle, Loader2, Plus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useOrders } from '@/hooks/useOrders';
import type { OrderStatus } from '@/types';
import NewOrder from './orders/NewOrder';

type FilterStatus = OrderStatus | 'all';

function OrdersList() {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<FilterStatus>('all');
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data, isLoading, error } = useOrders({
    page,
    pageSize,
    search: searchQuery || undefined,
    status: statusFilter === 'all' ? undefined : statusFilter,
  });

  const orders = data?.data || [];
  const total = data?.total || 0;

  // Calculate status counts from current data (in real app, might want separate API call)
  const statusCounts = useMemo(() => {
    const counts: Record<FilterStatus, number> = {
      all: total,
      pending: 0,
      confirmed: 0,
      processing: 0,
      completed: 0,
      cancelled: 0,
    };
    orders.forEach((order) => {
      if (counts[order.status] !== undefined) {
        counts[order.status]++;
      }
    });
    return counts;
  }, [orders, total]);

  const getStatusIcon = (status: OrderStatus) => {
    switch (status) {
      case 'pending':
        return <Clock className="w-4 h-4" />;
      case 'confirmed':
      case 'processing':
        return <Package className="w-4 h-4" />;
      case 'completed':
        return <CheckCircle className="w-4 h-4" />;
      case 'cancelled':
        return <XCircle className="w-4 h-4" />;
      default:
        return <Package className="w-4 h-4" />;
    }
  };

  const statusColors: Record<string, string> = {
    pending: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    confirmed: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    processing: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    completed: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    cancelled: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-12">
        <p className="text-destructive">Failed to load orders</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Orders</h1>
          <p className="text-muted-foreground">Manage and track customer orders</p>
        </div>
        <Link
          to="/orders/new"
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          New Order
        </Link>
      </div>

      {/* Status Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        {(['all', 'pending', 'confirmed', 'processing', 'completed', 'cancelled'] as const).map(
          (status) => (
            <button
              key={status}
              onClick={() => {
                setStatusFilter(status);
                setPage(1);
              }}
              className={cn(
                'px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
                statusFilter === status
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted hover:bg-muted/80'
              )}
            >
              <span className="capitalize">{status}</span>
              <span className="ml-2 px-1.5 py-0.5 text-xs rounded-full bg-background/20">
                {statusCounts[status] || 0}
              </span>
            </button>
          )
        )}
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
        <input
          type="text"
          placeholder="Search orders by ID or customer..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
        />
      </div>

      {/* Orders List */}
      <div className="space-y-4">
        {orders.map((order) => (
          <Link
            key={order.id}
            to={`/orders/${order.id}`}
            className="block bg-card border border-border rounded-xl p-4 hover:shadow-md transition-shadow cursor-pointer"
          >
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-start gap-4">
                <div
                  className={cn(
                    'w-10 h-10 rounded-lg flex items-center justify-center',
                    statusColors[order.status] || statusColors.pending
                  )}
                >
                  {getStatusIcon(order.status)}
                </div>
                <div>
                  <p className="font-semibold">{order.orderNumber}</p>
                  <p className="text-sm text-muted-foreground">
                    {order.customer.firstName} {order.customer.lastName} - {order.customer.email}
                  </p>
                  <p className="text-sm text-muted-foreground mt-1">
                    {order.items.length} item{order.items.length > 1 ? 's' : ''} | {new Date(order.createdAt).toLocaleDateString()}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-4">
                <div className="text-right">
                  <p className="font-semibold">${order.total.toFixed(2)}</p>
                  <span
                    className={cn(
                      'inline-block px-2 py-1 text-xs rounded-full capitalize',
                      statusColors[order.status] || statusColors.pending
                    )}
                  >
                    {order.status}
                  </span>
                </div>
              </div>
            </div>
          </Link>
        ))}

        {orders.length === 0 && (
          <div className="text-center py-12">
            <Package className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No orders found</p>
          </div>
        )}
      </div>
    </div>
  );
}

export default function Orders() {
  return (
    <Routes>
      <Route index element={<OrdersList />} />
      <Route path="new" element={<NewOrder />} />
    </Routes>
  );
}

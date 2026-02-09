import { useState } from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import { Search, Package, CheckCircle, Clock, XCircle, Loader2, Plus, Truck, AlertCircle, Edit2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useOrders } from '@/hooks/useOrders';
import { OrderStatusCodes, getOrderStatusLabel, getOrderStatusColor } from '@/types';
import NewOrder from './orders/NewOrder';

function OrdersList() {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | 'all'>('all');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const { data, isLoading, error } = useOrders({
    page,
    pageSize,
    search: searchQuery || undefined,
    status: statusFilter === 'all' ? undefined : statusFilter,
  });

  const orders = data?.data || [];
  const total = data?.total || 0;
  const totalPages = data?.totalPages || 1;

  const getStatusIcon = (statusCode: number) => {
    switch (statusCode) {
      case OrderStatusCodes.NEW_ORDER:
      case OrderStatusCodes.PAYMENT_PENDING:
        return <Clock className="w-4 h-4" />;
      case OrderStatusCodes.PENDING:
      case OrderStatusCodes.URGENT:
        return <Package className="w-4 h-4" />;
      case OrderStatusCodes.IN_PRODUCTION:
        return <Edit2 className="w-4 h-4" />;
      case OrderStatusCodes.SHIPPED:
        return <Truck className="w-4 h-4" />;
      case OrderStatusCodes.COMPLETED:
        return <CheckCircle className="w-4 h-4" />;
      case OrderStatusCodes.CANCELLED:
      case OrderStatusCodes.DELETED:
        return <XCircle className="w-4 h-4" />;
      case OrderStatusCodes.INVALID_ADDRESS:
        return <AlertCircle className="w-4 h-4" />;
      default:
        return <Package className="w-4 h-4" />;
    }
  };

  // Status filter options
  const statusOptions = [
    { value: 'all' as const, label: 'All' },
    { value: OrderStatusCodes.NEW_ORDER, label: 'New' },
    { value: OrderStatusCodes.PENDING, label: 'Pending' },
    { value: OrderStatusCodes.IN_PRODUCTION, label: 'In Production' },
    { value: OrderStatusCodes.SHIPPED, label: 'Shipped' },
    { value: OrderStatusCodes.CANCELLED, label: 'Cancelled' },
  ];

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
        <AlertCircle className="w-12 h-12 mx-auto text-destructive mb-4" />
        <p className="text-destructive">Failed to load orders</p>
        <p className="text-sm text-muted-foreground mt-2">
          {error instanceof Error ? error.message : 'Unknown error'}
        </p>
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
        {statusOptions.map((option) => (
          <button
            key={option.value}
            onClick={() => {
              setStatusFilter(option.value);
              setPage(1);
            }}
            className={cn(
              'px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
              statusFilter === option.value
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted hover:bg-muted/80'
            )}
          >
            {option.label}
          </button>
        ))}
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
        {orders.map((order) => {
          const orderNumber = order.externalOrderId || order.intOrderId || `#${order.id}`;
          const statusColor = getOrderStatusColor(order.orderStatus);
          const statusLabel = getOrderStatusLabel(order.orderStatus);
          const productCount = order.products?.length || 0;
          const totalAmount = parseFloat(order.totalAmount) || 0;

          return (
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
                      statusColor
                    )}
                  >
                    {getStatusIcon(order.orderStatus)}
                  </div>
                  <div>
                    <p className="font-semibold">{orderNumber}</p>
                    <p className="text-sm text-muted-foreground">
                      {order.customerName || 'No customer'}
                      {order.customerEmail && ` - ${order.customerEmail}`}
                    </p>
                    <p className="text-sm text-muted-foreground mt-1">
                      {productCount} item{productCount !== 1 ? 's' : ''} | {new Date(order.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <div className="text-right">
                    <p className="font-semibold">${totalAmount.toFixed(2)}</p>
                    <span
                      className={cn(
                        'inline-block px-2 py-1 text-xs rounded-full',
                        statusColor
                      )}
                    >
                      {statusLabel}
                    </span>
                  </div>
                </div>
              </div>
            </Link>
          );
        })}

        {orders.length === 0 && (
          <div className="text-center py-12">
            <Package className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No orders found</p>
            <p className="text-sm text-muted-foreground mt-2">
              Orders synced from ShipStation will appear here
            </p>
          </div>
        )}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-center gap-2 pt-4">
          <button
            onClick={() => setPage(p => Math.max(1, p - 1))}
            disabled={page === 1}
            className="px-3 py-1 rounded border border-border disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
          >
            Previous
          </button>
          <span className="text-sm text-muted-foreground">
            Page {page} of {totalPages} ({total} orders)
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            className="px-3 py-1 rounded border border-border disabled:opacity-50 disabled:cursor-not-allowed hover:bg-muted"
          >
            Next
          </button>
        </div>
      )}
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

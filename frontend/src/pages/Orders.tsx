import { useState, useEffect } from 'react';
import { Routes, Route, Link, useLocation } from 'react-router-dom';
import { Search, Package, CheckCircle, Clock, XCircle, Loader2, Truck, AlertCircle, Edit2, RefreshCw, Store, ClipboardList, PackageCheck } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useOrders } from '@/hooks/useOrders';
import { useIsSubdealer, useAssignedStoreIds } from '@/stores/authStore';
import { OrderStatusCodes, getOrderStatusLabel, getOrderStatusColor } from '@/types';
import { useQueryClient, useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import NewOrder from './orders/NewOrder';

interface StoreInfo {
  id: number;
  shipstationStoreId: number;
  storeName: string;
  marketplaceName?: string;
  isActive: boolean;
}

// Shared order card component
function OrderCard({ order }: { order: any }) {
  const orderNumber = order.externalOrderId || order.intOrderId || `#${order.id}`;
  const statusColor = getOrderStatusColor(order.orderStatus);
  const statusLabel = getOrderStatusLabel(order.orderStatus);
  const productCount = order.products?.length || 0;
  const totalAmount = parseFloat(order.totalAmount) || 0;

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

  return (
    <Link
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
}

// Store filter component
function StoreFilter({
  stores,
  storeFilter,
  setStoreFilter,
  isSubdealer
}: {
  stores: StoreInfo[];
  storeFilter: number | 'all';
  setStoreFilter: (v: number | 'all') => void;
  isSubdealer: boolean;
}) {
  if (isSubdealer || stores.length === 0) return null;

  return (
    <div className="flex items-center gap-2">
      <Store className="w-4 h-4 text-muted-foreground" />
      <select
        value={storeFilter}
        onChange={(e) => setStoreFilter(e.target.value === 'all' ? 'all' : Number(e.target.value))}
        className="px-3 py-2 bg-background border border-border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-ring"
      >
        <option value="all">All Stores</option>
        {stores.map((store) => (
          <option key={store.id} value={store.id}>
            {store.storeName}
          </option>
        ))}
      </select>
    </div>
  );
}

// New Orders tab - awaiting shipment orders
function NewOrdersTab() {
  const [searchQuery, setSearchQuery] = useState('');
  const [storeFilter, setStoreFilter] = useState<number | 'all'>('all');
  const [page, setPage] = useState(1);
  const [isSyncing, setIsSyncing] = useState(false);
  const pageSize = 20;
  const queryClient = useQueryClient();

  const isSubdealer = useIsSubdealer();
  const assignedStoreIds = useAssignedStoreIds();

  const { data: stores = [] } = useQuery({
    queryKey: ['shipstation-stores'],
    queryFn: async () => {
      const response = await apiClient.get('/shipstation/stores');
      return response.data as StoreInfo[];
    },
  });

  useEffect(() => {
    if (isSubdealer && assignedStoreIds.length > 0 && storeFilter === 'all') {
      setStoreFilter(assignedStoreIds[0]);
    }
  }, [isSubdealer, assignedStoreIds, storeFilter]);

  const effectiveStoreFilter = storeFilter === 'all' ? undefined : storeFilter;

  const handleSyncOrders = async () => {
    setIsSyncing(true);
    try {
      if (isSubdealer && assignedStoreIds.length > 0) {
        for (const storeId of assignedStoreIds) {
          await apiClient.post('/shipstation/sync-orders', { storeId });
        }
      } else {
        await apiClient.post('/shipstation/sync-orders', {});
      }
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    } catch (err) {
      console.error('Failed to sync orders:', err);
    } finally {
      setIsSyncing(false);
    }
  };

  // Fetch orders that are NOT completed/shipped (awaiting processing)
  const { data, isLoading, error } = useOrders({
    page,
    pageSize,
    search: searchQuery || undefined,
    statuses: [
      OrderStatusCodes.NEW_ORDER,
      OrderStatusCodes.PENDING,
      OrderStatusCodes.URGENT,
      OrderStatusCodes.IN_PRODUCTION,
      OrderStatusCodes.PAYMENT_PENDING,
    ],
    storeId: effectiveStoreFilter,
  });

  const orders = data?.data || [];
  const total = data?.total || 0;
  const totalPages = data?.totalPages || 1;

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
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Actions */}
      <div className="flex flex-col sm:flex-row gap-4 justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search orders..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
        <div className="flex items-center gap-2">
          <StoreFilter
            stores={stores}
            storeFilter={storeFilter}
            setStoreFilter={setStoreFilter}
            isSubdealer={isSubdealer}
          />
          <button
            onClick={handleSyncOrders}
            disabled={isSyncing}
            className="inline-flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors disabled:opacity-50"
          >
            <RefreshCw className={cn("w-4 h-4", isSyncing && "animate-spin")} />
            {isSyncing ? 'Syncing...' : 'Sync'}
          </button>
        </div>
      </div>

      {/* Orders List */}
      <div className="space-y-4">
        {orders.map((order) => (
          <OrderCard key={order.id} order={order} />
        ))}

        {orders.length === 0 && (
          <div className="text-center py-12">
            <Package className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No pending orders</p>
            <p className="text-sm text-muted-foreground mt-2">
              New orders from ShipStation will appear here
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
            className="px-3 py-1 rounded border border-border disabled:opacity-50 hover:bg-muted"
          >
            Previous
          </button>
          <span className="text-sm text-muted-foreground">
            Page {page} of {totalPages} ({total} orders)
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            className="px-3 py-1 rounded border border-border disabled:opacity-50 hover:bg-muted"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

// Order List tab - completed/shipped orders
function OrderListTab() {
  const [searchQuery, setSearchQuery] = useState('');
  const [storeFilter, setStoreFilter] = useState<number | 'all'>('all');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const isSubdealer = useIsSubdealer();
  const assignedStoreIds = useAssignedStoreIds();

  const { data: stores = [] } = useQuery({
    queryKey: ['shipstation-stores'],
    queryFn: async () => {
      const response = await apiClient.get('/shipstation/stores');
      return response.data as StoreInfo[];
    },
  });

  useEffect(() => {
    if (isSubdealer && assignedStoreIds.length > 0 && storeFilter === 'all') {
      setStoreFilter(assignedStoreIds[0]);
    }
  }, [isSubdealer, assignedStoreIds, storeFilter]);

  const effectiveStoreFilter = storeFilter === 'all' ? undefined : storeFilter;

  // Fetch completed/shipped orders
  const { data, isLoading, error } = useOrders({
    page,
    pageSize,
    search: searchQuery || undefined,
    statuses: [
      OrderStatusCodes.SHIPPED,
      OrderStatusCodes.COMPLETED,
    ],
    storeId: effectiveStoreFilter,
  });

  const orders = data?.data || [];
  const total = data?.total || 0;
  const totalPages = data?.totalPages || 1;

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
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Actions */}
      <div className="flex flex-col sm:flex-row gap-4 justify-between">
        <div className="relative flex-1 max-w-md">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search completed orders..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
        <StoreFilter
          stores={stores}
          storeFilter={storeFilter}
          setStoreFilter={setStoreFilter}
          isSubdealer={isSubdealer}
        />
      </div>

      {/* Orders List */}
      <div className="space-y-4">
        {orders.map((order) => (
          <OrderCard key={order.id} order={order} />
        ))}

        {orders.length === 0 && (
          <div className="text-center py-12">
            <PackageCheck className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No completed orders yet</p>
            <p className="text-sm text-muted-foreground mt-2">
              Shipped and completed orders will appear here
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
            className="px-3 py-1 rounded border border-border disabled:opacity-50 hover:bg-muted"
          >
            Previous
          </button>
          <span className="text-sm text-muted-foreground">
            Page {page} of {totalPages} ({total} orders)
          </span>
          <button
            onClick={() => setPage(p => Math.min(totalPages, p + 1))}
            disabled={page === totalPages}
            className="px-3 py-1 rounded border border-border disabled:opacity-50 hover:bg-muted"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

// Main Orders Layout with Tabs
function OrdersLayout() {
  const location = useLocation();
  const currentPath = location.pathname;

  const tabs = [
    { path: '/orders', label: 'New Orders', icon: ClipboardList },
    { path: '/orders/list', label: 'Order List', icon: PackageCheck },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">Orders</h1>
        <p className="text-muted-foreground">Manage and track customer orders</p>
      </div>

      {/* Tabs */}
      <div className="border-b border-border">
        <nav className="flex gap-4">
          {tabs.map((tab) => {
            const isActive = currentPath === tab.path ||
              (tab.path === '/orders' && currentPath === '/orders');
            const Icon = tab.icon;

            return (
              <Link
                key={tab.path}
                to={tab.path}
                className={cn(
                  'flex items-center gap-2 px-4 py-3 border-b-2 -mb-px transition-colors',
                  isActive
                    ? 'border-primary text-primary font-medium'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                )}
              >
                <Icon className="w-4 h-4" />
                {tab.label}
              </Link>
            );
          })}
        </nav>
      </div>

      {/* Tab Content */}
      <Routes>
        <Route index element={<NewOrdersTab />} />
        <Route path="list" element={<OrderListTab />} />
        <Route path="new" element={<NewOrder />} />
        <Route path=":id" element={<NewOrder />} />
      </Routes>
    </div>
  );
}

export default function Orders() {
  return <OrdersLayout />;
}

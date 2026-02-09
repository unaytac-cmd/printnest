import { useState } from 'react';
import { Package, Truck, CheckCircle, Clock, AlertCircle, MapPin } from 'lucide-react';
import { cn } from '@/lib/utils';

interface FulfillmentOrder {
  id: string;
  orderNumber: string;
  status: 'pending' | 'printing' | 'quality_check' | 'shipping' | 'delivered';
  items: {
    name: string;
    quantity: number;
  }[];
  customer: string;
  destination: string;
  estimatedDelivery: string;
  trackingNumber?: string;
}

const mockFulfillmentOrders: FulfillmentOrder[] = [
  {
    id: '1',
    orderNumber: 'ORD-2024-001',
    status: 'delivered',
    items: [{ name: 'Custom T-Shirt', quantity: 2 }],
    customer: 'John Doe',
    destination: 'New York, NY',
    estimatedDelivery: '2024-01-18',
    trackingNumber: '1Z999AA10123456784',
  },
  {
    id: '2',
    orderNumber: 'ORD-2024-002',
    status: 'shipping',
    items: [{ name: 'Premium Hoodie', quantity: 1 }],
    customer: 'Jane Smith',
    destination: 'Los Angeles, CA',
    estimatedDelivery: '2024-01-20',
    trackingNumber: '1Z999AA10123456785',
  },
  {
    id: '3',
    orderNumber: 'ORD-2024-003',
    status: 'quality_check',
    items: [
      { name: 'Ceramic Mug', quantity: 3 },
      { name: 'Canvas Poster', quantity: 1 },
    ],
    customer: 'Mike Johnson',
    destination: 'Chicago, IL',
    estimatedDelivery: '2024-01-22',
  },
  {
    id: '4',
    orderNumber: 'ORD-2024-004',
    status: 'printing',
    items: [{ name: 'Phone Case', quantity: 1 }],
    customer: 'Sarah Williams',
    destination: 'Miami, FL',
    estimatedDelivery: '2024-01-23',
  },
  {
    id: '5',
    orderNumber: 'ORD-2024-005',
    status: 'pending',
    items: [{ name: 'Dad Hat', quantity: 2 }],
    customer: 'Chris Brown',
    destination: 'Seattle, WA',
    estimatedDelivery: '2024-01-25',
  },
];

const statusConfig = {
  pending: {
    label: 'Pending',
    color: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    icon: Clock,
  },
  printing: {
    label: 'Printing',
    color: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    icon: Package,
  },
  quality_check: {
    label: 'Quality Check',
    color: 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400',
    icon: AlertCircle,
  },
  shipping: {
    label: 'Shipping',
    color: 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-400',
    icon: Truck,
  },
  delivered: {
    label: 'Delivered',
    color: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    icon: CheckCircle,
  },
};

export default function Fulfillment() {
  const [statusFilter, setStatusFilter] = useState<string>('all');

  const filteredOrders = mockFulfillmentOrders.filter(
    (order) => statusFilter === 'all' || order.status === statusFilter
  );

  const statusCounts = {
    all: mockFulfillmentOrders.length,
    pending: mockFulfillmentOrders.filter((o) => o.status === 'pending').length,
    printing: mockFulfillmentOrders.filter((o) => o.status === 'printing').length,
    quality_check: mockFulfillmentOrders.filter((o) => o.status === 'quality_check').length,
    shipping: mockFulfillmentOrders.filter((o) => o.status === 'shipping').length,
    delivered: mockFulfillmentOrders.filter((o) => o.status === 'delivered').length,
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">Fulfillment</h1>
        <p className="text-muted-foreground">
          Track order fulfillment and shipping status
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 lg:grid-cols-5 gap-4">
        {Object.entries(statusConfig).map(([key, config]) => {
          const Icon = config.icon;
          return (
            <div
              key={key}
              className={cn(
                'p-4 rounded-xl border border-border cursor-pointer transition-all',
                statusFilter === key
                  ? 'bg-primary/10 border-primary'
                  : 'bg-card hover:bg-muted/50'
              )}
              onClick={() => setStatusFilter(key)}
            >
              <div className="flex items-center gap-3">
                <div
                  className={cn(
                    'w-10 h-10 rounded-lg flex items-center justify-center',
                    config.color
                  )}
                >
                  <Icon className="w-5 h-5" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">{config.label}</p>
                  <p className="text-xl font-bold">
                    {statusCounts[key as keyof typeof statusCounts]}
                  </p>
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Filter Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2">
        <button
          onClick={() => setStatusFilter('all')}
          className={cn(
            'px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
            statusFilter === 'all'
              ? 'bg-primary text-primary-foreground'
              : 'bg-muted hover:bg-muted/80'
          )}
        >
          All Orders ({statusCounts.all})
        </button>
      </div>

      {/* Orders List */}
      <div className="space-y-4">
        {filteredOrders.map((order) => {
          const config = statusConfig[order.status];
          const StatusIcon = config.icon;

          return (
            <div
              key={order.id}
              className="bg-card border border-border rounded-xl p-4 hover:shadow-md transition-shadow"
            >
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                {/* Order Info */}
                <div className="flex items-start gap-4">
                  <div
                    className={cn(
                      'w-12 h-12 rounded-lg flex items-center justify-center',
                      config.color
                    )}
                  >
                    <StatusIcon className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="font-semibold">{order.orderNumber}</p>
                    <p className="text-sm text-muted-foreground">
                      {order.customer}
                    </p>
                    <div className="flex items-center gap-1 mt-1 text-sm text-muted-foreground">
                      <MapPin className="w-3 h-3" />
                      {order.destination}
                    </div>
                  </div>
                </div>

                {/* Items */}
                <div className="flex-1 lg:text-center">
                  {order.items.map((item, i) => (
                    <p key={i} className="text-sm">
                      {item.quantity}x {item.name}
                    </p>
                  ))}
                </div>

                {/* Status & Tracking */}
                <div className="lg:text-right">
                  <span
                    className={cn(
                      'inline-block px-3 py-1 text-sm rounded-full',
                      config.color
                    )}
                  >
                    {config.label}
                  </span>
                  {order.trackingNumber && (
                    <p className="text-sm text-muted-foreground mt-2">
                      Tracking: {order.trackingNumber}
                    </p>
                  )}
                  <p className="text-sm text-muted-foreground mt-1">
                    Est. Delivery: {order.estimatedDelivery}
                  </p>
                </div>
              </div>

              {/* Progress Bar */}
              <div className="mt-4 pt-4 border-t border-border">
                <div className="flex items-center gap-2">
                  {Object.entries(statusConfig).map(([key]) => {
                    const statusOrder = ['pending', 'printing', 'quality_check', 'shipping', 'delivered'];
                    const currentIndex = statusOrder.indexOf(order.status);
                    const stepIndex = statusOrder.indexOf(key);
                    const isCompleted = stepIndex <= currentIndex;

                    return (
                      <div key={key} className="flex-1 flex items-center">
                        <div
                          className={cn(
                            'w-6 h-6 rounded-full flex items-center justify-center text-xs font-medium',
                            isCompleted
                              ? 'bg-primary text-primary-foreground'
                              : 'bg-muted text-muted-foreground'
                          )}
                        >
                          {stepIndex + 1}
                        </div>
                        {stepIndex < 4 && (
                          <div
                            className={cn(
                              'flex-1 h-1 mx-2',
                              stepIndex < currentIndex ? 'bg-primary' : 'bg-muted'
                            )}
                          />
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {filteredOrders.length === 0 && (
        <div className="text-center py-12">
          <Package className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
          <p className="text-muted-foreground">No orders found</p>
        </div>
      )}
    </div>
  );
}

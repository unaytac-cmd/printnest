import * as Dialog from '@radix-ui/react-dialog';
import { Button } from '@/components/ui';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui';
import { OrderStatusBadge, mapApiStatusToBadgeStatus } from './OrderStatusBadge';
import { formatCurrency, formatDate } from '@/lib/utils';
import type { Order, OrderItem, Address } from '@/types';
import {
  XIcon,
  UserIcon,
  MapPinIcon,
  TruckIcon,
  PackageIcon,
  PrinterIcon,
  EditIcon,
  XCircleIcon,
} from 'lucide-react';

interface OrderDetailModalProps {
  order: Order | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onEdit?: (order: Order) => void;
  onCancel?: (order: Order) => void;
  onPrintLabel?: (order: Order) => void;
}

function AddressDisplay({ address, title }: { address: Address; title: string }) {
  return (
    <div>
      <h4 className="mb-2 text-sm font-medium text-muted-foreground">{title}</h4>
      <div className="text-sm">
        <p className="font-medium">
          {address.firstName} {address.lastName}
        </p>
        {address.company && <p>{address.company}</p>}
        <p>{address.address1}</p>
        {address.address2 && <p>{address.address2}</p>}
        <p>
          {address.city}, {address.state} {address.postalCode}
        </p>
        <p>{address.country}</p>
        {address.phone && <p className="mt-1">{address.phone}</p>}
      </div>
    </div>
  );
}

function OrderItemRow({ item }: { item: OrderItem }) {
  const imageUrl = item.product.images?.[0] || '/placeholder-product.png';

  return (
    <div className="flex gap-4 py-3 border-b last:border-b-0">
      <div className="h-16 w-16 flex-shrink-0 overflow-hidden rounded-md border bg-muted">
        <img
          src={imageUrl}
          alt={item.product.name}
          className="h-full w-full object-cover"
        />
      </div>
      <div className="flex-1 min-w-0">
        <h4 className="font-medium truncate">{item.product.name}</h4>
        {item.variant && (
          <p className="text-sm text-muted-foreground">
            {Object.entries(item.variant.options || {})
              .map(([key, value]) => `${key}: ${value}`)
              .join(', ')}
          </p>
        )}
        <p className="text-sm text-muted-foreground">
          Qty: {item.quantity} x {formatCurrency(item.price)}
        </p>
      </div>
      <div className="text-right">
        <p className="font-medium">{formatCurrency(item.total)}</p>
      </div>
    </div>
  );
}

export function OrderDetailModal({
  order,
  open,
  onOpenChange,
  onEdit,
  onCancel,
  onPrintLabel,
}: OrderDetailModalProps) {
  if (!order) return null;

  const canEdit = order.status !== 'cancelled' && order.status !== 'completed';
  const canCancel = order.status !== 'cancelled' && order.status !== 'completed';

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <Dialog.Content className="fixed right-0 top-0 z-50 h-full w-full max-w-2xl overflow-y-auto border-l bg-background shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right sm:max-w-xl">
          {/* Header */}
          <div className="sticky top-0 z-10 flex items-center justify-between border-b bg-background p-4">
            <div className="flex items-center gap-4">
              <Dialog.Title className="text-lg font-semibold">
                Order #{order.orderNumber}
              </Dialog.Title>
              <OrderStatusBadge
                status={mapApiStatusToBadgeStatus(
                  order.status,
                  order.paymentStatus
                )}
              />
            </div>
            <Dialog.Close asChild>
              <Button variant="ghost" size="icon">
                <XIcon className="h-5 w-5" />
              </Button>
            </Dialog.Close>
          </div>

          {/* Content */}
          <div className="space-y-6 p-4">
            {/* Order Info */}
            <div className="flex flex-wrap gap-4 text-sm">
              <div>
                <span className="text-muted-foreground">Date:</span>{' '}
                <span className="font-medium">{formatDate(order.createdAt)}</span>
              </div>
              <div>
                <span className="text-muted-foreground">Payment:</span>{' '}
                <span className="font-medium capitalize">
                  {order.paymentStatus}
                </span>
              </div>
              <div>
                <span className="text-muted-foreground">Fulfillment:</span>{' '}
                <span className="font-medium capitalize">
                  {order.fulfillmentStatus?.replace('_', ' ')}
                </span>
              </div>
            </div>

            {/* Customer Info */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <UserIcon className="h-4 w-4" />
                  Customer
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="flex items-start gap-4">
                  <div>
                    <p className="font-medium">
                      {order.customer.firstName} {order.customer.lastName}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {order.customer.email}
                    </p>
                    {order.customer.phone && (
                      <p className="text-sm text-muted-foreground">
                        {order.customer.phone}
                      </p>
                    )}
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Addresses */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <MapPinIcon className="h-4 w-4" />
                  Addresses
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="grid gap-6 sm:grid-cols-2">
                  <AddressDisplay
                    address={order.shippingAddress}
                    title="Shipping Address"
                  />
                  <AddressDisplay
                    address={order.billingAddress}
                    title="Billing Address"
                  />
                </div>
              </CardContent>
            </Card>

            {/* Order Items */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <PackageIcon className="h-4 w-4" />
                  Order Items ({order.items.length})
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="divide-y">
                  {order.items.map((item) => (
                    <OrderItemRow key={item.id} item={item} />
                  ))}
                </div>

                {/* Order Totals */}
                <div className="mt-4 space-y-2 border-t pt-4">
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Subtotal</span>
                    <span>{formatCurrency(order.subtotal)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Shipping</span>
                    <span>{formatCurrency(order.shipping)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-muted-foreground">Tax</span>
                    <span>{formatCurrency(order.tax)}</span>
                  </div>
                  <div className="flex justify-between font-medium">
                    <span>Total</span>
                    <span>{formatCurrency(order.total)}</span>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Shipping Info */}
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="flex items-center gap-2 text-base">
                  <TruckIcon className="h-4 w-4" />
                  Shipping
                </CardTitle>
              </CardHeader>
              <CardContent>
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Method</span>
                    <span>Standard Shipping</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Tracking</span>
                    <span className="text-primary">
                      {/* Placeholder for tracking number */}
                      Not available yet
                    </span>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Notes */}
            {order.notes && (
              <Card>
                <CardHeader className="pb-3">
                  <CardTitle className="text-base">Notes</CardTitle>
                </CardHeader>
                <CardContent>
                  <p className="text-sm text-muted-foreground">{order.notes}</p>
                </CardContent>
              </Card>
            )}
          </div>

          {/* Footer Actions */}
          <div className="sticky bottom-0 flex items-center justify-end gap-3 border-t bg-background p-4">
            {canCancel && onCancel && (
              <Button
                variant="outline"
                onClick={() => onCancel(order)}
                className="text-destructive hover:bg-destructive hover:text-destructive-foreground"
              >
                <XCircleIcon className="mr-2 h-4 w-4" />
                Cancel Order
              </Button>
            )}
            {onPrintLabel && (
              <Button variant="outline" onClick={() => onPrintLabel(order)}>
                <PrinterIcon className="mr-2 h-4 w-4" />
                Print Label
              </Button>
            )}
            {canEdit && onEdit && (
              <Button onClick={() => onEdit(order)}>
                <EditIcon className="mr-2 h-4 w-4" />
                Edit Order
              </Button>
            )}
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

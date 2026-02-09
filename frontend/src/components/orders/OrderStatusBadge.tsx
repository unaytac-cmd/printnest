import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

// Extended order status type for this component
export type OrderStatusType =
  | 'draft'
  | 'payment_pending'
  | 'pending'
  | 'in_production'
  | 'shipped'
  | 'cancelled';

const orderStatusBadgeVariants = cva(
  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      status: {
        draft: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
        payment_pending:
          'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-500',
        pending:
          'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-500',
        in_production:
          'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-500',
        shipped:
          'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-500',
        cancelled:
          'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-500',
      },
    },
    defaultVariants: {
      status: 'pending',
    },
  }
);

const statusLabels: Record<OrderStatusType, string> = {
  draft: 'Draft',
  payment_pending: 'Payment Pending',
  pending: 'Pending',
  in_production: 'In Production',
  shipped: 'Shipped',
  cancelled: 'Cancelled',
};

interface OrderStatusBadgeProps
  extends VariantProps<typeof orderStatusBadgeVariants> {
  status: OrderStatusType;
  className?: string;
}

export function OrderStatusBadge({
  status,
  className,
}: OrderStatusBadgeProps) {
  return (
    <span className={cn(orderStatusBadgeVariants({ status }), className)}>
      {statusLabels[status]}
    </span>
  );
}

// Helper to convert API status to badge status
export function mapApiStatusToBadgeStatus(
  orderStatus: string,
  paymentStatus?: string
): OrderStatusType {
  if (orderStatus === 'cancelled') return 'cancelled';
  if (orderStatus === 'pending' && paymentStatus === 'pending') return 'payment_pending';
  if (orderStatus === 'pending') return 'pending';
  if (orderStatus === 'processing') return 'in_production';
  if (orderStatus === 'completed') return 'shipped';
  if (orderStatus === 'confirmed') return 'pending';
  return 'draft';
}

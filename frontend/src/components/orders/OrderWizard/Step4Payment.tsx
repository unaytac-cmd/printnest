import { useFormContext } from 'react-hook-form';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui';
import { formatCurrency, cn } from '@/lib/utils';
import type { OrderWizardData } from './types';
import { CreditCardIcon, FileTextIcon, BanknoteIcon } from 'lucide-react';

interface PaymentMethod {
  id: 'card' | 'invoice' | 'cod';
  name: string;
  description: string;
  icon: React.ReactNode;
}

const paymentMethods: PaymentMethod[] = [
  {
    id: 'card',
    name: 'Credit Card',
    description: 'Pay securely with your credit or debit card',
    icon: <CreditCardIcon className="h-5 w-5" />,
  },
  {
    id: 'invoice',
    name: 'Invoice',
    description: 'Pay later via invoice (Net 30)',
    icon: <FileTextIcon className="h-5 w-5" />,
  },
  {
    id: 'cod',
    name: 'Cash on Delivery',
    description: 'Pay when you receive the order',
    icon: <BanknoteIcon className="h-5 w-5" />,
  },
];

const shippingPrices: Record<string, number> = {
  standard: 5.99,
  express: 12.99,
  overnight: 24.99,
};

export function Step4Payment() {
  const {
    register,
    watch,
    formState: { errors },
  } = useFormContext<OrderWizardData>();

  const items = watch('items');
  const shippingMethod = watch('shippingMethod');
  const selectedPaymentMethod = watch('paymentMethod');

  // Calculate totals
  const subtotal = items.reduce((sum, item) => {
    return sum + (item.price || 0) * (item.quantity || 0);
  }, 0);

  const shippingCost = shippingPrices[shippingMethod] || 0;
  const taxRate = 0.0825; // 8.25% example tax rate
  const tax = subtotal * taxRate;
  const total = subtotal + shippingCost + tax;

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Payment & Confirmation</h2>
        <p className="text-sm text-muted-foreground">
          Review your order and select a payment method.
        </p>
      </div>

      {/* Order Summary */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Order Summary</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {items.map((item, index) => (
              <div key={index} className="flex justify-between text-sm">
                <span>
                  Product #{index + 1} x {item.quantity}
                </span>
                <span>{formatCurrency((item.price || 0) * (item.quantity || 0))}</span>
              </div>
            ))}
            <div className="border-t pt-3">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Subtotal</span>
                <span>{formatCurrency(subtotal)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Shipping</span>
                <span>{formatCurrency(shippingCost)}</span>
              </div>
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Tax (8.25%)</span>
                <span>{formatCurrency(tax)}</span>
              </div>
            </div>
            <div className="border-t pt-3">
              <div className="flex justify-between font-semibold">
                <span>Total</span>
                <span className="text-lg">{formatCurrency(total)}</span>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Payment Method Selection */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Payment Method</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {paymentMethods.map((method) => (
              <label
                key={method.id}
                className={cn(
                  'flex cursor-pointer items-start gap-4 rounded-lg border p-4 transition-colors hover:bg-muted/50',
                  selectedPaymentMethod === method.id && 'border-primary bg-primary/5'
                )}
              >
                <input
                  type="radio"
                  value={method.id}
                  {...register('paymentMethod')}
                  className="mt-1 h-4 w-4 border-primary text-primary focus:ring-primary"
                />
                <div className="flex items-center gap-3">
                  <div className="rounded-full bg-muted p-2">{method.icon}</div>
                  <div>
                    <p className="font-medium">{method.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {method.description}
                    </p>
                  </div>
                </div>
              </label>
            ))}
          </div>
          {errors.paymentMethod && (
            <p className="mt-2 text-sm text-destructive">
              {errors.paymentMethod.message}
            </p>
          )}
        </CardContent>
      </Card>

      {/* Order Notes */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Order Notes (Optional)</CardTitle>
        </CardHeader>
        <CardContent>
          <textarea
            {...register('notes')}
            placeholder="Add any special instructions or notes for this order..."
            className="min-h-[100px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          />
        </CardContent>
      </Card>

      {/* Terms */}
      <div className="rounded-lg border bg-muted/30 p-4">
        <p className="text-sm text-muted-foreground">
          By placing this order, you agree to our Terms of Service and Privacy Policy.
          Orders are processed within 1-2 business days.
        </p>
      </div>
    </div>
  );
}

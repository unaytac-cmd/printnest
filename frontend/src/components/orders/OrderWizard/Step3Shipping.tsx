import { useFormContext } from 'react-hook-form';
import { Input, Card, CardContent, CardHeader, CardTitle } from '@/components/ui';
import * as Checkbox from '@radix-ui/react-checkbox';
import { CheckIcon } from 'lucide-react';
import type { ShippingData } from './types';
import { cn } from '@/lib/utils';

interface ShippingMethod {
  id: string;
  name: string;
  description: string;
  price: number;
  estimatedDays: string;
}

const shippingMethods: ShippingMethod[] = [
  {
    id: 'standard',
    name: 'Standard Shipping',
    description: 'Delivered via USPS',
    price: 5.99,
    estimatedDays: '5-7 business days',
  },
  {
    id: 'express',
    name: 'Express Shipping',
    description: 'Delivered via UPS',
    price: 12.99,
    estimatedDays: '2-3 business days',
  },
  {
    id: 'overnight',
    name: 'Overnight Shipping',
    description: 'Delivered via FedEx',
    price: 24.99,
    estimatedDays: '1 business day',
  },
];

interface AddressFormProps {
  prefix: 'shippingAddress' | 'billingAddress';
  disabled?: boolean;
}

function AddressForm({ prefix, disabled = false }: AddressFormProps) {
  const {
    register,
    formState: { errors },
  } = useFormContext<ShippingData>();

  const getError = (field: string) => {
    const fieldErrors = errors[prefix] as Record<string, { message?: string }> | undefined;
    return fieldErrors?.[field]?.message;
  };

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <div>
        <label className="text-sm font-medium">First Name</label>
        <Input
          {...register(`${prefix}.firstName`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('firstName') && (
          <p className="mt-1 text-sm text-destructive">{getError('firstName')}</p>
        )}
      </div>
      <div>
        <label className="text-sm font-medium">Last Name</label>
        <Input
          {...register(`${prefix}.lastName`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('lastName') && (
          <p className="mt-1 text-sm text-destructive">{getError('lastName')}</p>
        )}
      </div>
      <div className="sm:col-span-2">
        <label className="text-sm font-medium">Company (Optional)</label>
        <Input
          {...register(`${prefix}.company`)}
          disabled={disabled}
          className="mt-1"
        />
      </div>
      <div className="sm:col-span-2">
        <label className="text-sm font-medium">Address</label>
        <Input
          {...register(`${prefix}.address1`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('address1') && (
          <p className="mt-1 text-sm text-destructive">{getError('address1')}</p>
        )}
      </div>
      <div className="sm:col-span-2">
        <label className="text-sm font-medium">Apartment, Suite, etc. (Optional)</label>
        <Input
          {...register(`${prefix}.address2`)}
          disabled={disabled}
          className="mt-1"
        />
      </div>
      <div>
        <label className="text-sm font-medium">City</label>
        <Input
          {...register(`${prefix}.city`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('city') && (
          <p className="mt-1 text-sm text-destructive">{getError('city')}</p>
        )}
      </div>
      <div>
        <label className="text-sm font-medium">State / Province</label>
        <Input
          {...register(`${prefix}.state`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('state') && (
          <p className="mt-1 text-sm text-destructive">{getError('state')}</p>
        )}
      </div>
      <div>
        <label className="text-sm font-medium">Postal Code</label>
        <Input
          {...register(`${prefix}.postalCode`)}
          disabled={disabled}
          className="mt-1"
        />
        {getError('postalCode') && (
          <p className="mt-1 text-sm text-destructive">{getError('postalCode')}</p>
        )}
      </div>
      <div>
        <label className="text-sm font-medium">Country</label>
        <Input
          {...register(`${prefix}.country`)}
          disabled={disabled}
          className="mt-1"
          defaultValue="United States"
        />
        {getError('country') && (
          <p className="mt-1 text-sm text-destructive">{getError('country')}</p>
        )}
      </div>
      <div className="sm:col-span-2">
        <label className="text-sm font-medium">Phone (Optional)</label>
        <Input
          type="tel"
          {...register(`${prefix}.phone`)}
          disabled={disabled}
          className="mt-1"
        />
      </div>
    </div>
  );
}

export function Step3Shipping() {
  const {
    register,
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<ShippingData>();

  const sameAsBilling = watch('sameAsBilling');
  const selectedMethod = watch('shippingMethod');

  const handleSameAsBillingChange = (checked: boolean) => {
    setValue('sameAsBilling', checked);
    if (checked) {
      // Copy billing address to shipping address
      const billingAddress = watch('billingAddress');
      setValue('shippingAddress', billingAddress);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Shipping Information</h2>
        <p className="text-sm text-muted-foreground">
          Enter the shipping and billing addresses for this order.
        </p>
      </div>

      {/* Billing Address */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Billing Address</CardTitle>
        </CardHeader>
        <CardContent>
          <AddressForm prefix="billingAddress" />
        </CardContent>
      </Card>

      {/* Same as Billing Checkbox */}
      <div className="flex items-center gap-2">
        <Checkbox.Root
          id="sameAsBilling"
          checked={sameAsBilling}
          onCheckedChange={(checked) => handleSameAsBillingChange(!!checked)}
          className="flex h-5 w-5 items-center justify-center rounded border border-primary data-[state=checked]:bg-primary data-[state=checked]:text-primary-foreground"
        >
          <Checkbox.Indicator>
            <CheckIcon className="h-4 w-4" />
          </Checkbox.Indicator>
        </Checkbox.Root>
        <label htmlFor="sameAsBilling" className="text-sm font-medium cursor-pointer">
          Shipping address is the same as billing address
        </label>
      </div>

      {/* Shipping Address */}
      {!sameAsBilling && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Shipping Address</CardTitle>
          </CardHeader>
          <CardContent>
            <AddressForm prefix="shippingAddress" />
          </CardContent>
        </Card>
      )}

      {/* Shipping Method */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Shipping Method</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {shippingMethods.map((method) => (
              <label
                key={method.id}
                className={cn(
                  'flex cursor-pointer items-center justify-between rounded-lg border p-4 transition-colors hover:bg-muted/50',
                  selectedMethod === method.id && 'border-primary bg-primary/5'
                )}
              >
                <div className="flex items-center gap-3">
                  <input
                    type="radio"
                    value={method.id}
                    {...register('shippingMethod')}
                    className="h-4 w-4 border-primary text-primary focus:ring-primary"
                  />
                  <div>
                    <p className="font-medium">{method.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {method.description} - {method.estimatedDays}
                    </p>
                  </div>
                </div>
                <span className="font-medium">${method.price.toFixed(2)}</span>
              </label>
            ))}
          </div>
          {errors.shippingMethod && (
            <p className="mt-2 text-sm text-destructive">
              {errors.shippingMethod.message}
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

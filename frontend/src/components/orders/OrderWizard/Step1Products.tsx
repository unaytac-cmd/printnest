import { useFieldArray, useFormContext } from 'react-hook-form';
import { Button, Input, Card, CardContent } from '@/components/ui';
import { formatCurrency } from '@/lib/utils';
import type { Product, ProductVariant } from '@/types';
import type { ProductSelectionData } from './types';
import { PlusIcon, TrashIcon, MinusIcon } from 'lucide-react';
import * as Select from '@radix-ui/react-select';
import { ChevronDownIcon, CheckIcon } from 'lucide-react';

interface Step1ProductsProps {
  products: Product[];
  loading?: boolean;
}

export function Step1Products({ products, loading = false }: Step1ProductsProps) {
  const {
    control,
    register,
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<ProductSelectionData>();

  const { fields, append, remove } = useFieldArray({
    control,
    name: 'items',
  });

  const watchedItems = watch('items');

  const handleAddProduct = () => {
    append({
      productId: '',
      variantId: undefined,
      quantity: 1,
      price: 0,
    });
  };

  const handleProductChange = (index: number, productId: string) => {
    const product = products.find((p) => p.id === productId);
    if (product) {
      setValue(`items.${index}.productId`, productId);
      setValue(`items.${index}.price`, product.price);
      setValue(`items.${index}.variantId`, undefined);
    }
  };

  const handleVariantChange = (index: number, variantId: string) => {
    const item = watchedItems[index];
    const product = products.find((p) => p.id === item.productId);
    const variant = product?.variants.find((v) => v.id === variantId);
    if (variant) {
      setValue(`items.${index}.variantId`, variantId);
      setValue(`items.${index}.price`, variant.price);
    }
  };

  const handleQuantityChange = (index: number, delta: number) => {
    const currentQty = watchedItems[index]?.quantity || 1;
    const newQty = Math.max(1, currentQty + delta);
    setValue(`items.${index}.quantity`, newQty);
  };

  const getProductVariants = (productId: string): ProductVariant[] => {
    const product = products.find((p) => p.id === productId);
    return product?.variants || [];
  };

  const calculateTotal = () => {
    return watchedItems.reduce((sum, item) => {
      return sum + (item.price || 0) * (item.quantity || 0);
    }, 0);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Select Products</h2>
        <p className="text-sm text-muted-foreground">
          Add products to your order with their variants and quantities.
        </p>
      </div>

      {errors.items?.root && (
        <p className="text-sm text-destructive">{errors.items.root.message}</p>
      )}

      <div className="space-y-4">
        {fields.map((field, index) => {
          const item = watchedItems[index];
          const variants = getProductVariants(item?.productId || '');
          const product = products.find((p) => p.id === item?.productId);

          return (
            <Card key={field.id}>
              <CardContent className="p-4">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start">
                  {/* Product Image */}
                  <div className="h-20 w-20 flex-shrink-0 overflow-hidden rounded-md border bg-muted">
                    {product?.images[0] ? (
                      <img
                        src={product.images[0]}
                        alt={product.name}
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <div className="flex h-full items-center justify-center text-muted-foreground">
                        No image
                      </div>
                    )}
                  </div>

                  <div className="flex-1 space-y-3">
                    {/* Product Select */}
                    <div>
                      <Select.Root
                        value={item?.productId || ''}
                        onValueChange={(value) => handleProductChange(index, value)}
                      >
                        <Select.Trigger className="inline-flex h-10 w-full items-center justify-between gap-2 rounded-md border border-input bg-background px-3 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
                          <Select.Value placeholder="Select a product" />
                          <Select.Icon>
                            <ChevronDownIcon className="h-4 w-4 opacity-50" />
                          </Select.Icon>
                        </Select.Trigger>
                        <Select.Portal>
                          <Select.Content className="z-50 max-h-[300px] overflow-hidden rounded-md border bg-popover shadow-md">
                            <Select.Viewport className="p-1">
                              {products.map((product) => (
                                <Select.Item
                                  key={product.id}
                                  value={product.id}
                                  className="relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-accent focus:bg-accent"
                                >
                                  <Select.ItemIndicator className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                                    <CheckIcon className="h-4 w-4" />
                                  </Select.ItemIndicator>
                                  <Select.ItemText>
                                    {product.name} - {formatCurrency(product.price)}
                                  </Select.ItemText>
                                </Select.Item>
                              ))}
                            </Select.Viewport>
                          </Select.Content>
                        </Select.Portal>
                      </Select.Root>
                      {errors.items?.[index]?.productId && (
                        <p className="mt-1 text-sm text-destructive">
                          {errors.items[index].productId?.message}
                        </p>
                      )}
                    </div>

                    {/* Variant Select */}
                    {variants.length > 0 && (
                      <div>
                        <Select.Root
                          value={item?.variantId || ''}
                          onValueChange={(value) => handleVariantChange(index, value)}
                        >
                          <Select.Trigger className="inline-flex h-10 w-full items-center justify-between gap-2 rounded-md border border-input bg-background px-3 text-sm ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2">
                            <Select.Value placeholder="Select variant (optional)" />
                            <Select.Icon>
                              <ChevronDownIcon className="h-4 w-4 opacity-50" />
                            </Select.Icon>
                          </Select.Trigger>
                          <Select.Portal>
                            <Select.Content className="z-50 max-h-[300px] overflow-hidden rounded-md border bg-popover shadow-md">
                              <Select.Viewport className="p-1">
                                {variants.map((variant) => (
                                  <Select.Item
                                    key={variant.id}
                                    value={variant.id}
                                    className="relative flex cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none hover:bg-accent focus:bg-accent"
                                  >
                                    <Select.ItemIndicator className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
                                      <CheckIcon className="h-4 w-4" />
                                    </Select.ItemIndicator>
                                    <Select.ItemText>
                                      {variant.name} - {formatCurrency(variant.price)}
                                    </Select.ItemText>
                                  </Select.Item>
                                ))}
                              </Select.Viewport>
                            </Select.Content>
                          </Select.Portal>
                        </Select.Root>
                      </div>
                    )}

                    {/* Quantity */}
                    <div className="flex items-center gap-3">
                      <span className="text-sm text-muted-foreground">Quantity:</span>
                      <div className="flex items-center gap-2">
                        <Button
                          type="button"
                          variant="outline"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => handleQuantityChange(index, -1)}
                          disabled={(item?.quantity || 1) <= 1}
                        >
                          <MinusIcon className="h-4 w-4" />
                        </Button>
                        <Input
                          type="number"
                          min={1}
                          {...register(`items.${index}.quantity`, {
                            valueAsNumber: true,
                          })}
                          className="h-8 w-16 text-center"
                        />
                        <Button
                          type="button"
                          variant="outline"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => handleQuantityChange(index, 1)}
                        >
                          <PlusIcon className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  </div>

                  {/* Price and Remove */}
                  <div className="flex items-start gap-4">
                    <div className="text-right">
                      <p className="text-lg font-semibold">
                        {formatCurrency((item?.price || 0) * (item?.quantity || 0))}
                      </p>
                      <p className="text-sm text-muted-foreground">
                        {formatCurrency(item?.price || 0)} each
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="text-destructive hover:bg-destructive/10"
                      onClick={() => remove(index)}
                      disabled={fields.length === 1}
                    >
                      <TrashIcon className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <Button
        type="button"
        variant="outline"
        onClick={handleAddProduct}
        className="w-full"
      >
        <PlusIcon className="mr-2 h-4 w-4" />
        Add Another Product
      </Button>

      {/* Order Summary */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center justify-between">
            <span className="text-lg font-medium">Subtotal</span>
            <span className="text-xl font-bold">{formatCurrency(calculateTotal())}</span>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

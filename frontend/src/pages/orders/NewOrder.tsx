import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Plus, Trash2, Upload, Loader2, Check } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useProductSelectionData, useImportOrdersFromExcel, useCreateOrder } from '@/api/hooks';
import type {
  ProductForSelection,
  Option1ForSelection,
  Option2ForSelection,
  VariantForSelection,
  ModificationForSelection,
} from '@/types';

interface OrderProductItem {
  id: string;
  categoryId: number | null;
  productId: number | null;
  option1Id: number | null;
  option2Id: number | null;
  variantId: number | null;
  quantity: number;
  modifications: { modificationId: number; designUrl: string }[];
}

interface ShippingAddress {
  name: string;
  street1: string;
  street2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export default function NewOrder() {
  const navigate = useNavigate();
  const { data: selectionData, isLoading: isLoadingData } = useProductSelectionData();
  const createOrder = useCreateOrder();
  const importExcel = useImportOrdersFromExcel();

  const [products, setProducts] = useState<OrderProductItem[]>([
    {
      id: '1',
      categoryId: null,
      productId: null,
      option1Id: null,
      option2Id: null,
      variantId: null,
      quantity: 1,
      modifications: [],
    },
  ]);

  const [address, setAddress] = useState<ShippingAddress>({
    name: '',
    street1: '',
    street2: '',
    city: '',
    state: '',
    postalCode: '',
    country: 'US',
  });

  const [showExcelImport, setShowExcelImport] = useState(false);

  // Get filtered options based on selections
  const getFilteredProducts = (categoryId: number | null): ProductForSelection[] => {
    if (!selectionData || !categoryId) return [];
    return selectionData.products.filter((p) => p.categoryId === categoryId);
  };

  const getOption1s = (productId: number | null): Option1ForSelection[] => {
    if (!selectionData || !productId) return [];
    return selectionData.option1s.filter((o) => o.productId === productId);
  };

  const getOption2s = (productId: number | null): Option2ForSelection[] => {
    if (!selectionData || !productId) return [];
    return selectionData.option2s.filter((o) => o.productId === productId);
  };

  const getVariant = (
    productId: number | null,
    option1Id: number | null,
    option2Id: number | null
  ): VariantForSelection | undefined => {
    if (!selectionData || !productId) return undefined;
    return selectionData.variants.find(
      (v) =>
        v.productId === productId &&
        (v.option1Id === option1Id || (!v.option1Id && !option1Id)) &&
        (v.option2Id === option2Id || (!v.option2Id && !option2Id))
    );
  };

  const getModifications = (categoryId: number | null): ModificationForSelection[] => {
    if (!selectionData || !categoryId) return [];
    return selectionData.modifications.filter((m) => m.categoryId === categoryId);
  };

  const updateProduct = (id: string, updates: Partial<OrderProductItem>) => {
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id !== id) return p;
        const updated = { ...p, ...updates };

        // Reset dependent fields when parent changes
        if ('categoryId' in updates) {
          updated.productId = null;
          updated.option1Id = null;
          updated.option2Id = null;
          updated.variantId = null;
          updated.modifications = [];
        }
        if ('productId' in updates) {
          updated.option1Id = null;
          updated.option2Id = null;
          updated.variantId = null;
        }
        if ('option1Id' in updates || 'option2Id' in updates) {
          const variant = getVariant(updated.productId, updated.option1Id, updated.option2Id);
          updated.variantId = variant?.id || null;
        }

        return updated;
      })
    );
  };

  const addProduct = () => {
    setProducts((prev) => [
      ...prev,
      {
        id: Date.now().toString(),
        categoryId: null,
        productId: null,
        option1Id: null,
        option2Id: null,
        variantId: null,
        quantity: 1,
        modifications: [],
      },
    ]);
  };

  const removeProduct = (id: string) => {
    setProducts((prev) => prev.filter((p) => p.id !== id));
  };

  const addModification = (productId: string, modificationId: number) => {
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id !== productId) return p;
        if (p.modifications.some((m) => m.modificationId === modificationId)) return p;
        return {
          ...p,
          modifications: [...p.modifications, { modificationId, designUrl: '' }],
        };
      })
    );
  };

  const updateModificationUrl = (productId: string, modificationId: number, designUrl: string) => {
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id !== productId) return p;
        return {
          ...p,
          modifications: p.modifications.map((m) =>
            m.modificationId === modificationId ? { ...m, designUrl } : m
          ),
        };
      })
    );
  };

  const removeModification = (productId: string, modificationId: number) => {
    setProducts((prev) =>
      prev.map((p) => {
        if (p.id !== productId) return p;
        return {
          ...p,
          modifications: p.modifications.filter((m) => m.modificationId !== modificationId),
        };
      })
    );
  };

  const calculateTotal = useMemo(() => {
    if (!selectionData) return 0;

    return products.reduce((total, product) => {
      const variant = selectionData.variants.find((v) => v.id === product.variantId);
      const variantPrice = variant?.price || 0;

      const modificationPrices = product.modifications.reduce((mTotal, mod) => {
        const modification = selectionData.modifications.find((m) => m.id === mod.modificationId);
        return mTotal + (modification?.priceDifference || 0);
      }, 0);

      return total + (variantPrice + modificationPrices) * product.quantity;
    }, 0);
  }, [products, selectionData]);

  const handleSubmit = async () => {
    // Validate
    if (!address.name || !address.street1 || !address.city || !address.state || !address.postalCode) {
      alert('Please fill in all required address fields');
      return;
    }

    const validProducts = products.filter((p) => p.variantId);
    if (validProducts.length === 0) {
      alert('Please add at least one product with a valid variant');
      return;
    }

    try {
      await createOrder.mutateAsync({
        customer: {
          firstName: address.name.split(' ')[0] || address.name,
          lastName: address.name.split(' ').slice(1).join(' ') || '',
          email: '', // Will be filled later
        },
        items: validProducts.map((p) => ({
          productId: String(p.productId),
          variantId: p.variantId ? String(p.variantId) : undefined,
          quantity: p.quantity,
          price: selectionData?.variants.find((v) => v.id === p.variantId)?.price || 0,
        })),
        shippingAddress: {
          firstName: address.name.split(' ')[0] || address.name,
          lastName: address.name.split(' ').slice(1).join(' ') || '',
          address1: address.street1,
          address2: address.street2,
          city: address.city,
          state: address.state,
          postalCode: address.postalCode,
          country: address.country,
        },
        shippingMethod: 'standard',
        paymentMethod: 'manual',
      });
      navigate('/orders');
    } catch {
      alert('Failed to create order');
    }
  };

  const handleExcelImport = async (file: File) => {
    try {
      const result = await importExcel.mutateAsync({ file });
      if (result.success) {
        alert(`Successfully imported ${result.ordersCreated} orders`);
        navigate('/orders');
      } else {
        alert(`Import completed with errors: ${result.message}`);
      }
    } catch {
      alert('Failed to import Excel file');
    }
  };

  if (isLoadingData) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center gap-4">
        <button
          onClick={() => navigate('/orders')}
          className="p-2 hover:bg-muted rounded-lg transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <div>
          <h1 className="text-2xl font-bold">New Order</h1>
          <p className="text-muted-foreground">Create a manual order</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Products Section */}
        <div className="lg:col-span-2 space-y-4">
          <div className="bg-card border border-border rounded-xl p-6">
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-lg font-semibold">Products</h2>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowExcelImport(!showExcelImport)}
                  className="inline-flex items-center gap-2 px-3 py-1.5 text-sm border border-border rounded-lg hover:bg-muted transition-colors"
                >
                  <Upload className="w-4 h-4" />
                  Import Excel
                </button>
                <button
                  onClick={addProduct}
                  className="inline-flex items-center gap-2 px-3 py-1.5 text-sm bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
                >
                  <Plus className="w-4 h-4" />
                  Add Product
                </button>
              </div>
            </div>

            {/* Excel Import Section */}
            {showExcelImport && (
              <div className="mb-6 p-4 border border-dashed border-border rounded-lg">
                <input
                  type="file"
                  accept=".xlsx,.xls"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) handleExcelImport(file);
                  }}
                  className="block w-full text-sm text-muted-foreground
                    file:mr-4 file:py-2 file:px-4
                    file:rounded-lg file:border-0
                    file:text-sm file:font-medium
                    file:bg-primary file:text-primary-foreground
                    hover:file:bg-primary/90"
                />
                <p className="mt-2 text-xs text-muted-foreground">
                  Excel format: name, street1, city, state, zip, country, product, category, option_1, option_2, quantity, modification_1, modification_1_url
                </p>
              </div>
            )}

            {/* Products List */}
            <div className="space-y-4">
              {products.map((product, index) => (
                <div
                  key={product.id}
                  className="p-4 border border-border rounded-lg space-y-3"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-medium">Product {index + 1}</span>
                    {products.length > 1 && (
                      <button
                        onClick={() => removeProduct(product.id)}
                        className="p-1 text-destructive hover:bg-destructive/10 rounded transition-colors"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    {/* Category */}
                    <div>
                      <label className="text-sm text-muted-foreground">Category</label>
                      <select
                        value={product.categoryId || ''}
                        onChange={(e) =>
                          updateProduct(product.id, {
                            categoryId: e.target.value ? Number(e.target.value) : null,
                          })
                        }
                        className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                      >
                        <option value="">Select category</option>
                        {selectionData?.categories.map((cat) => (
                          <option key={cat.id} value={cat.id}>
                            {cat.name}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Product */}
                    <div>
                      <label className="text-sm text-muted-foreground">Product</label>
                      <select
                        value={product.productId || ''}
                        onChange={(e) =>
                          updateProduct(product.id, {
                            productId: e.target.value ? Number(e.target.value) : null,
                          })
                        }
                        disabled={!product.categoryId}
                        className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring disabled:opacity-50"
                      >
                        <option value="">Select product</option>
                        {getFilteredProducts(product.categoryId).map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.title}
                          </option>
                        ))}
                      </select>
                    </div>

                    {/* Option 1 */}
                    {getOption1s(product.productId).length > 0 && (
                      <div>
                        <label className="text-sm text-muted-foreground">Size</label>
                        <select
                          value={product.option1Id || ''}
                          onChange={(e) =>
                            updateProduct(product.id, {
                              option1Id: e.target.value ? Number(e.target.value) : null,
                            })
                          }
                          className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                        >
                          <option value="">Select size</option>
                          {getOption1s(product.productId).map((o) => (
                            <option key={o.id} value={o.id}>
                              {o.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* Option 2 */}
                    {getOption2s(product.productId).length > 0 && (
                      <div>
                        <label className="text-sm text-muted-foreground">Color</label>
                        <select
                          value={product.option2Id || ''}
                          onChange={(e) =>
                            updateProduct(product.id, {
                              option2Id: e.target.value ? Number(e.target.value) : null,
                            })
                          }
                          className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                        >
                          <option value="">Select color</option>
                          {getOption2s(product.productId).map((o) => (
                            <option key={o.id} value={o.id}>
                              {o.name} {o.isDark && '(Dark)'}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}

                    {/* Quantity */}
                    <div>
                      <label className="text-sm text-muted-foreground">Quantity</label>
                      <input
                        type="number"
                        min="1"
                        value={product.quantity}
                        onChange={(e) =>
                          updateProduct(product.id, {
                            quantity: Math.max(1, parseInt(e.target.value) || 1),
                          })
                        }
                        className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                      />
                    </div>
                  </div>

                  {/* Modifications */}
                  {product.categoryId && getModifications(product.categoryId).length > 0 && (
                    <div className="pt-2 border-t border-border">
                      <label className="text-sm text-muted-foreground">Print Locations</label>
                      <div className="mt-2 flex flex-wrap gap-2">
                        {getModifications(product.categoryId).map((mod) => {
                          const isSelected = product.modifications.some(
                            (m) => m.modificationId === mod.id
                          );
                          return (
                            <button
                              key={mod.id}
                              onClick={() =>
                                isSelected
                                  ? removeModification(product.id, mod.id)
                                  : addModification(product.id, mod.id)
                              }
                              className={cn(
                                'px-3 py-1 text-sm rounded-full border transition-colors',
                                isSelected
                                  ? 'bg-primary text-primary-foreground border-primary'
                                  : 'border-border hover:bg-muted'
                              )}
                            >
                              {mod.name}
                              {mod.priceDifference > 0 && ` (+$${mod.priceDifference})`}
                              {isSelected && <Check className="w-3 h-3 ml-1 inline" />}
                            </button>
                          );
                        })}
                      </div>

                      {/* Design URLs for selected modifications */}
                      {product.modifications.length > 0 && (
                        <div className="mt-3 space-y-2">
                          {product.modifications.map((mod) => {
                            const modification = selectionData?.modifications.find(
                              (m) => m.id === mod.modificationId
                            );
                            return (
                              <div key={mod.modificationId} className="flex gap-2 items-center">
                                <span className="text-sm w-20">{modification?.name}:</span>
                                <input
                                  type="text"
                                  placeholder="Design URL"
                                  value={mod.designUrl}
                                  onChange={(e) =>
                                    updateModificationUrl(
                                      product.id,
                                      mod.modificationId,
                                      e.target.value
                                    )
                                  }
                                  className="flex-1 px-3 py-1.5 text-sm bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                                />
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Variant Price */}
                  {product.variantId && (
                    <div className="text-right text-sm">
                      Price: $
                      {(
                        (selectionData?.variants.find((v) => v.id === product.variantId)?.price ||
                          0) +
                        product.modifications.reduce((sum, mod) => {
                          const m = selectionData?.modifications.find(
                            (x) => x.id === mod.modificationId
                          );
                          return sum + (m?.priceDifference || 0);
                        }, 0)
                      ).toFixed(2)}{' '}
                      x {product.quantity}
                    </div>
                  )}
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Sidebar - Address & Summary */}
        <div className="space-y-4">
          {/* Shipping Address */}
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold mb-4">Shipping Address</h2>
            <div className="space-y-3">
              <div>
                <label className="text-sm text-muted-foreground">Customer Name *</label>
                <input
                  type="text"
                  value={address.name}
                  onChange={(e) => setAddress({ ...address, name: e.target.value })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="John Doe"
                />
              </div>
              <div>
                <label className="text-sm text-muted-foreground">Street Address *</label>
                <input
                  type="text"
                  value={address.street1}
                  onChange={(e) => setAddress({ ...address, street1: e.target.value })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="123 Main St"
                />
              </div>
              <div>
                <label className="text-sm text-muted-foreground">Apt, Suite, etc.</label>
                <input
                  type="text"
                  value={address.street2}
                  onChange={(e) => setAddress({ ...address, street2: e.target.value })}
                  className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  placeholder="Apt 4B"
                />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-sm text-muted-foreground">City *</label>
                  <input
                    type="text"
                    value={address.city}
                    onChange={(e) => setAddress({ ...address, city: e.target.value })}
                    className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
                <div>
                  <label className="text-sm text-muted-foreground">State *</label>
                  <input
                    type="text"
                    value={address.state}
                    onChange={(e) => setAddress({ ...address, state: e.target.value })}
                    className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-sm text-muted-foreground">ZIP Code *</label>
                  <input
                    type="text"
                    value={address.postalCode}
                    onChange={(e) => setAddress({ ...address, postalCode: e.target.value })}
                    className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
                <div>
                  <label className="text-sm text-muted-foreground">Country</label>
                  <select
                    value={address.country}
                    onChange={(e) => setAddress({ ...address, country: e.target.value })}
                    className="w-full mt-1 px-3 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
                  >
                    <option value="US">United States</option>
                    <option value="CA">Canada</option>
                  </select>
                </div>
              </div>
            </div>
          </div>

          {/* Order Summary */}
          <div className="bg-card border border-border rounded-xl p-6">
            <h2 className="text-lg font-semibold mb-4">Order Summary</h2>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Products ({products.length})</span>
                <span>${calculateTotal.toFixed(2)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">Shipping</span>
                <span>Calculated at checkout</span>
              </div>
              <div className="border-t border-border pt-2 mt-2">
                <div className="flex justify-between font-semibold">
                  <span>Subtotal</span>
                  <span>${calculateTotal.toFixed(2)}</span>
                </div>
              </div>
            </div>

            <button
              onClick={handleSubmit}
              disabled={createOrder.isPending}
              className="w-full mt-6 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
            >
              {createOrder.isPending ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Creating...
                </>
              ) : (
                'Create Order'
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

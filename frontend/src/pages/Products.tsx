import { useState } from 'react';
import { Routes, Route, Link } from 'react-router-dom';
import { Plus, Search, Filter, Edit, Trash2, Eye, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useProducts, useDeleteProduct } from '@/hooks/useProducts';

function ProductsList() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const { data, isLoading, error } = useProducts({
    page,
    pageSize,
    search: searchQuery || undefined,
  });
  const deleteProduct = useDeleteProduct();

  const products = data?.data || [];
  const total = data?.total || 0;
  const totalPages = data?.totalPages || 1;

  const toggleSelectAll = () => {
    if (selectedProducts.length === products.length) {
      setSelectedProducts([]);
    } else {
      setSelectedProducts(products.map((p) => p.id));
    }
  };

  const toggleSelect = (id: string) => {
    setSelectedProducts((prev) =>
      prev.includes(id) ? prev.filter((p) => p !== id) : [...prev, id]
    );
  };

  const handleDelete = (id: string) => {
    if (confirm('Are you sure you want to delete this product?')) {
      deleteProduct.mutate(id);
    }
  };

  const statusColors: Record<string, string> = {
    active: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
    draft: 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400',
    archived: 'bg-gray-100 text-gray-800 dark:bg-gray-900/30 dark:text-gray-400',
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
        <p className="text-destructive">Failed to load products</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Products</h1>
          <p className="text-muted-foreground">
            Manage your product catalog
          </p>
        </div>
        <Link
          to="/products/new"
          className="inline-flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          Add Product
        </Link>
      </div>

      {/* Filters */}
      <div className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search products..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 bg-background border border-border rounded-lg focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
        <button className="inline-flex items-center gap-2 px-4 py-2 border border-border rounded-lg hover:bg-accent transition-colors">
          <Filter className="w-4 h-4" />
          Filters
        </button>
      </div>

      {/* Products Table */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b border-border bg-muted/50">
                <th className="text-left p-4">
                  <input
                    type="checkbox"
                    checked={products.length > 0 && selectedProducts.length === products.length}
                    onChange={toggleSelectAll}
                    className="rounded border-border"
                  />
                </th>
                <th className="text-left p-4 font-medium text-sm">Product</th>
                <th className="text-left p-4 font-medium text-sm">Category</th>
                <th className="text-left p-4 font-medium text-sm">Price</th>
                <th className="text-left p-4 font-medium text-sm">Stock</th>
                <th className="text-left p-4 font-medium text-sm">Status</th>
                <th className="text-left p-4 font-medium text-sm">Actions</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr
                  key={product.id}
                  className="border-b border-border hover:bg-muted/50 transition-colors"
                >
                  <td className="p-4">
                    <input
                      type="checkbox"
                      checked={selectedProducts.includes(product.id)}
                      onChange={() => toggleSelect(product.id)}
                      className="rounded border-border"
                    />
                  </td>
                  <td className="p-4">
                    <div className="flex items-center gap-3">
                      {product.images?.[0] ? (
                        <img
                          src={product.images[0]}
                          alt={product.name}
                          className="w-12 h-12 object-cover rounded-lg"
                        />
                      ) : (
                        <div className="w-12 h-12 bg-muted rounded-lg flex items-center justify-center">
                          <span className="text-xs text-muted-foreground">IMG</span>
                        </div>
                      )}
                      <span className="font-medium">{product.name}</span>
                    </div>
                  </td>
                  <td className="p-4 text-muted-foreground">{product.category?.name || '-'}</td>
                  <td className="p-4 font-medium">${product.price.toFixed(2)}</td>
                  <td className="p-4">
                    <span
                      className={cn(
                        'font-medium',
                        product.stock === 0 && 'text-destructive'
                      )}
                    >
                      {product.stock}
                    </span>
                  </td>
                  <td className="p-4">
                    <span
                      className={cn(
                        'inline-block px-2 py-1 text-xs rounded-full capitalize',
                        statusColors[product.status] || statusColors.draft
                      )}
                    >
                      {product.status}
                    </span>
                  </td>
                  <td className="p-4">
                    <div className="flex items-center gap-2">
                      <Link
                        to={`/products/${product.id}`}
                        className="p-2 hover:bg-muted rounded-lg transition-colors"
                        title="View"
                      >
                        <Eye className="w-4 h-4" />
                      </Link>
                      <Link
                        to={`/products/${product.id}/edit`}
                        className="p-2 hover:bg-muted rounded-lg transition-colors"
                        title="Edit"
                      >
                        <Edit className="w-4 h-4" />
                      </Link>
                      <button
                        className="p-2 hover:bg-muted rounded-lg transition-colors text-destructive"
                        title="Delete"
                        onClick={() => handleDelete(product.id)}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        <div className="flex items-center justify-between p-4 border-t border-border">
          <p className="text-sm text-muted-foreground">
            Showing {products.length} of {total} products
          </p>
          <div className="flex items-center gap-2">
            <button
              className="px-3 py-1 border border-border rounded-lg hover:bg-muted transition-colors disabled:opacity-50"
              disabled={page <= 1}
              onClick={() => setPage((p) => Math.max(1, p - 1))}
            >
              Previous
            </button>
            {Array.from({ length: Math.min(5, totalPages) }, (_, i) => i + 1).map((p) => (
              <button
                key={p}
                onClick={() => setPage(p)}
                className={cn(
                  'px-3 py-1 rounded-lg',
                  p === page
                    ? 'bg-primary text-primary-foreground'
                    : 'border border-border hover:bg-muted transition-colors'
                )}
              >
                {p}
              </button>
            ))}
            <button
              className="px-3 py-1 border border-border rounded-lg hover:bg-muted transition-colors disabled:opacity-50"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
            >
              Next
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function NewProduct() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Add New Product</h1>
        <p className="text-muted-foreground">Create a new product listing</p>
      </div>
      <div className="bg-card border border-border rounded-xl p-6">
        <p className="text-muted-foreground">Product form coming soon...</p>
      </div>
    </div>
  );
}

export default function Products() {
  return (
    <Routes>
      <Route index element={<ProductsList />} />
      <Route path="new" element={<NewProduct />} />
    </Routes>
  );
}

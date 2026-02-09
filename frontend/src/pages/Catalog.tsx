import { useState } from 'react';
import { Search, Plus } from 'lucide-react';
import { cn } from '@/lib/utils';

interface CatalogProduct {
  id: string;
  name: string;
  category: string;
  basePrice: number;
  image: string;
  variants: number;
  printAreas: string[];
}

const mockCatalogProducts: CatalogProduct[] = [
  {
    id: '1',
    name: 'Unisex Heavy Cotton Tee',
    category: 'T-Shirts',
    basePrice: 8.99,
    image: '/placeholder.jpg',
    variants: 45,
    printAreas: ['Front', 'Back', 'Left Sleeve', 'Right Sleeve'],
  },
  {
    id: '2',
    name: 'Unisex Heavy Blend Hoodie',
    category: 'Hoodies',
    basePrice: 18.99,
    image: '/placeholder.jpg',
    variants: 32,
    printAreas: ['Front', 'Back'],
  },
  {
    id: '3',
    name: 'Premium Ceramic Mug',
    category: 'Drinkware',
    basePrice: 4.99,
    image: '/placeholder.jpg',
    variants: 8,
    printAreas: ['Wrap Around'],
  },
  {
    id: '4',
    name: 'iPhone Tough Case',
    category: 'Phone Cases',
    basePrice: 6.99,
    image: '/placeholder.jpg',
    variants: 24,
    printAreas: ['Back'],
  },
  {
    id: '5',
    name: 'Premium Canvas Poster',
    category: 'Wall Art',
    basePrice: 12.99,
    image: '/placeholder.jpg',
    variants: 12,
    printAreas: ['Full'],
  },
  {
    id: '6',
    name: 'Embroidered Dad Hat',
    category: 'Hats',
    basePrice: 9.99,
    image: '/placeholder.jpg',
    variants: 18,
    printAreas: ['Front', 'Side'],
  },
];

const categories = [
  'All',
  'T-Shirts',
  'Hoodies',
  'Drinkware',
  'Phone Cases',
  'Wall Art',
  'Hats',
  'Bags',
  'Accessories',
];

export default function Catalog() {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');

  const filteredProducts = mockCatalogProducts.filter((product) => {
    const matchesSearch = product.name.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesCategory =
      selectedCategory === 'All' || product.category === selectedCategory;
    return matchesSearch && matchesCategory;
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold">Product Catalog</h1>
        <p className="text-muted-foreground">
          Browse and select base products for your store
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-col lg:flex-row gap-4">
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
        <div className="flex gap-2 overflow-x-auto pb-2 lg:pb-0">
          {categories.map((category) => (
            <button
              key={category}
              onClick={() => setSelectedCategory(category)}
              className={cn(
                'px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-colors',
                selectedCategory === category
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted hover:bg-muted/80'
              )}
            >
              {category}
            </button>
          ))}
        </div>
      </div>

      {/* Products Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        {filteredProducts.map((product) => (
          <div
            key={product.id}
            className="bg-card border border-border rounded-xl overflow-hidden group hover:shadow-lg transition-shadow"
          >
            {/* Product Image */}
            <div className="aspect-square bg-muted relative">
              <div className="absolute inset-0 flex items-center justify-center text-muted-foreground">
                <span className="text-sm">Product Image</span>
              </div>
              {/* Hover Overlay */}
              <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                <button className="inline-flex items-center gap-2 px-4 py-2 bg-white text-black rounded-lg font-medium">
                  <Plus className="w-4 h-4" />
                  Add to Store
                </button>
              </div>
            </div>

            {/* Product Info */}
            <div className="p-4">
              <h3 className="font-semibold mb-1 line-clamp-1">{product.name}</h3>
              <p className="text-sm text-muted-foreground mb-2">{product.category}</p>

              <div className="flex items-center justify-between mb-3">
                <span className="text-lg font-bold">
                  ${product.basePrice.toFixed(2)}
                </span>
                <span className="text-sm text-muted-foreground">
                  {product.variants} variants
                </span>
              </div>

              {/* Print Areas */}
              <div className="flex flex-wrap gap-1">
                {product.printAreas.map((area) => (
                  <span
                    key={area}
                    className="px-2 py-0.5 text-xs bg-muted rounded-full"
                  >
                    {area}
                  </span>
                ))}
              </div>
            </div>
          </div>
        ))}
      </div>

      {filteredProducts.length === 0 && (
        <div className="text-center py-12">
          <p className="text-muted-foreground">No products found</p>
        </div>
      )}
    </div>
  );
}

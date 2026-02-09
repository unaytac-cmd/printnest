/**
 * Query keys for TanStack Query
 * Centralized key management for better cache invalidation
 */

export const queryKeys = {
  // Auth
  auth: {
    all: ['auth'] as const,
    me: () => [...queryKeys.auth.all, 'me'] as const,
  },

  // Tenant
  tenant: {
    all: ['tenant'] as const,
    bySlug: (slug: string) => [...queryKeys.tenant.all, 'slug', slug] as const,
    settings: (tenantId: string) =>
      [...queryKeys.tenant.all, tenantId, 'settings'] as const,
  },

  // Products
  products: {
    all: ['products'] as const,
    list: (filters?: object) =>
      [...queryKeys.products.all, 'list', filters] as const,
    detail: (id: string) => [...queryKeys.products.all, 'detail', id] as const,
    variants: (productId: string) =>
      [...queryKeys.products.all, productId, 'variants'] as const,
  },

  // Orders
  orders: {
    all: ['orders'] as const,
    list: (filters?: object) =>
      [...queryKeys.orders.all, 'list', filters] as const,
    detail: (id: string) => [...queryKeys.orders.all, 'detail', id] as const,
    fulfillment: (orderId: string) =>
      [...queryKeys.orders.all, orderId, 'fulfillment'] as const,
  },

  // Customers
  customers: {
    all: ['customers'] as const,
    list: (filters?: object) =>
      [...queryKeys.customers.all, 'list', filters] as const,
    detail: (id: string) =>
      [...queryKeys.customers.all, 'detail', id] as const,
    orders: (customerId: string) =>
      [...queryKeys.customers.all, customerId, 'orders'] as const,
  },

  // Analytics
  analytics: {
    all: ['analytics'] as const,
    dashboard: (period: string) =>
      [...queryKeys.analytics.all, 'dashboard', period] as const,
    revenue: (period: string) =>
      [...queryKeys.analytics.all, 'revenue', period] as const,
    topProducts: (period: string) =>
      [...queryKeys.analytics.all, 'top-products', period] as const,
  },

  // Catalog (POD products)
  catalog: {
    all: ['catalog'] as const,
    list: (filters?: object) =>
      [...queryKeys.catalog.all, 'list', filters] as const,
    detail: (id: string) => [...queryKeys.catalog.all, 'detail', id] as const,
    categories: () => [...queryKeys.catalog.all, 'categories'] as const,
  },

  // Designs
  designs: {
    all: ['designs'] as const,
    list: (filters?: object) =>
      [...queryKeys.designs.all, 'list', filters] as const,
    detail: (id: string) => [...queryKeys.designs.all, 'detail', id] as const,
  },
} as const;

// Type helper for query key factory
export type QueryKeys = typeof queryKeys;

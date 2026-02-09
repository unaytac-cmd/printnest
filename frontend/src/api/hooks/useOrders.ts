import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';
import type { ApiOrder } from '@/types';

// Backend response type
interface OrderListResponse {
  orders: ApiOrder[];
  total: number;
  page: number;
  limit: number;
  totalPages: number;
}

// Filter types - matches backend OrderFiltersExtended
export interface OrderFilters {
  page?: number;
  pageSize?: number;  // mapped to limit on backend
  status?: number;    // integer status code
  statuses?: number[];
  storeId?: number;
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

// Mutation types
interface CreateOrderData {
  customerId?: string;
  customer?: {
    firstName: string;
    lastName: string;
    email: string;
    phone?: string;
  };
  items: {
    productId: string;
    variantId?: string;
    quantity: number;
    price: number;
  }[];
  shippingAddress: {
    firstName: string;
    lastName: string;
    address1: string;
    address2?: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    phone?: string;
  };
  billingAddress?: {
    firstName: string;
    lastName: string;
    address1: string;
    address2?: string;
    city: string;
    state: string;
    postalCode: string;
    country: string;
    phone?: string;
  };
  shippingMethod: string;
  paymentMethod: string;
  notes?: string;
}

interface UpdateOrderStatusData {
  id: number;
  status: number;
}

interface BulkOrderActionData {
  orderIds: number[];
  action: 'update_status' | 'export' | 'create_gangsheet' | 'cancel';
  status?: number;
}

/**
 * Hook to fetch paginated orders list with filters
 */
export function useOrders(filters: OrderFilters = {}) {
  return useQuery({
    queryKey: queryKeys.orders.list(filters),
    queryFn: async () => {
      // Map frontend params to backend params
      const params: Record<string, unknown> = {
        page: filters.page || 1,
        limit: filters.pageSize || 20,
        sortBy: filters.sortBy || 'createdAt',
        sortOrder: filters.sortOrder?.toUpperCase() || 'DESC',
      };

      if (filters.status !== undefined) {
        params.status = filters.status;
      }
      if (filters.statuses && filters.statuses.length > 0) {
        params.statuses = filters.statuses.join(',');
      }
      if (filters.storeId) {
        params.storeId = filters.storeId;
      }
      if (filters.search) {
        params.search = filters.search;
      }

      const response = await api.get<OrderListResponse>('/orders', params);

      // Transform to standard paginated format
      return {
        data: response.data.orders,
        total: response.data.total,
        page: response.data.page,
        pageSize: response.data.limit,
        totalPages: response.data.totalPages,
      };
    },
    staleTime: 30 * 1000, // 30 seconds
  });
}

/**
 * Hook to fetch a single order by ID
 */
export function useOrder(id: string | number) {
  return useQuery({
    queryKey: queryKeys.orders.detail(String(id)),
    queryFn: async () => {
      const response = await api.get<ApiOrder>(`/orders/${id}?withProducts=true`);
      return response.data;
    },
    enabled: !!id,
  });
}

/**
 * Hook to create a new order
 */
export function useCreateOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateOrderData) => {
      const response = await api.post<ApiOrder>('/orders', data);
      return response.data;
    },
    onSuccess: (newOrder) => {
      // Add to cache
      queryClient.setQueryData(queryKeys.orders.detail(String(newOrder.id)), newOrder);
      // Invalidate list to refetch
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
      // Invalidate analytics as order counts changed
      queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all });
    },
  });
}

/**
 * Hook to update an order's status
 */
export function useUpdateOrderStatus() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, status }: UpdateOrderStatusData) => {
      const response = await api.patch<ApiOrder>(`/orders/${id}/status`, { status });
      return response.data;
    },
    onSuccess: (updatedOrder) => {
      // Update the specific order in cache
      queryClient.setQueryData(
        queryKeys.orders.detail(String(updatedOrder.id)),
        updatedOrder
      );
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
      // Invalidate analytics
      queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all });
    },
  });
}

/**
 * Hook for bulk order operations
 */
export function useBulkOrderAction() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ orderIds, action, status }: BulkOrderActionData) => {
      const response = await api.post<{ success: boolean; affected: number }>(
        '/orders/bulk',
        {
          orderIds,
          action,
          ...(status && { status }),
        }
      );
      return response.data;
    },
    onSuccess: () => {
      // Invalidate all order-related queries
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
      // Invalidate analytics
      queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all });
    },
  });
}

/**
 * Hook to cancel an order
 */
export function useCancelOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      const response = await api.post<ApiOrder>(`/orders/${id}/cancel`);
      return response.data;
    },
    onSuccess: (cancelledOrder) => {
      // Update the specific order in cache
      queryClient.setQueryData(
        queryKeys.orders.detail(String(cancelledOrder.id)),
        cancelledOrder
      );
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
      // Invalidate analytics
      queryClient.invalidateQueries({ queryKey: queryKeys.analytics.all });
    },
  });
}

/**
 * Hook to export orders
 */
export function useExportOrders() {
  return useMutation({
    mutationFn: async (orderIds: number[]) => {
      const response = await api.post<{ downloadUrl: string }>(
        '/orders/export',
        { orderIds }
      );
      return response.data;
    },
  });
}

/**
 * Hook to get order fulfillment details
 */
export function useOrderFulfillment(orderId: string | number) {
  return useQuery({
    queryKey: queryKeys.orders.fulfillment(String(orderId)),
    queryFn: async () => {
      const response = await api.get<{
        status: string;
        trackingNumber?: string;
        carrier?: string;
        estimatedDelivery?: string;
        events: Array<{
          status: string;
          timestamp: string;
          description: string;
        }>;
      }>(`/orders/${orderId}/fulfillment`);
      return response.data;
    },
    enabled: !!orderId,
  });
}

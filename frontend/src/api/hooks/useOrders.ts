import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, PaginatedResponse } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';
import type { Order, OrderStatus } from '@/types';

// Filter types
export interface OrderFilters {
  page?: number;
  pageSize?: number;
  status?: OrderStatus | OrderStatus[];
  storeId?: string;
  search?: string;
  startDate?: string;
  endDate?: string;
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
  id: string;
  status: OrderStatus;
}

interface BulkOrderActionData {
  orderIds: string[];
  action: 'update_status' | 'export' | 'create_gangsheet' | 'cancel';
  status?: OrderStatus;
}

/**
 * Hook to fetch paginated orders list with filters
 */
export function useOrders(filters: OrderFilters = {}) {
  return useQuery({
    queryKey: queryKeys.orders.list(filters),
    queryFn: async () => {
      const response = await api.get<PaginatedResponse<Order>>('/orders', {
        ...filters,
        status: Array.isArray(filters.status)
          ? filters.status.join(',')
          : filters.status,
      });
      return response.data;
    },
    staleTime: 30 * 1000, // 30 seconds
  });
}

/**
 * Hook to fetch a single order by ID
 */
export function useOrder(id: string) {
  return useQuery({
    queryKey: queryKeys.orders.detail(id),
    queryFn: async () => {
      const response = await api.get<Order>(`/orders/${id}`);
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
      const response = await api.post<Order>('/orders', data);
      return response.data;
    },
    onSuccess: (newOrder) => {
      // Add to cache
      queryClient.setQueryData(queryKeys.orders.detail(newOrder.id), newOrder);
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
      const response = await api.patch<Order>(`/orders/${id}/status`, { status });
      return response.data;
    },
    onSuccess: (updatedOrder) => {
      // Update the specific order in cache
      queryClient.setQueryData(
        queryKeys.orders.detail(updatedOrder.id),
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
    mutationFn: async (id: string) => {
      const response = await api.post<Order>(`/orders/${id}/cancel`);
      return response.data;
    },
    onSuccess: (cancelledOrder) => {
      // Update the specific order in cache
      queryClient.setQueryData(
        queryKeys.orders.detail(cancelledOrder.id),
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
    mutationFn: async (orderIds: string[]) => {
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
export function useOrderFulfillment(orderId: string) {
  return useQuery({
    queryKey: queryKeys.orders.fulfillment(orderId),
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

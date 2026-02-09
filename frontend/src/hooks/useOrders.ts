import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';
import type { Order, PaginatedResponse, OrderStatus } from '@/types';

interface OrderFilters {
  page?: number;
  pageSize?: number;
  status?: OrderStatus;
  search?: string;
  startDate?: string;
  endDate?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

interface UpdateOrderData {
  id: string;
  status?: OrderStatus;
  notes?: string;
}

/**
 * Hook to fetch paginated orders list
 */
export function useOrders(filters: OrderFilters = {}) {
  return useQuery({
    queryKey: queryKeys.orders.list(filters),
    queryFn: async () => {
      const response = await api.get<PaginatedResponse<Order>>('/orders', filters);
      return response.data;
    },
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
 * Hook to update an order's status
 */
export function useUpdateOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: UpdateOrderData) => {
      const response = await api.patch<Order>(`/orders/${id}`, data);
      return response.data;
    },
    onSuccess: (data) => {
      // Update the specific order in cache
      queryClient.setQueryData(queryKeys.orders.detail(data.id), data);
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.list() });
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
    onSuccess: (data) => {
      // Update the specific order in cache
      queryClient.setQueryData(queryKeys.orders.detail(data.id), data);
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.list() });
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
      const response = await api.get(`/orders/${orderId}/fulfillment`);
      return response.data;
    },
    enabled: !!orderId,
  });
}

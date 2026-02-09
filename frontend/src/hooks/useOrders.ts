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

interface OrderFilters {
  page?: number;
  pageSize?: number;
  status?: number;
  statuses?: number[];
  storeId?: number;
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

interface UpdateOrderData {
  id: number;
  status?: number;
  notes?: string;
}

/**
 * Hook to fetch paginated orders list
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

      // Backend returns data directly (not wrapped in { data: ... })
      // api.get returns res.data, so response IS the OrderListResponse
      const data = response as unknown as OrderListResponse;

      // Transform to standard paginated format
      return {
        data: data.orders,
        total: data.total,
        page: data.page,
        pageSize: data.limit,
        totalPages: data.totalPages,
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
 * Hook to update an order's status
 */
export function useUpdateOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: UpdateOrderData) => {
      const response = await api.patch<ApiOrder>(`/orders/${id}`, data);
      return response.data;
    },
    onSuccess: (data) => {
      // Update the specific order in cache
      queryClient.setQueryData(queryKeys.orders.detail(String(data.id)), data);
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
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
    onSuccess: (data) => {
      // Update the specific order in cache
      queryClient.setQueryData(queryKeys.orders.detail(String(data.id)), data);
      // Invalidate orders list
      queryClient.invalidateQueries({ queryKey: queryKeys.orders.all });
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
      const response = await api.get(`/orders/${orderId}/fulfillment`);
      return response.data;
    },
    enabled: !!orderId,
  });
}

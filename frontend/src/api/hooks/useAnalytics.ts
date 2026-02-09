import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';

// Types for analytics data
export interface DashboardStats {
  totalOrders: number;
  totalOrdersChange: number;
  revenue: number;
  revenueChange: number;
  pendingOrders: number;
  pendingOrdersChange: number;
  shippedToday: number;
  shippedTodayChange: number;
}

export interface RevenueDataPoint {
  date: string;
  revenue: number;
  orders: number;
}

export interface OrderStatusData {
  status: string;
  count: number;
  color: string;
}

type Period = 'daily' | 'weekly' | 'monthly';

/**
 * Hook to fetch dashboard statistics
 */
export function useDashboardStats(period: Period = 'daily') {
  return useQuery({
    queryKey: queryKeys.analytics.dashboard(period),
    queryFn: async () => {
      const response = await api.get<DashboardStats>('/analytics/dashboard', {
        period,
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
    refetchInterval: 5 * 60 * 1000, // Auto-refresh every 5 minutes
  });
}

/**
 * Hook to fetch revenue chart data
 */
export function useRevenueChart(period: Period = 'daily') {
  return useQuery({
    queryKey: queryKeys.analytics.revenue(period),
    queryFn: async () => {
      const response = await api.get<RevenueDataPoint[]>('/analytics/revenue', {
        period,
      });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch order status distribution
 */
export function useOrderStatusChart() {
  return useQuery({
    queryKey: ['analytics', 'order-status'] as const,
    queryFn: async () => {
      const response = await api.get<OrderStatusData[]>('/analytics/order-status');
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch top products
 */
export function useTopProducts(period: Period = 'daily', limit = 5) {
  return useQuery({
    queryKey: queryKeys.analytics.topProducts(period),
    queryFn: async () => {
      const response = await api.get<
        Array<{
          productId: string;
          productName: string;
          thumbnail?: string;
          totalSold: number;
          revenue: number;
        }>
      >('/analytics/top-products', { period, limit });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch customer analytics
 */
export function useCustomerAnalytics(period: Period = 'monthly') {
  return useQuery({
    queryKey: ['analytics', 'customers', period] as const,
    queryFn: async () => {
      const response = await api.get<{
        totalCustomers: number;
        newCustomers: number;
        returningCustomers: number;
        averageOrderValue: number;
        topCustomers: Array<{
          customerId: string;
          name: string;
          email: string;
          totalOrders: number;
          totalSpent: number;
        }>;
      }>('/analytics/customers', { period });
      return response.data;
    },
    staleTime: 10 * 60 * 1000, // 10 minutes
  });
}

/**
 * Hook to fetch conversion analytics
 */
export function useConversionAnalytics(period: Period = 'daily') {
  return useQuery({
    queryKey: ['analytics', 'conversion', period] as const,
    queryFn: async () => {
      const response = await api.get<{
        conversionRate: number;
        conversionRateChange: number;
        abandonmentRate: number;
        averageTimeToConvert: number;
        funnelData: Array<{
          stage: string;
          count: number;
          percentage: number;
        }>;
      }>('/analytics/conversion', { period });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Hook to fetch fulfillment analytics
 */
export function useFulfillmentAnalytics(period: Period = 'daily') {
  return useQuery({
    queryKey: ['analytics', 'fulfillment', period] as const,
    queryFn: async () => {
      const response = await api.get<{
        averageFulfillmentTime: number;
        averageFulfillmentTimeChange: number;
        onTimeDeliveryRate: number;
        fulfillmentByStatus: Array<{
          status: string;
          count: number;
        }>;
        dailyFulfillment: Array<{
          date: string;
          fulfilled: number;
          pending: number;
        }>;
      }>('/analytics/fulfillment', { period });
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
}

/**
 * Combined hook to fetch all dashboard data at once
 */
export function useDashboardData(period: Period = 'daily') {
  const statsQuery = useDashboardStats(period);
  const revenueQuery = useRevenueChart(period);
  const statusQuery = useOrderStatusChart();

  return {
    stats: statsQuery,
    revenue: revenueQuery,
    status: statusQuery,
    isLoading:
      statsQuery.isLoading || revenueQuery.isLoading || statusQuery.isLoading,
    isError:
      statsQuery.isError || revenueQuery.isError || statusQuery.isError,
    refetch: () => {
      statsQuery.refetch();
      revenueQuery.refetch();
      statusQuery.refetch();
    },
  };
}

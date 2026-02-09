import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';

export interface DashboardStats {
  totalRevenue: number;
  totalOrders: number;
  totalProducts: number;
  totalCustomers: number;
  revenueChange: number;
  ordersChange: number;
  productsChange: number;
  customersChange: number;
}

export interface RecentOrder {
  id: string;
  orderNumber: string;
  customerName: string;
  productTitle: string;
  amount: number;
  status: 'pending' | 'processing' | 'shipped' | 'delivered' | 'cancelled';
  createdAt: string;
}

export interface DashboardData {
  stats: DashboardStats;
  recentOrders: RecentOrder[];
}

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => {
      try {
        const response = await api.get<DashboardData>('/dashboard');
        return response.data;
      } catch (error) {
        // Return empty data if API fails
        return {
          stats: {
            totalRevenue: 0,
            totalOrders: 0,
            totalProducts: 0,
            totalCustomers: 0,
            revenueChange: 0,
            ordersChange: 0,
            productsChange: 0,
            customersChange: 0,
          },
          recentOrders: [],
        };
      }
    },
    staleTime: 30000, // 30 seconds
  });
}

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

export interface Gangsheet {
  id: number;
  name: string;
  status: string;
  orderIds: number[];
  settings: GangsheetSettings;
  downloadUrl?: string;
  errorMessage?: string;
  createdAt: string;
  completedAt?: string;
}

export interface GangsheetSettings {
  rollWidth: number;
  rollHeight: number;
  dpi: number;
  gap: number;
  border: boolean;
  borderSize: number;
  borderColor: string;
}

export interface CreateGangsheetRequest {
  name: string;
  orderIds: number[];
  settings?: Partial<GangsheetSettings>;
}

interface GangsheetFilters {
  page?: number;
  pageSize?: number;
  status?: string;
}

export const gangsheetKeys = {
  all: ['gangsheets'] as const,
  list: (filters?: GangsheetFilters) => [...gangsheetKeys.all, 'list', filters] as const,
  detail: (id: number) => [...gangsheetKeys.all, 'detail', id] as const,
  settings: () => [...gangsheetKeys.all, 'settings'] as const,
};

/**
 * Hook to fetch gangsheet list
 */
export function useGangsheets(filters: GangsheetFilters = {}) {
  return useQuery({
    queryKey: gangsheetKeys.list(filters),
    queryFn: async () => {
      const response = await api.get<{
        gangsheets: Gangsheet[];
        total: number;
        page: number;
        pageSize: number;
      }>('/gangsheets', filters);
      return response.data;
    },
  });
}

/**
 * Hook to fetch single gangsheet
 */
export function useGangsheet(id: number) {
  return useQuery({
    queryKey: gangsheetKeys.detail(id),
    queryFn: async () => {
      const response = await api.get<Gangsheet>(`/gangsheets/${id}`);
      return response.data;
    },
    enabled: !!id,
    refetchInterval: (query) => {
      // Poll while processing
      const data = query.state.data;
      if (data && !['Completed', 'Failed'].includes(data.status)) {
        return 3000; // Poll every 3 seconds
      }
      return false;
    },
  });
}

/**
 * Hook to fetch gangsheet settings
 */
export function useGangsheetSettings() {
  return useQuery({
    queryKey: gangsheetKeys.settings(),
    queryFn: async () => {
      const response = await api.get<GangsheetSettings>('/gangsheet-settings');
      return response.data;
    },
  });
}

/**
 * Hook to create gangsheet
 */
export function useCreateGangsheet() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateGangsheetRequest) => {
      const response = await api.post<Gangsheet>('/gangsheets', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gangsheetKeys.all });
    },
  });
}

/**
 * Hook to update gangsheet settings
 */
export function useUpdateGangsheetSettings() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (settings: Partial<GangsheetSettings>) => {
      const response = await api.put<GangsheetSettings>('/gangsheet-settings', settings);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gangsheetKeys.settings() });
    },
  });
}

/**
 * Hook to delete gangsheet
 */
export function useDeleteGangsheet() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/gangsheets/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: gangsheetKeys.all });
    },
  });
}

/**
 * Hook to get orders ready for gangsheet
 */
export function useOrdersForGangsheet() {
  return useQuery({
    queryKey: ['orders', 'for-gangsheet'],
    queryFn: async () => {
      const response = await api.get<{
        orders: Array<{
          id: number;
          orderNumber: string;
          customerName: string;
          productCount: number;
          status: string;
          createdAt: string;
        }>;
      }>('/orders', { status: 'pending', forGangsheet: true });
      return response.data;
    },
  });
}

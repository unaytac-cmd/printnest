import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

// Map Values - variant mapping
export interface MapValue {
  id: number;
  valueId1: string;
  valueId2?: string;
  variantId: number;
  variantModificationId?: number;
  isDark: boolean;
  createdAt: string;
  // Joined data
  variantName?: string;
  productName?: string;
}

// Map Listings - design mapping
export interface MapListing {
  id: number;
  listingId: string;
  modificationId: number;
  lightDesignId?: number;
  darkDesignId?: number;
  createdAt: string;
  // Joined data
  modificationName?: string;
  lightDesignUrl?: string;
  darkDesignUrl?: string;
}

export interface CreateMapValueRequest {
  valueId1: string;
  valueId2?: string;
  variantId: number;
  variantModificationId?: number;
  isDark?: boolean;
}

export interface CreateMapListingRequest {
  listingId: string;
  modificationId: number;
  lightDesignId?: number;
  darkDesignId?: number;
}

export const mappingKeys = {
  all: ['mapping'] as const,
  values: () => [...mappingKeys.all, 'values'] as const,
  listings: () => [...mappingKeys.all, 'listings'] as const,
  valueDetail: (id: number) => [...mappingKeys.values(), id] as const,
  listingDetail: (id: number) => [...mappingKeys.listings(), id] as const,
};

/**
 * Hook to fetch map values
 */
export function useMapValues() {
  return useQuery({
    queryKey: mappingKeys.values(),
    queryFn: async () => {
      const response = await api.get<{ mapValues: MapValue[] }>('/map-values');
      return response.data;
    },
  });
}

/**
 * Hook to fetch map listings
 */
export function useMapListings() {
  return useQuery({
    queryKey: mappingKeys.listings(),
    queryFn: async () => {
      const response = await api.get<{ mapListings: MapListing[] }>('/map-listings');
      return response.data;
    },
  });
}

/**
 * Hook to create map value
 */
export function useCreateMapValue() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateMapValueRequest) => {
      const response = await api.post<MapValue>('/map-values', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.values() });
    },
  });
}

/**
 * Hook to update map value
 */
export function useUpdateMapValue() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: CreateMapValueRequest & { id: number }) => {
      const response = await api.put<MapValue>(`/map-values/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.values() });
    },
  });
}

/**
 * Hook to delete map value
 */
export function useDeleteMapValue() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/map-values/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.values() });
    },
  });
}

/**
 * Hook to create map listing
 */
export function useCreateMapListing() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateMapListingRequest) => {
      const response = await api.post<MapListing>('/map-listings', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.listings() });
    },
  });
}

/**
 * Hook to update map listing
 */
export function useUpdateMapListing() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: CreateMapListingRequest & { id: number }) => {
      const response = await api.put<MapListing>(`/map-listings/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.listings() });
    },
  });
}

/**
 * Hook to delete map listing
 */
export function useDeleteMapListing() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/map-listings/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: mappingKeys.listings() });
    },
  });
}

/**
 * Hook to run mapping on an order
 */
export function useMapOrder() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (orderId: number) => {
      const response = await api.post<{ success: boolean; mappedCount: number }>(
        `/orders/${orderId}/map`
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
    },
  });
}

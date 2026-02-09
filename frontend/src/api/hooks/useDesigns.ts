import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, PaginatedResponse } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';
import type { Design } from '@/types';

// Extended design type with additional fields
export interface ExtendedDesign extends Design {
  type?: 'image' | 'embroidery' | 'vector';
  fileUrl?: string;
  fileSize?: number;
  mimeType?: string;
}

// Filter types
export interface DesignFilters {
  page?: number;
  pageSize?: number;
  type?: 'image' | 'embroidery' | 'vector';
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

// Upload response type
interface UploadDesignResponse {
  id: string;
  name: string;
  thumbnail: string;
  fileUrl: string;
  type: 'image' | 'embroidery' | 'vector';
  data: {
    width: number;
    height: number;
    elements: Array<{
      id: string;
      type: string;
      x: number;
      y: number;
      width: number;
      height: number;
      rotation: number;
      opacity: number;
      data: Record<string, unknown>;
    }>;
  };
}

/**
 * Hook to fetch paginated designs list with filters
 */
export function useDesigns(filters: DesignFilters = {}) {
  return useQuery({
    queryKey: queryKeys.designs.list(filters),
    queryFn: async () => {
      const response = await api.get<PaginatedResponse<ExtendedDesign>>(
        '/designs',
        filters
      );
      return response.data;
    },
    staleTime: 60 * 1000, // 1 minute
  });
}

/**
 * Hook to fetch a single design by ID
 */
export function useDesign(id: string) {
  return useQuery({
    queryKey: queryKeys.designs.detail(id),
    queryFn: async () => {
      const response = await api.get<ExtendedDesign>(`/designs/${id}`);
      return response.data;
    },
    enabled: !!id,
  });
}

/**
 * Hook to upload new designs
 */
export function useUploadDesign() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (files: File[]) => {
      const formData = new FormData();
      files.forEach((file) => {
        formData.append('files', file);
      });

      // Use axios directly for multipart form data
      const response = await fetch(
        `${import.meta.env.VITE_API_URL || 'http://localhost:8000/api/v1'}/designs/upload`,
        {
          method: 'POST',
          body: formData,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        throw new Error('Upload failed');
      }

      return response.json() as Promise<{ data: UploadDesignResponse[] }>;
    },
    onSuccess: () => {
      // Invalidate designs list to refetch
      queryClient.invalidateQueries({ queryKey: queryKeys.designs.all });
    },
  });
}

/**
 * Hook to update a design
 */
export function useUpdateDesign() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      id,
      data,
    }: {
      id: string;
      data: Partial<{ name: string; type: 'image' | 'embroidery' | 'vector' }>;
    }) => {
      const response = await api.patch<ExtendedDesign>(`/designs/${id}`, data);
      return response.data;
    },
    onSuccess: (updatedDesign) => {
      // Update the specific design in cache
      queryClient.setQueryData(
        queryKeys.designs.detail(updatedDesign.id),
        updatedDesign
      );
      // Invalidate designs list
      queryClient.invalidateQueries({ queryKey: queryKeys.designs.all });
    },
  });
}

/**
 * Hook to delete a design
 */
export function useDeleteDesign() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/designs/${id}`);
      return id;
    },
    onSuccess: (deletedId) => {
      // Remove from cache
      queryClient.removeQueries({ queryKey: queryKeys.designs.detail(deletedId) });
      // Invalidate designs list
      queryClient.invalidateQueries({ queryKey: queryKeys.designs.all });
    },
  });
}

/**
 * Hook to duplicate a design
 */
export function useDuplicateDesign() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      const response = await api.post<ExtendedDesign>(`/designs/${id}/duplicate`);
      return response.data;
    },
    onSuccess: (newDesign) => {
      // Add to cache
      queryClient.setQueryData(queryKeys.designs.detail(newDesign.id), newDesign);
      // Invalidate designs list
      queryClient.invalidateQueries({ queryKey: queryKeys.designs.all });
    },
  });
}

/**
 * Hook to download a design file
 */
export function useDownloadDesign() {
  return useMutation({
    mutationFn: async (id: string) => {
      const response = await api.get<{ downloadUrl: string }>(
        `/designs/${id}/download`
      );
      return response.data;
    },
    onSuccess: (data) => {
      // Trigger download
      window.open(data.downloadUrl, '_blank');
    },
  });
}

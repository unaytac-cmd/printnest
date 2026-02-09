import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';

export interface Category {
  id: number;
  name: string;
  description?: string;
  parentCategoryId?: number;
  isHeavy: boolean;
  status: number;
  createdAt: string;
  updatedAt: string;
}

export interface Modification {
  id: number;
  categoryId: number;
  name: string;
  description?: string;
  priceDifference: number;
  useWidth: number; // 1 or 2
  status: number;
  createdAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  parentCategoryId?: number;
  isHeavy?: boolean;
}

export interface CreateModificationRequest {
  name: string;
  description?: string;
  priceDifference?: number;
  useWidth?: number;
}

export const categoryKeys = {
  all: ['categories'] as const,
  list: () => [...categoryKeys.all, 'list'] as const,
  detail: (id: number) => [...categoryKeys.all, 'detail', id] as const,
  modifications: (categoryId: number) => [...categoryKeys.all, categoryId, 'modifications'] as const,
};

/**
 * Hook to fetch all categories
 */
export function useCategories() {
  return useQuery({
    queryKey: categoryKeys.list(),
    queryFn: async () => {
      const response = await api.get<{ categories: Category[] }>('/categories');
      return response.data;
    },
  });
}

/**
 * Hook to fetch single category
 */
export function useCategory(id: number) {
  return useQuery({
    queryKey: categoryKeys.detail(id),
    queryFn: async () => {
      const response = await api.get<Category>(`/categories/${id}`);
      return response.data;
    },
    enabled: !!id,
  });
}

/**
 * Hook to fetch category modifications
 */
export function useCategoryModifications(categoryId: number) {
  return useQuery({
    queryKey: categoryKeys.modifications(categoryId),
    queryFn: async () => {
      const response = await api.get<{ modifications: Modification[] }>(
        `/categories/${categoryId}/modifications`
      );
      return response.data;
    },
    enabled: !!categoryId,
  });
}

/**
 * Hook to create category
 */
export function useCreateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateCategoryRequest) => {
      const response = await api.post<Category>('/categories', data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
    },
  });
}

/**
 * Hook to update category
 */
export function useUpdateCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: CreateCategoryRequest & { id: number }) => {
      const response = await api.put<Category>(`/categories/${id}`, data);
      return response.data;
    },
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
      queryClient.invalidateQueries({ queryKey: categoryKeys.detail(variables.id) });
    },
  });
}

/**
 * Hook to delete category
 */
export function useDeleteCategory() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/categories/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.all });
    },
  });
}

/**
 * Hook to create modification
 */
export function useCreateModification(categoryId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateModificationRequest) => {
      const response = await api.post<Modification>(
        `/categories/${categoryId}/modifications`,
        data
      );
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.modifications(categoryId) });
    },
  });
}

/**
 * Hook to update modification
 */
export function useUpdateModification(categoryId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: CreateModificationRequest & { id: number }) => {
      const response = await api.put<Modification>(`/modifications/${id}`, data);
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.modifications(categoryId) });
    },
  });
}

/**
 * Hook to delete modification
 */
export function useDeleteModification(categoryId: number) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/modifications/${id}`);
      return id;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: categoryKeys.modifications(categoryId) });
    },
  });
}

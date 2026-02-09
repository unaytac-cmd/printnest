import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { queryKeys } from '@/api/queryKeys';
import type { Product, PaginatedResponse } from '@/types';

interface ProductFilters {
  page?: number;
  pageSize?: number;
  status?: string;
  category?: string;
  search?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

interface CreateProductData {
  name: string;
  description?: string;
  price: number;
  images?: string[];
  categoryId?: string;
  status?: 'active' | 'draft';
}

interface UpdateProductData extends Partial<CreateProductData> {
  id: string;
}

/**
 * Hook to fetch paginated products list
 */
export function useProducts(filters: ProductFilters = {}) {
  return useQuery({
    queryKey: queryKeys.products.list(filters),
    queryFn: async () => {
      const response = await api.get<PaginatedResponse<Product>>('/products', filters);
      return response.data;
    },
  });
}

/**
 * Hook to fetch a single product by ID
 */
export function useProduct(id: string) {
  return useQuery({
    queryKey: queryKeys.products.detail(id),
    queryFn: async () => {
      const response = await api.get<Product>(`/products/${id}`);
      return response.data;
    },
    enabled: !!id,
  });
}

/**
 * Hook to create a new product
 */
export function useCreateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (data: CreateProductData) => {
      const response = await api.post<Product>('/products', data);
      return response.data;
    },
    onSuccess: () => {
      // Invalidate products list to refetch
      queryClient.invalidateQueries({ queryKey: queryKeys.products.all });
    },
  });
}

/**
 * Hook to update an existing product
 */
export function useUpdateProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({ id, ...data }: UpdateProductData) => {
      const response = await api.put<Product>(`/products/${id}`, data);
      return response.data;
    },
    onSuccess: (data) => {
      // Update the specific product in cache
      queryClient.setQueryData(queryKeys.products.detail(data.id), data);
      // Invalidate products list
      queryClient.invalidateQueries({ queryKey: queryKeys.products.list() });
    },
  });
}

/**
 * Hook to delete a product
 */
export function useDeleteProduct() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/products/${id}`);
      return id;
    },
    onSuccess: (deletedId) => {
      // Remove from cache
      queryClient.removeQueries({ queryKey: queryKeys.products.detail(deletedId) });
      // Invalidate products list
      queryClient.invalidateQueries({ queryKey: queryKeys.products.list() });
    },
  });
}

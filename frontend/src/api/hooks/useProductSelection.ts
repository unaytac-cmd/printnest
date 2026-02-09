import { useQuery, useMutation } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { ProductSelectionData, ExcelImportResult } from '@/types';

/**
 * Hook to fetch product selection data for manual order creation
 */
export function useProductSelectionData() {
  return useQuery({
    queryKey: ['orders', 'product-selection-data'],
    queryFn: async () => {
      const response = await api.get<ProductSelectionData>('/orders/product-selection-data');
      return response.data;
    },
    staleTime: 5 * 60 * 1000, // 5 minutes - this data doesn't change often
  });
}

/**
 * Hook to import orders from Excel file
 */
export function useImportOrdersFromExcel() {
  return useMutation({
    mutationFn: async ({ file, storeId }: { file: File; storeId?: string }) => {
      const formData = new FormData();
      formData.append('file', file);

      const url = storeId
        ? `/orders/import-excel?storeId=${storeId}`
        : '/orders/import-excel';

      const response = await fetch(
        `${import.meta.env.VITE_API_URL || 'http://localhost:8000/api/v1'}${url}`,
        {
          method: 'POST',
          body: formData,
          credentials: 'include',
        }
      );

      if (!response.ok) {
        throw new Error('Failed to import Excel file');
      }

      return response.json() as Promise<ExcelImportResult>;
    },
  });
}

// Order hooks
export {
  useOrders,
  useOrder,
  useCreateOrder,
  useUpdateOrderStatus,
  useBulkOrderAction,
  useCancelOrder,
  useExportOrders,
  useOrderFulfillment,
} from './useOrders';
export type { OrderFilters } from './useOrders';

// Design hooks
export {
  useDesigns,
  useDesign,
  useUploadDesign,
  useUpdateDesign,
  useDeleteDesign,
  useDuplicateDesign,
  useDownloadDesign,
} from './useDesigns';
export type { DesignFilters, ExtendedDesign } from './useDesigns';

// Analytics hooks
export {
  useDashboardStats,
  useRevenueChart,
  useOrderStatusChart,
  useTopProducts,
  useCustomerAnalytics,
  useConversionAnalytics,
  useFulfillmentAnalytics,
  useDashboardData,
} from './useAnalytics';
export type {
  DashboardStats,
  RevenueDataPoint,
  OrderStatusData,
} from './useAnalytics';

// Product Selection hooks (for manual order creation)
export {
  useProductSelectionData,
  useImportOrdersFromExcel,
} from './useProductSelection';

// Gangsheet hooks
export {
  useGangsheets,
  useGangsheet,
  useGangsheetSettings,
  useCreateGangsheet,
  useUpdateGangsheetSettings,
  useDeleteGangsheet,
  useOrdersForGangsheet,
} from './useGangsheet';
export type { Gangsheet, GangsheetSettings, CreateGangsheetRequest } from './useGangsheet';

// Mapping hooks
export {
  useMapValues,
  useMapListings,
  useCreateMapValue,
  useUpdateMapValue,
  useDeleteMapValue,
  useCreateMapListing,
  useUpdateMapListing,
  useDeleteMapListing,
  useMapOrder,
} from './useMapping';
export type { MapValue, MapListing } from './useMapping';

// Category hooks
export {
  useCategories,
  useCategory,
  useCategoryModifications,
  useCreateCategory,
  useUpdateCategory,
  useDeleteCategory,
  useCreateModification,
  useUpdateModification,
  useDeleteModification,
} from './useCategories';
export type { Category, Modification } from './useCategories';

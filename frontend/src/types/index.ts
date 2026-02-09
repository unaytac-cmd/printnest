// Common types used across the application

// API Response types
export interface ApiResponse<T> {
  data: T;
  message?: string;
  success: boolean;
}

export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface ApiError {
  message: string;
  code?: string;
  details?: Record<string, string[]>;
}

// User types
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  avatar?: string;
  role: UserRole;
  emailVerified: boolean;
  createdAt: string;
  updatedAt: string;
}

export type UserRole = 'admin' | 'seller' | 'staff';

// Tenant types
export interface Tenant {
  id: string;
  slug: string;
  name: string;
  logo?: string;
  favicon?: string;
  primaryColor?: string;
  secondaryColor?: string;
  domain?: string;
  customDomain?: string;
  settings: TenantSettings;
  status: TenantStatus;
  createdAt: string;
  updatedAt: string;
}

export type TenantStatus = 'active' | 'suspended' | 'pending';

export interface TenantSettings {
  currency: string;
  timezone: string;
  language: string;
  taxEnabled: boolean;
  taxRate?: number;
  shippingEnabled: boolean;
  emailNotifications: boolean;
  smsNotifications: boolean;
  analyticsEnabled: boolean;
  customBranding: boolean;
}

// Product types
export interface Product {
  id: string;
  name: string;
  description: string;
  slug: string;
  images: string[];
  price: number;
  compareAtPrice?: number;
  cost: number;
  sku?: string;
  barcode?: string;
  stock: number;
  trackInventory: boolean;
  status: ProductStatus;
  category?: Category;
  variants: ProductVariant[];
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export type ProductStatus = 'active' | 'draft' | 'archived';

export interface ProductVariant {
  id: string;
  name: string;
  sku?: string;
  price: number;
  stock: number;
  options: Record<string, string>;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  parentId?: string;
}

// Order types (legacy - used by existing components)
export interface Order {
  id: string;
  orderNumber: string;
  customer: Customer;
  items: OrderItem[];
  subtotal: number;
  tax: number;
  shipping: number;
  total: number;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  fulfillmentStatus: FulfillmentStatus;
  shippingAddress: Address;
  billingAddress: Address;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export type OrderStatus = 'pending' | 'confirmed' | 'processing' | 'completed' | 'cancelled';
export type PaymentStatus = 'pending' | 'paid' | 'refunded' | 'failed';
export type FulfillmentStatus = 'pending' | 'printing' | 'quality_check' | 'shipping' | 'delivered';

export interface OrderItem {
  id: string;
  product: Product;
  variant?: ProductVariant;
  quantity: number;
  price: number;
  total: number;
}

// =====================================================
// API Order types - matches backend OrderFull
// =====================================================

export interface ApiOrder {
  id: number;
  tenantId: number;
  userId: number;
  storeId?: number;
  intOrderId?: string;
  externalOrderId?: string;
  orderType: number;
  orderStatus: number;
  orderMapStatus: number;
  orderInfo?: ApiOrderInfo;
  totalAmount: string;
  shippingAmount: string;
  taxAmount: string;
  urgentAmount?: string;
  giftNote?: string;
  customerEmail?: string;
  customerName?: string;
  shippingAddress?: Address;
  billingAddress?: Address;
  trackingNumber?: string;
  trackingUrl?: string;
  paymentMethod?: string;
  shipstationStoreId?: number;
  shipstationOrderId?: number;
  shippedAt?: string;
  createdAt: string;
  updatedAt: string;
  products?: ApiOrderProduct[];
}

export interface ApiOrderInfo {
  toAddress?: Address;
  orderNote?: string;
  giftNote?: string;
}

export interface ApiOrderProduct {
  id: number;
  orderId: number;
  productId?: number;
  variantId?: number;
  productTitle: string;
  quantity: number;
  price: string;
  designUrl?: string;
  sku?: string;
  status: number;
}

// Order status codes from backend
export const OrderStatusCodes = {
  COMBINED: -4,
  COMPLETED: -3,
  INVALID_ADDRESS: -2,
  DELETED: -1,
  NEW_ORDER: 0,
  CANCELLED: 2,
  PAYMENT_PENDING: 4,
  EDITING: 8,
  PENDING: 12,
  URGENT: 14,
  AWAITING_RESPONSE: 15,
  IN_PRODUCTION: 16,
  SHIPPED: 20,
} as const;

export type OrderStatusCode = typeof OrderStatusCodes[keyof typeof OrderStatusCodes];

// Helper to convert status code to string
export function getOrderStatusLabel(code: number): string {
  switch (code) {
    case OrderStatusCodes.COMBINED: return 'Combined';
    case OrderStatusCodes.COMPLETED: return 'Completed';
    case OrderStatusCodes.INVALID_ADDRESS: return 'Invalid Address';
    case OrderStatusCodes.DELETED: return 'Deleted';
    case OrderStatusCodes.NEW_ORDER: return 'New';
    case OrderStatusCodes.CANCELLED: return 'Cancelled';
    case OrderStatusCodes.PAYMENT_PENDING: return 'Payment Pending';
    case OrderStatusCodes.EDITING: return 'Editing';
    case OrderStatusCodes.PENDING: return 'Pending';
    case OrderStatusCodes.URGENT: return 'Urgent';
    case OrderStatusCodes.AWAITING_RESPONSE: return 'Awaiting Response';
    case OrderStatusCodes.IN_PRODUCTION: return 'In Production';
    case OrderStatusCodes.SHIPPED: return 'Shipped';
    default: return 'Unknown';
  }
}

export function getOrderStatusColor(code: number): string {
  switch (code) {
    case OrderStatusCodes.COMPLETED:
    case OrderStatusCodes.SHIPPED:
      return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
    case OrderStatusCodes.CANCELLED:
    case OrderStatusCodes.DELETED:
    case OrderStatusCodes.INVALID_ADDRESS:
      return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400';
    case OrderStatusCodes.PENDING:
    case OrderStatusCodes.URGENT:
      return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
    case OrderStatusCodes.IN_PRODUCTION:
      return 'bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400';
    default:
      return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400';
  }
}

// Customer types
export interface Customer {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phone?: string;
  addresses: Address[];
  ordersCount: number;
  totalSpent: number;
  tags: string[];
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface Address {
  id: string;
  firstName: string;
  lastName: string;
  company?: string;
  address1: string;
  address2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  phone?: string;
  isDefault: boolean;
}

// Design types
export interface Design {
  id: string;
  name: string;
  thumbnail: string;
  data: DesignData;
  createdAt: string;
  updatedAt: string;
}

export interface DesignData {
  width: number;
  height: number;
  elements: DesignElement[];
  backgroundColor?: string;
}

export interface DesignElement {
  id: string;
  type: 'image' | 'text' | 'shape';
  x: number;
  y: number;
  width: number;
  height: number;
  rotation: number;
  opacity: number;
  data: Record<string, unknown>;
}

// Product Selection Types (for manual order creation)
export interface ProductSelectionData {
  categories: CategoryForSelection[];
  products: ProductForSelection[];
  option1s: Option1ForSelection[];
  option2s: Option2ForSelection[];
  variants: VariantForSelection[];
  variantModifications: VariantModificationForSelection[];
  modifications: ModificationForSelection[];
}

export interface CategoryForSelection {
  id: number;
  name: string;
  isHeavy: boolean;
}

export interface ProductForSelection {
  id: number;
  categoryId: number;
  title: string;
  designType: number;
}

export interface Option1ForSelection {
  id: number;
  productId: number;
  name: string;
}

export interface Option2ForSelection {
  id: number;
  productId: number;
  name: string;
  isDark: boolean;
}

export interface VariantForSelection {
  id: number;
  productId: number;
  option1Id: number | null;
  option2Id: number | null;
  price: number;
  width1: number | null;
  width2: number | null;
  inStock: boolean;
  status: number;
}

export interface VariantModificationForSelection {
  id: number;
  productId: number;
  option1Id: number;
  weight: number;
  width: number;
  height: number;
  depth: number;
}

export interface ModificationForSelection {
  id: number;
  categoryId: number;
  name: string;
  priceDifference: number;
  useWidth: number;
}

// Excel Import Types
export interface ExcelImportResult {
  success: boolean;
  ordersCreated: number;
  ordersWithErrors: number;
  errors: ExcelImportError[];
  message?: string;
}

export interface ExcelImportError {
  rowNumber: number;
  customerName?: string;
  errorMessage: string;
}

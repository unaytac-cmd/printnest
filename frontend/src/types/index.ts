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

// Order types
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

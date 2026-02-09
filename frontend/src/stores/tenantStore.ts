import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// Tenant type
export interface Tenant {
  id: string;
  slug: string;
  name: string;
  ownerId?: string;
  logo?: string;
  favicon?: string;
  primaryColor?: string;
  secondaryColor?: string;
  domain?: string;
  customDomain?: string;
  settings?: TenantSettings;
  status: 'active' | 'suspended' | 'pending';
  onboardingCompleted?: boolean;
  createdAt: string;
  updatedAt: string;
}

// Tenant settings type
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

// Tenant state interface
interface TenantState {
  // State
  tenant: Tenant | null;
  isLoading: boolean;
  error: string | null;

  // Computed
  isMultiTenant: boolean;

  // Actions
  setTenant: (tenant: Tenant | null) => void;
  updateTenant: (tenantData: Partial<Tenant>) => void;
  updateSettings: (settings: Partial<TenantSettings>) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  clearTenant: () => void;
}

export const useTenantStore = create<TenantState>()(
  persist(
    (set, get) => ({
      // Initial state
      tenant: null,
      isLoading: true,
      error: null,
      isMultiTenant: true,

      // Set tenant
      setTenant: (tenant) =>
        set({
          tenant,
          isLoading: false,
          error: null,
        }),

      // Update tenant data
      updateTenant: (tenantData) => {
        const currentTenant = get().tenant;
        if (currentTenant) {
          set({
            tenant: { ...currentTenant, ...tenantData },
          });
        }
      },

      // Update tenant settings
      updateSettings: (settings) => {
        const currentTenant = get().tenant;
        if (currentTenant) {
          const currentSettings = currentTenant.settings || {
            currency: 'USD',
            timezone: 'UTC',
            language: 'en',
            taxEnabled: false,
            shippingEnabled: true,
            emailNotifications: true,
            smsNotifications: false,
            analyticsEnabled: true,
            customBranding: false,
          };
          set({
            tenant: {
              ...currentTenant,
              settings: { ...currentSettings, ...settings },
            },
          });
        }
      },

      // Set loading state
      setLoading: (loading) =>
        set({
          isLoading: loading,
        }),

      // Set error
      setError: (error) =>
        set({
          error,
          isLoading: false,
        }),

      // Clear tenant
      clearTenant: () =>
        set({
          tenant: null,
          isLoading: false,
          error: null,
        }),
    }),
    {
      name: 'printnest-tenant',
      storage: createJSONStorage(() => sessionStorage),
      partialize: (state) => ({
        tenant: state.tenant,
      }),
    }
  )
);

// Selector hooks for better performance
export const useTenant = () => useTenantStore((state) => state.tenant);
export const useTenantSettings = () => useTenantStore((state) => state.tenant?.settings);
export const useTenantLoading = () => useTenantStore((state) => state.isLoading);
export const useTenantError = () => useTenantStore((state) => state.error);

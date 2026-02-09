import { useEffect, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTenantStore, Tenant } from '@/stores/tenantStore';
import { api } from '@/api/client';
import { getSubdomain } from '@/lib/utils';

// Response type for tenant lookup
interface TenantLookupResponse {
  tenant: Tenant;
}

/**
 * Extracts tenant slug from the current URL
 * Supports both subdomain routing and path-based routing
 */
export function getTenantSlug(): string | null {
  const hostname = window.location.hostname;
  const pathname = window.location.pathname;

  // Try subdomain first
  const subdomain = getSubdomain(hostname);
  if (subdomain) {
    return subdomain;
  }

  // Fall back to path-based routing (e.g., /store/tenant-slug)
  const pathMatch = pathname.match(/^\/store\/([^/]+)/);
  if (pathMatch) {
    return pathMatch[1];
  }

  // Check for tenant in query params (useful for development)
  const urlParams = new URLSearchParams(window.location.search);
  const tenantParam = urlParams.get('tenant');
  if (tenantParam) {
    return tenantParam;
  }

  return null;
}

// Mock tenant for development
const DEV_TENANT: Tenant = {
  id: 'dev-tenant',
  slug: 'localhost',
  name: 'Development Store',
  status: 'active',
  ownerId: 'dev-user',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
};

/**
 * Hook to fetch and manage tenant based on subdomain
 */
export function useTenantFromSubdomain() {
  const { setTenant, setLoading, setError, tenant, isLoading, error } = useTenantStore();

  const slug = getTenantSlug();
  const isDevelopment = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

  const {
    data,
    isLoading: queryLoading,
    error: queryError,
    refetch,
  } = useQuery({
    queryKey: ['tenant', slug],
    queryFn: async () => {
      // In development without slug, use mock tenant
      if (isDevelopment && !slug) {
        return DEV_TENANT;
      }
      if (!slug) {
        throw new Error('No tenant slug found');
      }
      const response = await api.get<TenantLookupResponse>(`/tenants/lookup/${slug}`);
      return response.data.tenant;
    },
    enabled: isDevelopment || !!slug,
    staleTime: 5 * 60 * 1000, // Consider data fresh for 5 minutes
    retry: (failureCount, error) => {
      // Don't retry for 404 errors (tenant not found)
      if (error instanceof Error && error.message.includes('404')) {
        return false;
      }
      return failureCount < 2;
    },
  });

  // Update store when data changes
  useEffect(() => {
    if (data) {
      setTenant(data);
    }
  }, [data, setTenant]);

  // Update loading state
  useEffect(() => {
    setLoading(queryLoading);
  }, [queryLoading, setLoading]);

  // Update error state
  useEffect(() => {
    if (queryError) {
      setError(queryError instanceof Error ? queryError.message : 'Failed to load tenant');
    }
  }, [queryError, setError]);

  const refreshTenant = useCallback(() => {
    refetch();
  }, [refetch]);

  return {
    tenant,
    slug: slug || (isDevelopment ? 'localhost' : null),
    isLoading: isLoading || queryLoading,
    error,
    refreshTenant,
    isValid: isDevelopment || (!!tenant && tenant.status === 'active'),
  };
}

/**
 * Hook to check if we're in a multi-tenant context
 */
export function useIsMultiTenant(): boolean {
  const slug = getTenantSlug();
  return !!slug;
}

/**
 * Hook to get the current tenant's branding
 */
export function useTenantBranding() {
  const tenant = useTenantStore((state) => state.tenant);

  return {
    name: tenant?.name || 'Printnest',
    logo: tenant?.logo,
    favicon: tenant?.favicon,
    primaryColor: tenant?.primaryColor || '#0ea5e9',
    secondaryColor: tenant?.secondaryColor || '#075985',
  };
}

/**
 * Hook to build tenant-aware URLs
 */
export function useTenantUrl() {
  const tenant = useTenantStore((state) => state.tenant);

  const buildUrl = useCallback(
    (path: string): string => {
      // If tenant has custom domain, use it
      if (tenant?.customDomain) {
        return `https://${tenant.customDomain}${path}`;
      }

      // Use subdomain
      if (tenant?.slug) {
        const baseHost = import.meta.env.VITE_BASE_HOST || 'printnest.com';
        return `https://${tenant.slug}.${baseHost}${path}`;
      }

      // Fallback to current origin
      return `${window.location.origin}${path}`;
    },
    [tenant]
  );

  return { buildUrl };
}

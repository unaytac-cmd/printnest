import { useMutation } from '@tanstack/react-query';
import apiClient from '../client';

// =====================================================
// TYPES
// =====================================================

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  storeName: string;
  storeSlug: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface UserResponse {
  id: number;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: string;
  emailVerified: boolean;
  createdAt: string;
}

export interface TenantResponse {
  id: number;
  name: string;
  subdomain: string;
  customDomain: string | null;
  status: number;
  createdAt: string;
}

export interface AuthResponse {
  user: UserResponse;
  tenant: TenantResponse;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface TokenRefreshResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

// =====================================================
// API FUNCTIONS
// =====================================================

const authApi = {
  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/register', data);
    return response.data;
  },

  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await apiClient.post<AuthResponse>('/auth/login', data);
    return response.data;
  },

  logout: async (): Promise<void> => {
    await apiClient.post('/auth/logout');
  },

  refreshToken: async (refreshToken: string): Promise<TokenRefreshResponse> => {
    const response = await apiClient.post<TokenRefreshResponse>('/auth/refresh', {
      refreshToken,
    });
    return response.data;
  },

  checkEmail: async (email: string): Promise<{ available: boolean }> => {
    const response = await apiClient.get<{ available: boolean }>(
      `/auth/check-email?email=${encodeURIComponent(email)}`
    );
    return response.data;
  },

  checkSubdomain: async (subdomain: string): Promise<{ available: boolean }> => {
    const response = await apiClient.get<{ available: boolean }>(
      `/auth/check-subdomain?subdomain=${encodeURIComponent(subdomain)}`
    );
    return response.data;
  },

  getCurrentUser: async (): Promise<{
    userId: string;
    email: string;
    tenantId: string;
    roles: string[];
  }> => {
    const response = await apiClient.get('/auth/me');
    return response.data;
  },
};

// =====================================================
// HOOKS
// =====================================================

export function useRegister() {
  return useMutation({
    mutationFn: authApi.register,
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: authApi.login,
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: authApi.logout,
  });
}

export function useCheckEmail() {
  return useMutation({
    mutationFn: authApi.checkEmail,
  });
}

export function useCheckSubdomain() {
  return useMutation({
    mutationFn: authApi.checkSubdomain,
  });
}

export { authApi };

import { create } from 'zustand';
import { persist, createJSONStorage } from 'zustand/middleware';

// User roles
export type UserRole = 'producer' | 'subdealer' | 'owner' | 'admin' | 'employee' | 'seller' | 'staff';

// User type
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
  // Sub-dealer specific fields
  parentUserId?: string;
  assignedStoreIds?: number[];
  totalCredit?: string;
}

// Auth state interface
interface AuthState {
  // State
  user: User | null;
  token: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Actions
  setUser: (user: User | null) => void;
  setTokens: (token: string, refreshToken: string) => void;
  login: (user: User, token: string, refreshToken: string) => void;
  logout: () => void;
  updateUser: (userData: Partial<User>) => void;
  setLoading: (loading: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      // Initial state
      user: null,
      token: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: true,

      // Set user
      setUser: (user) =>
        set({
          user,
          isAuthenticated: !!user,
        }),

      // Set tokens
      setTokens: (token, refreshToken) =>
        set({
          token,
          refreshToken,
        }),

      // Login action
      login: (user, token, refreshToken) =>
        set({
          user,
          token,
          refreshToken,
          isAuthenticated: true,
          isLoading: false,
        }),

      // Logout action
      logout: () =>
        set({
          user: null,
          token: null,
          refreshToken: null,
          isAuthenticated: false,
          isLoading: false,
        }),

      // Update user data
      updateUser: (userData) => {
        const currentUser = get().user;
        if (currentUser) {
          set({
            user: { ...currentUser, ...userData },
          });
        }
      },

      // Set loading state
      setLoading: (loading) =>
        set({
          isLoading: loading,
        }),
    }),
    {
      name: 'printnest-auth',
      storage: createJSONStorage(() => localStorage),
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        refreshToken: state.refreshToken,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);

// Selector hooks for better performance
export const useUser = () => useAuthStore((state) => state.user);
export const useIsAuthenticated = () => useAuthStore((state) => state.isAuthenticated);
export const useAuthLoading = () => useAuthStore((state) => state.isLoading);
export const useUserRole = () => useAuthStore((state) => state.user?.role);

// Role check helpers
export const useIsProducer = () => {
  const role = useUserRole();
  return role === 'producer' || role === 'owner' || role === 'admin';
};

export const useIsSubdealer = () => {
  const role = useUserRole();
  return role === 'subdealer';
};

export const useAssignedStoreIds = () => useAuthStore((state) => state.user?.assignedStoreIds ?? []);

import { useEffect } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { AppShell } from '@/components/layout/AppShell';
import { useAuthStore, useIsAuthenticated, useAuthLoading } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import { useTenantFromSubdomain } from '@/hooks/useTenant';

// Lazy load pages for better performance
import { lazy, Suspense } from 'react';

// Page components (lazy loaded)
const Dashboard = lazy(() => import('@/pages/Dashboard'));
const Products = lazy(() => import('@/pages/Products'));
const Orders = lazy(() => import('@/pages/Orders'));
const Customers = lazy(() => import('@/pages/Customers'));
const Analytics = lazy(() => import('@/pages/Analytics'));
const DesignStudio = lazy(() => import('@/pages/DesignStudio'));
const Catalog = lazy(() => import('@/pages/Catalog'));
const Fulfillment = lazy(() => import('@/pages/Fulfillment'));
const Settings = lazy(() => import('@/pages/Settings'));
const Login = lazy(() => import('@/pages/Login'));
const Register = lazy(() => import('@/pages/Register'));
const NotFound = lazy(() => import('@/pages/NotFound'));
const LandingPage = lazy(() => import('@/pages/LandingPage'));
const Onboarding = lazy(() => import('@/pages/Onboarding'));

// New feature pages
const Gangsheet = lazy(() => import('@/pages/Gangsheet'));
const Mapping = lazy(() => import('@/pages/Mapping'));
const Categories = lazy(() => import('@/pages/Categories'));

// Super Admin pages
const SuperAdminLayout = lazy(() => import('@/pages/superadmin/SuperAdminLayout'));
const SuperAdminDashboard = lazy(() => import('@/pages/superadmin/SuperAdminDashboard'));
const TenantsPage = lazy(() => import('@/pages/superadmin/TenantsPage'));
const UsersPage = lazy(() => import('@/pages/superadmin/UsersPage'));
const BillingPage = lazy(() => import('@/pages/superadmin/BillingPage'));
const AnalyticsPage = lazy(() => import('@/pages/superadmin/AnalyticsPage'));

// Loading component
function PageLoader() {
  return (
    <div className="flex items-center justify-center h-full min-h-[400px]">
      <div className="flex flex-col items-center gap-4">
        <div className="w-10 h-10 border-4 border-primary border-t-transparent rounded-full animate-spin" />
        <p className="text-sm text-muted-foreground">Loading...</p>
      </div>
    </div>
  );
}

// Protected route wrapper
function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useIsAuthenticated();
  const isLoading = useAuthLoading();
  const location = useLocation();

  if (isLoading) {
    return <PageLoader />;
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <>{children}</>;
}

// Guest route wrapper (for login/register)
function GuestRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useIsAuthenticated();
  const isLoading = useAuthLoading();
  const location = useLocation();
  const tenant = useTenantStore((state) => state.tenant);

  if (isLoading) {
    return <PageLoader />;
  }

  if (isAuthenticated) {
    // Check if onboarding is completed
    if (tenant && tenant.onboardingCompleted === false) {
      return <Navigate to="/onboarding" replace />;
    }
    const from = location.state?.from?.pathname || '/dashboard';
    return <Navigate to={from} replace />;
  }

  return <>{children}</>;
}

// Tenant wrapper for multi-tenant support
function TenantWrapper({ children }: { children: React.ReactNode }) {
  const { isLoading, error, isValid } = useTenantFromSubdomain();

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-4">
          <div className="w-12 h-12 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          <p className="text-muted-foreground">Loading store...</p>
        </div>
      </div>
    );
  }

  if (error || !isValid) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="text-center p-8 max-w-md">
          <h1 className="text-3xl font-bold text-foreground mb-4">
            Store Not Found
          </h1>
          <p className="text-muted-foreground mb-6">
            {error || 'The store you are looking for does not exist or is not available.'}
          </p>
          <a
            href="https://printnest.com"
            className="inline-block px-6 py-3 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors"
          >
            Go to Printnest
          </a>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

function App() {
  const { setLoading } = useAuthStore();

  // Check authentication status on mount
  useEffect(() => {
    // Simulating auth check - in production, this would verify the token
    const checkAuth = async () => {
      try {
        // Check if we have a valid token
        const token = localStorage.getItem('printnest-auth');
        if (token) {
          // In production, validate token with backend
          // await api.get('/auth/me');
        }
      } catch (error) {
        console.error('Auth check failed:', error);
      } finally {
        setLoading(false);
      }
    };

    checkAuth();
  }, [setLoading]);

  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        {/* Landing page */}
        <Route
          path="/welcome"
          element={
            <GuestRoute>
              <LandingPage />
            </GuestRoute>
          }
        />

        {/* Onboarding */}
        <Route path="/onboarding" element={<Onboarding />} />

        {/* Auth routes (guest only) */}
        <Route
          path="/login"
          element={
            <GuestRoute>
              <Login />
            </GuestRoute>
          }
        />
        <Route
          path="/register"
          element={
            <GuestRoute>
              <Register />
            </GuestRoute>
          }
        />

        {/* Protected routes with AppShell */}
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <TenantWrapper>
                <AppShell />
              </TenantWrapper>
            </ProtectedRoute>
          }
        >
          {/* Redirect root to dashboard */}
          <Route index element={<Navigate to="/dashboard" replace />} />

          {/* Main routes */}
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="products/*" element={<Products />} />
          <Route path="orders/*" element={<Orders />} />
          <Route path="customers/*" element={<Customers />} />
          <Route path="analytics" element={<Analytics />} />

          {/* POD routes */}
          <Route path="design-studio" element={<DesignStudio />} />
          <Route path="catalog" element={<Catalog />} />
          <Route path="fulfillment" element={<Fulfillment />} />

          {/* Production routes */}
          <Route path="gangsheet/*" element={<Gangsheet />} />
          <Route path="mapping" element={<Mapping />} />
          <Route path="categories" element={<Categories />} />

          {/* Settings routes */}
          <Route path="settings/*" element={<Settings />} />
        </Route>

        {/* Super Admin routes */}
        <Route
          path="/superadmin"
          element={
            <ProtectedRoute>
              <SuperAdminLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<SuperAdminDashboard />} />
          <Route path="tenants" element={<TenantsPage />} />
          <Route path="users" element={<UsersPage />} />
          <Route path="billing" element={<BillingPage />} />
          <Route path="analytics" element={<AnalyticsPage />} />
          <Route path="settings" element={<div className="p-6"><h1 className="text-2xl font-bold">Platform Settings</h1><p className="text-muted-foreground">Coming soon...</p></div>} />
        </Route>

        {/* 404 */}
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Suspense>
  );
}

export default App;

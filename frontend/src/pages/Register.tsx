import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Eye, EyeOff, Mail, Lock, User, Store, Loader2, Check } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import { useRegister } from '@/api/hooks/useAuth';
import { cn } from '@/lib/utils';

export default function Register() {
  const [step, setStep] = useState(1);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: '',
    storeName: '',
    storeSlug: '',
    agreeToTerms: false,
  });
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const navigate = useNavigate();
  const login = useAuthStore((state) => state.login);
  const setTenant = useTenantStore((state) => state.setTenant);
  const registerMutation = useRegister();

  const updateFormData = (field: string, value: string | boolean) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (field === 'storeName') {
      // Auto-generate slug from store name
      const slug = (value as string)
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-|-$/g, '');
      setFormData((prev) => ({ ...prev, storeSlug: slug }));
    }
  };

  const passwordRequirements = [
    { label: 'At least 8 characters', met: formData.password.length >= 8 },
    { label: 'Contains uppercase letter', met: /[A-Z]/.test(formData.password) },
    { label: 'Contains lowercase letter', met: /[a-z]/.test(formData.password) },
    { label: 'Contains number', met: /[0-9]/.test(formData.password) },
  ];

  const isStep1Valid =
    formData.firstName &&
    formData.lastName &&
    formData.email &&
    formData.password &&
    formData.password === formData.confirmPassword &&
    passwordRequirements.every((req) => req.met);

  const isStep2Valid =
    formData.storeName && formData.storeSlug && formData.agreeToTerms;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (step === 1 && isStep1Valid) {
      setStep(2);
      return;
    }

    if (step === 2 && isStep2Valid) {
      setError('');
      setIsLoading(true);

      try {
        const response = await registerMutation.mutateAsync({
          firstName: formData.firstName,
          lastName: formData.lastName,
          email: formData.email,
          password: formData.password,
          storeName: formData.storeName,
          storeSlug: formData.storeSlug,
        });

        // Set user in auth store
        const user = {
          id: response.user.id.toString(),
          email: response.user.email,
          firstName: response.user.firstName || '',
          lastName: response.user.lastName || '',
          role: response.user.role as 'owner' | 'admin' | 'seller' | 'employee',
          emailVerified: response.user.emailVerified,
          createdAt: response.user.createdAt,
          updatedAt: response.user.createdAt,
        };

        login(user, response.accessToken, response.refreshToken);

        // Set tenant in tenant store
        setTenant({
          id: response.tenant.id.toString(),
          name: response.tenant.name,
          slug: response.tenant.subdomain,
          customDomain: response.tenant.customDomain || undefined,
          status: response.tenant.status === 1 ? 'active' : 'suspended',
          onboardingCompleted: false,
          createdAt: response.tenant.createdAt || new Date().toISOString(),
          updatedAt: response.tenant.createdAt || new Date().toISOString(),
        });

        // Tenant and user are already created, go directly to dashboard
        // Onboarding settings can be configured later in Settings
        navigate('/dashboard', { replace: true });
      } catch (err: unknown) {
        const error = err as { response?: { data?: { error?: string } } };
        setError(error.response?.data?.error || 'Registration failed. Please try again.');
      } finally {
        setIsLoading(false);
      }
    }
  };

  return (
    <div className="min-h-screen flex">
      {/* Left side - Form */}
      <div className="flex-1 flex items-center justify-center p-8">
        <div className="w-full max-w-md">
          {/* Logo */}
          <div className="mb-8">
            <div className="flex items-center gap-2 mb-2">
              <div className="w-10 h-10 rounded-lg bg-primary flex items-center justify-center text-white font-bold text-xl">
                P
              </div>
              <span className="text-2xl font-bold">Printnest</span>
            </div>
            <p className="text-muted-foreground">
              {step === 1
                ? 'Create your account to get started.'
                : 'Set up your store.'}
            </p>
          </div>

          {/* Progress */}
          <div className="flex items-center gap-4 mb-8">
            <div className="flex items-center gap-2">
              <div
                className={cn(
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium',
                  step >= 1
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground'
                )}
              >
                {step > 1 ? <Check className="w-4 h-4" /> : '1'}
              </div>
              <span className="text-sm font-medium">Account</span>
            </div>
            <div className="flex-1 h-px bg-border" />
            <div className="flex items-center gap-2">
              <div
                className={cn(
                  'w-8 h-8 rounded-full flex items-center justify-center text-sm font-medium',
                  step >= 2
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground'
                )}
              >
                2
              </div>
              <span className="text-sm font-medium">Store</span>
            </div>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            {error && (
              <div className="p-3 bg-destructive/10 text-destructive text-sm rounded-lg">
                {error}
              </div>
            )}

            {step === 1 && (
              <>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1">
                      First Name
                    </label>
                    <div className="relative">
                      <User className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                      <input
                        type="text"
                        value={formData.firstName}
                        onChange={(e) =>
                          updateFormData('firstName', e.target.value)
                        }
                        placeholder="John"
                        className="w-full pl-10 pr-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                        required
                      />
                    </div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">
                      Last Name
                    </label>
                    <input
                      type="text"
                      value={formData.lastName}
                      onChange={(e) =>
                        updateFormData('lastName', e.target.value)
                      }
                      placeholder="Doe"
                      className="w-full px-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">Email</label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                    <input
                      type="email"
                      value={formData.email}
                      onChange={(e) => updateFormData('email', e.target.value)}
                      placeholder="name@example.com"
                      className="w-full pl-10 pr-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={formData.password}
                      onChange={(e) =>
                        updateFormData('password', e.target.value)
                      }
                      placeholder="Create a password"
                      className="w-full pl-10 pr-12 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    >
                      {showPassword ? (
                        <EyeOff className="w-5 h-5" />
                      ) : (
                        <Eye className="w-5 h-5" />
                      )}
                    </button>
                  </div>
                  <div className="mt-2 space-y-1">
                    {passwordRequirements.map((req) => (
                      <div
                        key={req.label}
                        className={cn(
                          'flex items-center gap-2 text-xs',
                          req.met ? 'text-green-600' : 'text-muted-foreground'
                        )}
                      >
                        <Check
                          className={cn(
                            'w-3 h-3',
                            req.met ? 'opacity-100' : 'opacity-0'
                          )}
                        />
                        {req.label}
                      </div>
                    ))}
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Confirm Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                    <input
                      type="password"
                      value={formData.confirmPassword}
                      onChange={(e) =>
                        updateFormData('confirmPassword', e.target.value)
                      }
                      placeholder="Confirm your password"
                      className="w-full pl-10 pr-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                  </div>
                  {formData.confirmPassword &&
                    formData.password !== formData.confirmPassword && (
                      <p className="text-xs text-destructive mt-1">
                        Passwords do not match
                      </p>
                    )}
                </div>
              </>
            )}

            {step === 2 && (
              <>
                <div>
                  <label className="block text-sm font-medium mb-1">
                    Store Name
                  </label>
                  <div className="relative">
                    <Store className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-muted-foreground" />
                    <input
                      type="text"
                      value={formData.storeName}
                      onChange={(e) =>
                        updateFormData('storeName', e.target.value)
                      }
                      placeholder="My Awesome Store"
                      className="w-full pl-10 pr-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    Store URL
                  </label>
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground text-sm">https://</span>
                    <input
                      type="text"
                      value={formData.storeSlug}
                      onChange={(e) =>
                        updateFormData('storeSlug', e.target.value)
                      }
                      placeholder="my-store"
                      className="flex-1 px-4 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                      required
                    />
                    <span className="text-muted-foreground text-sm">
                      .printnest.com
                    </span>
                  </div>
                </div>

                <div className="pt-4">
                  <label className="flex items-start gap-3 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formData.agreeToTerms}
                      onChange={(e) =>
                        updateFormData('agreeToTerms', e.target.checked)
                      }
                      className="rounded border-border mt-1"
                      required
                    />
                    <span className="text-sm text-muted-foreground">
                      I agree to the{' '}
                      <a href="/terms" className="text-primary hover:underline">
                        Terms of Service
                      </a>{' '}
                      and{' '}
                      <a href="/privacy" className="text-primary hover:underline">
                        Privacy Policy
                      </a>
                    </span>
                  </label>
                </div>
              </>
            )}

            <div className="flex gap-4 pt-4">
              {step === 2 && (
                <button
                  type="button"
                  onClick={() => setStep(1)}
                  className="flex-1 py-2 border border-border rounded-lg font-medium hover:bg-muted transition-colors"
                >
                  Back
                </button>
              )}
              <button
                type="submit"
                disabled={
                  isLoading ||
                  (step === 1 && !isStep1Valid) ||
                  (step === 2 && !isStep2Valid)
                }
                className={cn(
                  'flex-1 py-2 bg-primary text-primary-foreground rounded-lg font-medium transition-colors',
                  isLoading ||
                    (step === 1 && !isStep1Valid) ||
                    (step === 2 && !isStep2Valid)
                    ? 'opacity-70 cursor-not-allowed'
                    : 'hover:bg-primary/90'
                )}
              >
                {isLoading ? (
                  <span className="flex items-center justify-center gap-2">
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Creating account...
                  </span>
                ) : step === 1 ? (
                  'Continue'
                ) : (
                  'Create Store'
                )}
              </button>
            </div>
          </form>

          {/* Sign in link */}
          <p className="mt-6 text-center text-sm text-muted-foreground">
            Already have an account?{' '}
            <Link to="/login" className="text-primary hover:underline">
              Sign in
            </Link>
          </p>
        </div>
      </div>

      {/* Right side - Image/Branding */}
      <div className="hidden lg:flex flex-1 bg-primary/5 items-center justify-center p-8">
        <div className="max-w-md text-center">
          <div className="w-32 h-32 mx-auto mb-8 rounded-full bg-primary/10 flex items-center justify-center">
            <div className="w-20 h-20 rounded-full bg-primary flex items-center justify-center text-white text-4xl font-bold">
              P
            </div>
          </div>
          <h2 className="text-2xl font-bold mb-4">
            Join thousands of creators
          </h2>
          <p className="text-muted-foreground">
            Start selling custom products today. No inventory, no upfront costs,
            just your creativity.
          </p>
          <div className="mt-8 flex justify-center gap-8">
            <div className="text-center">
              <p className="text-3xl font-bold text-primary">10K+</p>
              <p className="text-sm text-muted-foreground">Active Sellers</p>
            </div>
            <div className="text-center">
              <p className="text-3xl font-bold text-primary">1M+</p>
              <p className="text-sm text-muted-foreground">Products Sold</p>
            </div>
            <div className="text-center">
              <p className="text-3xl font-bold text-primary">150+</p>
              <p className="text-sm text-muted-foreground">Countries</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  CheckCircle,
  CreditCard,
  Truck,
  Store,
  Settings,
  ArrowRight,
  ArrowLeft,
  Loader2,
  Eye,
  EyeOff,
  ExternalLink,
  Globe,
  Layers,
  Package,
  Cloud,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/stores/authStore';
import { useTenantStore } from '@/stores/tenantStore';
import apiClient from '@/api/client';

interface OnboardingData {
  fullName: string;
  email: string;
}

interface ApiConfig {
  // Store
  businessName: string;
  subdomain: string;
  customDomain: string;

  // Payment
  stripeSecretKey: string;
  stripePublishableKey: string;
  stripeWebhookSecret: string;

  // ShipStation
  shipstationApiKey: string;
  shipstationApiSecret: string;

  // Shipping
  nestshipperApiKey: string;
  nestshipperClientId: string;

  // AWS S3 Storage
  awsAccessKeyId: string;
  awsSecretAccessKey: string;
  awsRegion: string;
  awsS3Bucket: string;

  // Gangsheet Settings
  gangsheetWidth: string;
  gangsheetHeight: string;
  gangsheetDpi: string;
  gangsheetSpacing: string;
  gangsheetBackgroundColor: string;
  gangsheetAutoArrange: boolean;
  gangsheetMaxDesignsPerSheet: string;
}

const steps = [
  { id: 'welcome', title: 'Welcome', icon: Store },
  { id: 'store', title: 'Store Setup', icon: Globe },
  { id: 'shipstation', title: 'ShipStation', icon: Package },
  { id: 'payment', title: 'Payment', icon: CreditCard },
  { id: 'shipping', title: 'Shipping', icon: Truck },
  { id: 'storage', title: 'Storage', icon: Cloud },
  { id: 'gangsheet', title: 'Gangsheet', icon: Layers },
  { id: 'complete', title: 'Complete', icon: Settings },
];

export default function Onboarding() {
  const [currentStep, setCurrentStep] = useState(0);
  const [onboardingData, setOnboardingData] = useState<OnboardingData | null>(null);
  const [apiConfig, setApiConfig] = useState<ApiConfig>({
    // Store
    businessName: '',
    subdomain: '',
    customDomain: '',

    // Payment
    stripeSecretKey: '',
    stripePublishableKey: '',
    stripeWebhookSecret: '',

    // ShipStation
    shipstationApiKey: '',
    shipstationApiSecret: '',

    // Shipping
    nestshipperApiKey: '',
    nestshipperClientId: '',

    // AWS S3 Storage
    awsAccessKeyId: '',
    awsSecretAccessKey: '',
    awsRegion: 'us-east-1',
    awsS3Bucket: '',

    // Gangsheet defaults
    gangsheetWidth: '22',
    gangsheetHeight: '72',
    gangsheetDpi: '300',
    gangsheetSpacing: '0.25',
    gangsheetBackgroundColor: '#FFFFFF',
    gangsheetAutoArrange: true,
    gangsheetMaxDesignsPerSheet: '50',
  });
  const [showSecrets, setShowSecrets] = useState<Record<string, boolean>>({});
  const [isLoading, setIsLoading] = useState(false);
  const navigate = useNavigate();
  const { user, login, isAuthenticated } = useAuthStore();
  const { tenant } = useTenantStore();

  useEffect(() => {
    // Check if user is authenticated (came from Register)
    if (isAuthenticated && user) {
      setOnboardingData({
        fullName: `${user.firstName} ${user.lastName}`.trim(),
        email: user.email,
      });
      // Pre-fill from tenant if available
      if (tenant) {
        setApiConfig(prev => ({
          ...prev,
          businessName: tenant.name || `${user.firstName}'s Store`,
          subdomain: tenant.slug || '',
        }));
      } else {
        // Tenant not loaded yet, use user info for defaults
        setApiConfig(prev => ({
          ...prev,
          businessName: `${user.firstName}'s Store`,
        }));
      }
    } else if (!isAuthenticated) {
      // Not authenticated - check localStorage (came from LandingPage)
      const data = localStorage.getItem('pendingOnboarding');
      if (data) {
        const parsed = JSON.parse(data);
        setOnboardingData(parsed);
        // Pre-fill business name from full name
        setApiConfig(prev => ({
          ...prev,
          businessName: parsed.fullName + "'s Store",
        }));
      } else {
        // No auth and no pending data, redirect to register
        navigate('/register');
      }
    }
    // Note: If isAuthenticated but no user yet, wait for user to load
  }, [navigate, isAuthenticated, user, tenant]);

  const toggleSecret = (key: string) => {
    setShowSecrets({ ...showSecrets, [key]: !showSecrets[key] });
  };

  const handleSubdomainChange = (value: string) => {
    const sanitized = value.toLowerCase().replace(/[^a-z0-9-]/g, '');
    setApiConfig({ ...apiConfig, subdomain: sanitized });
  };

  const handleNext = () => {
    if (currentStep < steps.length - 1) {
      setCurrentStep(currentStep + 1);
    }
  };

  // Validate current step - all required fields must be filled
  const isCurrentStepValid = (): boolean => {
    const stepId = steps[currentStep].id;
    switch (stepId) {
      case 'welcome':
        return true; // No validation needed
      case 'store':
        return !!(apiConfig.businessName.trim() && apiConfig.subdomain.trim());
      case 'shipstation':
        return !!(apiConfig.shipstationApiKey.trim() && apiConfig.shipstationApiSecret.trim());
      case 'payment':
        return !!(apiConfig.stripeSecretKey.trim() && apiConfig.stripePublishableKey.trim());
      case 'shipping':
        // NestShipper requires both API Key and Client ID
        return !!(apiConfig.nestshipperApiKey.trim() && apiConfig.nestshipperClientId.trim());
      case 'storage':
        return !!(
          apiConfig.awsAccessKeyId.trim() &&
          apiConfig.awsSecretAccessKey.trim() &&
          apiConfig.awsS3Bucket.trim()
        );
      case 'gangsheet':
        return !!(
          apiConfig.gangsheetWidth &&
          apiConfig.gangsheetHeight &&
          apiConfig.gangsheetDpi
        );
      case 'complete':
        return true;
      default:
        return true;
    }
  };

  const handleBack = () => {
    if (currentStep > 0) {
      setCurrentStep(currentStep - 1);
    }
  };

  const handleComplete = async () => {
    setIsLoading(true);

    // Build settings payload in the format expected by backend
    const settingsPayload = {
      name: apiConfig.businessName,
      customDomain: apiConfig.customDomain || null,
      shipstationSettings: {
        apiKey: apiConfig.shipstationApiKey,
        apiSecret: apiConfig.shipstationApiSecret,
      },
      stripeSettings: {
        secretKey: apiConfig.stripeSecretKey,
        publishableKey: apiConfig.stripePublishableKey,
        webhookSecret: apiConfig.stripeWebhookSecret || null,
      },
      shippingSettings: {
        nestshipperApiKey: apiConfig.nestshipperApiKey || null,
        nestshipperClientId: apiConfig.nestshipperClientId || null,
      },
      awsSettings: {
        accessKeyId: apiConfig.awsAccessKeyId,
        secretAccessKey: apiConfig.awsSecretAccessKey,
        region: apiConfig.awsRegion || 'us-east-1',
        s3Bucket: apiConfig.awsS3Bucket,
      },
      gangsheetSettings: {
        width: parseFloat(apiConfig.gangsheetWidth) || 22.0,
        height: parseFloat(apiConfig.gangsheetHeight) || 72.0,
        dpi: parseInt(apiConfig.gangsheetDpi) || 300,
        spacing: parseFloat(apiConfig.gangsheetSpacing) || 0.25,
        backgroundColor: apiConfig.gangsheetBackgroundColor || '#FFFFFF',
        autoArrange: apiConfig.gangsheetAutoArrange,
      },
    };

    try {
      if (isAuthenticated) {
        // User already registered, just update tenant settings
        await apiClient.put('/settings', settingsPayload);

        // Mark onboarding as complete in tenant store
        useTenantStore.getState().updateTenant({
          onboardingCompleted: true,
          name: apiConfig.businessName,
        });

        navigate('/dashboard');
      } else {
        // User coming from landing page, create account
        const response = await apiClient.post('/auth/onboarding/complete', {
          // User info
          fullName: onboardingData?.fullName || '',
          email: onboardingData?.email || '',
          password: localStorage.getItem('pendingOnboardingPassword') || '',
          // Store info
          businessName: apiConfig.businessName,
          subdomain: apiConfig.subdomain,
          customDomain: apiConfig.customDomain || null,
          // ShipStation
          shipstationApiKey: apiConfig.shipstationApiKey,
          shipstationApiSecret: apiConfig.shipstationApiSecret,
          // Stripe
          stripeSecretKey: apiConfig.stripeSecretKey,
          stripePublishableKey: apiConfig.stripePublishableKey,
          stripeWebhookSecret: apiConfig.stripeWebhookSecret || null,
          // Shipping
          nestshipperApiKey: apiConfig.nestshipperApiKey || null,
          nestshipperClientId: apiConfig.nestshipperClientId || null,
          // AWS S3
          awsAccessKeyId: apiConfig.awsAccessKeyId,
          awsSecretAccessKey: apiConfig.awsSecretAccessKey,
          awsRegion: apiConfig.awsRegion || 'us-east-1',
          awsS3Bucket: apiConfig.awsS3Bucket,
          // Gangsheet Settings
          gangsheetWidth: parseFloat(apiConfig.gangsheetWidth) || 22.0,
          gangsheetHeight: parseFloat(apiConfig.gangsheetHeight) || 72.0,
          gangsheetDpi: parseInt(apiConfig.gangsheetDpi) || 300,
          gangsheetSpacing: parseFloat(apiConfig.gangsheetSpacing) || 0.25,
          gangsheetBackgroundColor: apiConfig.gangsheetBackgroundColor || '#FFFFFF',
          gangsheetAutoArrange: apiConfig.gangsheetAutoArrange,
        });

        const { user: newUser, accessToken, refreshToken } = response.data;

        // Clear pending onboarding data
        localStorage.removeItem('pendingOnboarding');
        localStorage.removeItem('pendingOnboardingPassword');

        // Login with the response data
        login(
          {
            id: newUser.id.toString(),
            email: newUser.email,
            firstName: newUser.firstName || '',
            lastName: newUser.lastName || '',
            role: newUser.role,
            emailVerified: newUser.emailVerified,
            createdAt: newUser.createdAt,
            updatedAt: newUser.createdAt,
          },
          accessToken,
          refreshToken
        );

        // Mark onboarding as complete in tenant store
        useTenantStore.getState().updateTenant({ onboardingCompleted: true });

        navigate('/dashboard');
      }
    } catch (error: any) {
      console.error('Onboarding failed:', error);
      const errorMessage = error.response?.data?.error || 'Failed to complete onboarding. Please try again.';
      alert(errorMessage);
    } finally {
      setIsLoading(false);
    }
  };

  const renderStepContent = () => {
    switch (steps[currentStep].id) {
      case 'welcome':
        return (
          <div className="text-center py-8">
            <div className="w-20 h-20 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-6">
              <Store className="w-10 h-10 text-primary" />
            </div>
            <h2 className="text-2xl font-bold mb-4">
              Welcome, {onboardingData?.fullName?.split(' ')[0]}!
            </h2>
            <p className="text-muted-foreground mb-8 max-w-md mx-auto">
              Let's set up your PrintNest account. This will only take a few minutes.
            </p>
            <div className="bg-muted/50 rounded-lg p-4 max-w-sm mx-auto text-left">
              <h3 className="font-medium mb-2">What we'll configure:</h3>
              <ul className="space-y-2 text-sm text-muted-foreground">
                <li className="flex items-center gap-2">
                  <Globe className="w-4 h-4" />
                  Your store name & domain
                </li>
                <li className="flex items-center gap-2">
                  <Package className="w-4 h-4" />
                  ShipStation integration
                </li>
                <li className="flex items-center gap-2">
                  <CreditCard className="w-4 h-4" />
                  Payment processing
                </li>
                <li className="flex items-center gap-2">
                  <Truck className="w-4 h-4" />
                  Shipping labels
                </li>
                <li className="flex items-center gap-2">
                  <Cloud className="w-4 h-4" />
                  Cloud storage (AWS S3)
                </li>
                <li className="flex items-center gap-2">
                  <Layers className="w-4 h-4" />
                  Gangsheet settings
                </li>
              </ul>
            </div>
          </div>
        );

      case 'store':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">Store Setup</h2>
            <p className="text-muted-foreground mb-6">
              Configure your store name and domain.
            </p>

            <div className="space-y-6">
              {/* Business Name */}
              <div>
                <label className="block text-sm font-medium mb-1">
                  Business Name <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={apiConfig.businessName}
                  onChange={(e) => setApiConfig({ ...apiConfig, businessName: e.target.value })}
                  placeholder="My Awesome Store"
                  className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                />
              </div>

              {/* Subdomain */}
              <div>
                <label className="block text-sm font-medium mb-1">
                  Store URL <span className="text-red-500">*</span>
                </label>
                <div className="flex">
                  <input
                    type="text"
                    value={apiConfig.subdomain}
                    onChange={(e) => handleSubdomainChange(e.target.value)}
                    placeholder="mystore"
                    className="flex-1 px-4 py-2 border border-border rounded-l-lg bg-background"
                  />
                  <span className="px-4 py-2 bg-muted border border-l-0 border-border rounded-r-lg text-muted-foreground text-sm whitespace-nowrap">
                    .printnest.com
                  </span>
                </div>
                <p className="text-xs text-muted-foreground mt-1">
                  Only lowercase letters, numbers, and hyphens
                </p>
              </div>
            </div>
          </div>
        );

      case 'shipstation':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">ShipStation Integration</h2>
            <p className="text-muted-foreground mb-6">
              Connect your ShipStation account to pull orders automatically.
            </p>

            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4 mb-6">
              <p className="text-sm text-blue-800 dark:text-blue-200">
                Find your API keys in ShipStation:{' '}
                <a
                  href="https://ship.shipstation.com/settings/api"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline inline-flex items-center gap-1"
                >
                  Settings → API <ExternalLink className="w-3 h-3" />
                </a>
              </p>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">
                  API Key <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['shipstationApiKey'] ? 'text' : 'password'}
                    value={apiConfig.shipstationApiKey}
                    onChange={(e) => setApiConfig({ ...apiConfig, shipstationApiKey: e.target.value })}
                    placeholder="Enter your ShipStation API Key"
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('shipstationApiKey')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['shipstationApiKey'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  API Secret <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['shipstationApiSecret'] ? 'text' : 'password'}
                    value={apiConfig.shipstationApiSecret}
                    onChange={(e) => setApiConfig({ ...apiConfig, shipstationApiSecret: e.target.value })}
                    placeholder="Enter your ShipStation API Secret"
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('shipstationApiSecret')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['shipstationApiSecret'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>

            <div className="mt-6 p-4 bg-muted/50 rounded-lg">
              <h4 className="font-medium text-sm mb-2">How it works:</h4>
              <ul className="text-sm text-muted-foreground space-y-1">
                <li>• Orders from ShipStation are automatically synced</li>
                <li>• Create gangsheets from imported orders</li>
                <li>• Tracking numbers sync back to ShipStation</li>
              </ul>
            </div>
          </div>
        );

      case 'payment':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">Payment Gateway</h2>
            <p className="text-muted-foreground mb-6">
              Connect Stripe to process payments from your customers.
            </p>

            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4 mb-6">
              <p className="text-sm text-blue-800 dark:text-blue-200">
                Get your Stripe API keys:{' '}
                <a
                  href="https://dashboard.stripe.com/apikeys"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline inline-flex items-center gap-1"
                >
                  Stripe Dashboard → API Keys <ExternalLink className="w-3 h-3" />
                </a>
              </p>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">
                  Secret Key <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['stripeSecretKey'] ? 'text' : 'password'}
                    value={apiConfig.stripeSecretKey}
                    onChange={(e) => setApiConfig({ ...apiConfig, stripeSecretKey: e.target.value })}
                    placeholder="sk_live_..."
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('stripeSecretKey')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['stripeSecretKey'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  Publishable Key <span className="text-red-500">*</span>
                </label>
                <input
                  type="text"
                  value={apiConfig.stripePublishableKey}
                  onChange={(e) => setApiConfig({ ...apiConfig, stripePublishableKey: e.target.value })}
                  placeholder="pk_live_..."
                  className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  Webhook Secret <span className="text-muted-foreground">(optional)</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['stripeWebhookSecret'] ? 'text' : 'password'}
                    value={apiConfig.stripeWebhookSecret}
                    onChange={(e) => setApiConfig({ ...apiConfig, stripeWebhookSecret: e.target.value })}
                    placeholder="whsec_..."
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('stripeWebhookSecret')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['stripeWebhookSecret'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>
            </div>
          </div>
        );

      case 'shipping':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">Shipping Labels</h2>
            <p className="text-muted-foreground mb-6">
              Configure NestShipper for shipping label generation.
            </p>

            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4 mb-6">
              <p className="text-sm text-blue-800 dark:text-blue-200">
                Get your NestShipper credentials:{' '}
                <a
                  href="https://nestshipper.com/dashboard/settings"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline inline-flex items-center gap-1"
                >
                  NestShipper Dashboard → Settings <ExternalLink className="w-3 h-3" />
                </a>
              </p>
            </div>

            <div className="border border-border rounded-lg p-4">
              <h3 className="font-medium mb-4 flex items-center gap-2">
                <Truck className="w-4 h-4 text-primary" />
                NestShipper
              </h3>
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium mb-1">
                    Client ID <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type={showSecrets['nestshipperClientId'] ? 'text' : 'password'}
                      value={apiConfig.nestshipperClientId}
                      onChange={(e) => setApiConfig({ ...apiConfig, nestshipperClientId: e.target.value })}
                      placeholder="Enter your NestShipper Client ID"
                      className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                    />
                    <button
                      type="button"
                      onClick={() => toggleSecret('nestshipperClientId')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                    >
                      {showSecrets['nestshipperClientId'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-sm font-medium mb-1">
                    API Key <span className="text-red-500">*</span>
                  </label>
                  <div className="relative">
                    <input
                      type={showSecrets['nestshipperApiKey'] ? 'text' : 'password'}
                      value={apiConfig.nestshipperApiKey}
                      onChange={(e) => setApiConfig({ ...apiConfig, nestshipperApiKey: e.target.value })}
                      placeholder="Enter your NestShipper API Key"
                      className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                    />
                    <button
                      type="button"
                      onClick={() => toggleSecret('nestshipperApiKey')}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                    >
                      {showSecrets['nestshipperApiKey'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-6 p-4 bg-muted/50 rounded-lg">
              <h4 className="font-medium text-sm mb-2">NestShipper provides:</h4>
              <ul className="text-sm text-muted-foreground space-y-1">
                <li>• Discounted USPS, UPS, and FedEx rates</li>
                <li>• Automatic label generation</li>
                <li>• Real-time tracking updates</li>
              </ul>
            </div>
          </div>
        );

      case 'storage':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">Cloud Storage</h2>
            <p className="text-muted-foreground mb-6">
              Configure AWS S3 for storing gangsheet files and design uploads.
            </p>

            <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4 mb-6">
              <p className="text-sm text-blue-800 dark:text-blue-200">
                Get your AWS credentials:{' '}
                <a
                  href="https://console.aws.amazon.com/iam/home#/security_credentials"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="underline inline-flex items-center gap-1"
                >
                  AWS Console → Security Credentials <ExternalLink className="w-3 h-3" />
                </a>
              </p>
            </div>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">
                  Access Key ID <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['awsAccessKeyId'] ? 'text' : 'password'}
                    value={apiConfig.awsAccessKeyId}
                    onChange={(e) => setApiConfig({ ...apiConfig, awsAccessKeyId: e.target.value })}
                    placeholder="AKIA..."
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('awsAccessKeyId')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['awsAccessKeyId'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">
                  Secret Access Key <span className="text-red-500">*</span>
                </label>
                <div className="relative">
                  <input
                    type={showSecrets['awsSecretAccessKey'] ? 'text' : 'password'}
                    value={apiConfig.awsSecretAccessKey}
                    onChange={(e) => setApiConfig({ ...apiConfig, awsSecretAccessKey: e.target.value })}
                    placeholder="Enter your AWS Secret Access Key"
                    className="w-full px-4 py-2 pr-10 border border-border rounded-lg bg-background"
                  />
                  <button
                    type="button"
                    onClick={() => toggleSecret('awsSecretAccessKey')}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground"
                  >
                    {showSecrets['awsSecretAccessKey'] ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium mb-1">
                    Region <span className="text-red-500">*</span>
                  </label>
                  <select
                    value={apiConfig.awsRegion}
                    onChange={(e) => setApiConfig({ ...apiConfig, awsRegion: e.target.value })}
                    className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                  >
                    <option value="us-east-1">US East (N. Virginia)</option>
                    <option value="us-east-2">US East (Ohio)</option>
                    <option value="us-west-1">US West (N. California)</option>
                    <option value="us-west-2">US West (Oregon)</option>
                    <option value="eu-west-1">EU (Ireland)</option>
                    <option value="eu-central-1">EU (Frankfurt)</option>
                    <option value="ap-northeast-1">Asia Pacific (Tokyo)</option>
                    <option value="ap-southeast-1">Asia Pacific (Singapore)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">
                    S3 Bucket Name <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    value={apiConfig.awsS3Bucket}
                    onChange={(e) => setApiConfig({ ...apiConfig, awsS3Bucket: e.target.value })}
                    placeholder="my-printnest-bucket"
                    className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                  />
                </div>
              </div>
            </div>

            <div className="mt-6 p-4 bg-muted/50 rounded-lg">
              <h4 className="font-medium text-sm mb-2">S3 is used for:</h4>
              <ul className="text-sm text-muted-foreground space-y-1">
                <li>• Design file uploads</li>
                <li>• Gangsheet PNG storage</li>
                <li>• ZIP file downloads</li>
                <li>• Thumbnail generation</li>
              </ul>
            </div>
          </div>
        );

      case 'gangsheet':
        return (
          <div className="py-4">
            <h2 className="text-xl font-bold mb-2">Gangsheet Settings</h2>
            <p className="text-muted-foreground mb-6">
              Configure your gangsheet dimensions for DTF printing.
            </p>

            <div className="space-y-6">
              {/* Dimensions */}
              <div className="border border-border rounded-lg p-4">
                <h3 className="font-medium mb-4">Sheet Dimensions</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1">Width (inches)</label>
                    <input
                      type="number"
                      value={apiConfig.gangsheetWidth}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetWidth: e.target.value })}
                      className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                      min="1"
                      step="0.5"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Height (inches)</label>
                    <input
                      type="number"
                      value={apiConfig.gangsheetHeight}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetHeight: e.target.value })}
                      className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                      min="1"
                      step="0.5"
                    />
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 mt-3">
                  <button
                    type="button"
                    onClick={() => setApiConfig({ ...apiConfig, gangsheetWidth: '22', gangsheetHeight: '72' })}
                    className="text-xs px-3 py-1 border border-border rounded-full hover:bg-muted"
                  >
                    22" x 72" (DTF Roll)
                  </button>
                  <button
                    type="button"
                    onClick={() => setApiConfig({ ...apiConfig, gangsheetWidth: '13', gangsheetHeight: '19' })}
                    className="text-xs px-3 py-1 border border-border rounded-full hover:bg-muted"
                  >
                    13" x 19"
                  </button>
                  <button
                    type="button"
                    onClick={() => setApiConfig({ ...apiConfig, gangsheetWidth: '24', gangsheetHeight: '36' })}
                    className="text-xs px-3 py-1 border border-border rounded-full hover:bg-muted"
                  >
                    24" x 36"
                  </button>
                </div>
              </div>

              {/* Quality */}
              <div className="border border-border rounded-lg p-4">
                <h3 className="font-medium mb-4">Quality & Layout</h3>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium mb-1">DPI</label>
                    <select
                      value={apiConfig.gangsheetDpi}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetDpi: e.target.value })}
                      className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                    >
                      <option value="150">150 DPI</option>
                      <option value="300">300 DPI</option>
                      <option value="600">600 DPI</option>
                    </select>
                  </div>
                  <div>
                    <label className="block text-sm font-medium mb-1">Spacing (in)</label>
                    <input
                      type="number"
                      value={apiConfig.gangsheetSpacing}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetSpacing: e.target.value })}
                      className="w-full px-4 py-2 border border-border rounded-lg bg-background"
                      min="0"
                      max="2"
                      step="0.125"
                    />
                  </div>
                </div>

                <div className="mt-4">
                  <label className="block text-sm font-medium mb-1">Background</label>
                  <div className="flex gap-2">
                    <input
                      type="color"
                      value={apiConfig.gangsheetBackgroundColor}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetBackgroundColor: e.target.value })}
                      className="w-12 h-10 border border-border rounded-lg cursor-pointer"
                    />
                    <input
                      type="text"
                      value={apiConfig.gangsheetBackgroundColor}
                      onChange={(e) => setApiConfig({ ...apiConfig, gangsheetBackgroundColor: e.target.value })}
                      className="flex-1 px-4 py-2 border border-border rounded-lg bg-background font-mono text-sm"
                    />
                  </div>
                </div>

                <div className="mt-4 flex items-center gap-3">
                  <input
                    type="checkbox"
                    id="autoArrange"
                    checked={apiConfig.gangsheetAutoArrange}
                    onChange={(e) => setApiConfig({ ...apiConfig, gangsheetAutoArrange: e.target.checked })}
                    className="w-4 h-4 rounded border-border"
                  />
                  <label htmlFor="autoArrange" className="text-sm">
                    Auto-arrange designs to minimize waste
                  </label>
                </div>
              </div>

              {/* Preview */}
              <div className="bg-muted/50 rounded-lg p-4 text-center">
                <p className="text-sm text-muted-foreground mb-2">Preview</p>
                <div
                  className="border-2 border-dashed border-border rounded-lg flex items-center justify-center mx-auto"
                  style={{
                    width: '180px',
                    height: `${Math.min((parseFloat(apiConfig.gangsheetHeight) / parseFloat(apiConfig.gangsheetWidth)) * 180, 250)}px`,
                    backgroundColor: apiConfig.gangsheetBackgroundColor,
                  }}
                >
                  <span className="text-xs text-muted-foreground bg-background/80 px-2 py-1 rounded">
                    {apiConfig.gangsheetWidth}" × {apiConfig.gangsheetHeight}"
                  </span>
                </div>
              </div>
            </div>
          </div>
        );

      case 'complete':
        return (
          <div className="text-center py-8">
            <div className="w-20 h-20 rounded-full bg-green-100 dark:bg-green-900/30 flex items-center justify-center mx-auto mb-6">
              <CheckCircle className="w-10 h-10 text-green-600" />
            </div>
            <h2 className="text-2xl font-bold mb-4">You're All Set!</h2>
            <p className="text-muted-foreground mb-8 max-w-md mx-auto">
              Your store is ready. You can update these settings anytime from your dashboard.
            </p>

            <div className="bg-muted/50 rounded-lg p-4 max-w-md mx-auto text-left">
              <h3 className="font-medium mb-3">Your store:</h3>
              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Name</span>
                  <span className="font-medium">{apiConfig.businessName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">URL</span>
                  <span className="font-medium">{apiConfig.subdomain}.printnest.com</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Gangsheet</span>
                  <span className="font-medium">{apiConfig.gangsheetWidth}" × {apiConfig.gangsheetHeight}"</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">ShipStation</span>
                  <span className="font-medium">{apiConfig.shipstationApiKey ? '✓ Connected' : 'Not configured'}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">AWS S3</span>
                  <span className="font-medium">{apiConfig.awsAccessKeyId ? '✓ Connected' : 'Not configured'}</span>
                </div>
              </div>
            </div>
          </div>
        );

      default:
        return null;
    }
  };

  if (!onboardingData) {
    return null;
  }

  return (
    <div className="min-h-screen bg-background flex">
      {/* Sidebar */}
      <div className="hidden lg:flex w-72 bg-card border-r border-border flex-col p-6">
        <div className="flex items-center gap-2 mb-8">
          <div className="w-10 h-10 rounded-lg bg-primary flex items-center justify-center text-white font-bold text-xl">
            P
          </div>
          <span className="text-xl font-bold">PrintNest</span>
        </div>

        <nav className="flex-1">
          <ul className="space-y-1">
            {steps.map((step, index) => {
              const isComplete = index < currentStep;
              const isCurrent = index === currentStep;

              return (
                <li key={step.id}>
                  <div
                    className={cn(
                      'flex items-center gap-3 px-3 py-2 rounded-lg transition-colors',
                      isCurrent && 'bg-primary/10 text-primary',
                      !isCurrent && 'text-muted-foreground'
                    )}
                  >
                    <div
                      className={cn(
                        'w-7 h-7 rounded-full flex items-center justify-center text-xs',
                        isComplete && 'bg-green-100 text-green-600',
                        isCurrent && 'bg-primary text-primary-foreground',
                        !isComplete && !isCurrent && 'bg-muted'
                      )}
                    >
                      {isComplete ? (
                        <CheckCircle className="w-3 h-3" />
                      ) : (
                        <step.icon className="w-3 h-3" />
                      )}
                    </div>
                    <span className="text-sm font-medium">{step.title}</span>
                  </div>
                </li>
              );
            })}
          </ul>
        </nav>

        <div className="text-xs text-muted-foreground">
          Need help?{' '}
          <a href="mailto:support@printnest.com" className="text-primary hover:underline">
            Contact support
          </a>
        </div>
      </div>

      {/* Main */}
      <div className="flex-1 flex flex-col">
        {/* Mobile progress */}
        <div className="lg:hidden border-b border-border p-4">
          <div className="flex items-center gap-2 mb-3">
            <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center text-white font-bold">
              P
            </div>
            <span className="font-bold">PrintNest</span>
          </div>
          <div className="flex gap-1">
            {steps.map((_, index) => (
              <div
                key={index}
                className={cn(
                  'h-1 flex-1 rounded-full',
                  index <= currentStep ? 'bg-primary' : 'bg-muted'
                )}
              />
            ))}
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-lg mx-auto">{renderStepContent()}</div>
        </div>

        {/* Footer */}
        <div className="border-t border-border p-4">
          <div className="max-w-lg mx-auto flex items-center justify-between">
            <button
              onClick={handleBack}
              disabled={currentStep === 0}
              className="flex items-center gap-2 px-4 py-2 text-sm text-muted-foreground hover:text-foreground disabled:opacity-50"
            >
              <ArrowLeft className="w-4 h-4" />
              Back
            </button>

            <div className="flex items-center gap-3">
              {currentStep < steps.length - 1 ? (
                <button
                  onClick={handleNext}
                  disabled={!isCurrentStepValid()}
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-primary-foreground rounded-lg text-sm hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Continue
                  <ArrowRight className="w-4 h-4" />
                </button>
              ) : (
                <button
                  onClick={handleComplete}
                  disabled={isLoading}
                  className="flex items-center gap-2 px-6 py-2 bg-primary text-primary-foreground rounded-lg text-sm hover:bg-primary/90 disabled:opacity-70"
                >
                  {isLoading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Finishing...
                    </>
                  ) : (
                    <>
                      Go to Dashboard
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

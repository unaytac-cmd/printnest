import { Routes, Route, NavLink, Navigate } from 'react-router-dom';
import { Store, CreditCard, Bell, User, Shield, Palette, Truck, Users, RefreshCw, CheckCircle, XCircle, Eye, EyeOff, Plus, Settings2, Cloud, Package, ShoppingBag, FolderTree, UserCircle } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useState, useEffect, lazy, Suspense } from 'react';
import apiClient from '@/api/client';

// Lazy load heavy components
const ProductsPage = lazy(() => import('@/pages/Products'));
const CustomersPage = lazy(() => import('@/pages/Customers'));
const CategoriesPage = lazy(() => import('@/pages/Categories'));

const settingsNav = [
  // Store & Business
  { title: 'Store', href: '/settings/store', icon: Store, section: 'business' },
  { title: 'Products', href: '/settings/products', icon: ShoppingBag, section: 'business' },
  { title: 'Categories', href: '/settings/categories', icon: FolderTree, section: 'business' },
  { title: 'Customers', href: '/settings/customers', icon: UserCircle, section: 'business' },
  { title: 'Sub-dealers', href: '/settings/subdealers', icon: Users, section: 'business' },

  // Integrations
  { title: 'ShipStation', href: '/settings/shipstation', icon: Truck, section: 'integrations' },
  { title: 'Stripe', href: '/settings/stripe', icon: CreditCard, section: 'integrations' },
  { title: 'AWS', href: '/settings/aws', icon: Cloud, section: 'integrations' },
  { title: 'Shipping', href: '/settings/shipping', icon: Package, section: 'integrations' },

  // Account & Security
  { title: 'Notifications', href: '/settings/notifications', icon: Bell, section: 'account' },
  { title: 'Account', href: '/settings/account', icon: User, section: 'account' },
  { title: 'Security', href: '/settings/security', icon: Shield, section: 'account' },
  { title: 'Branding', href: '/settings/branding', icon: Palette, section: 'account' },
];

function SettingsLayout({ children }: { children: React.ReactNode }) {
  const businessItems = settingsNav.filter(item => item.section === 'business');
  const integrationItems = settingsNav.filter(item => item.section === 'integrations');
  const accountItems = settingsNav.filter(item => item.section === 'account');

  const renderNavSection = (title: string, items: typeof settingsNav) => (
    <div className="space-y-1">
      <p className="px-3 py-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
        {title}
      </p>
      {items.map((item) => (
        <NavLink
          key={item.href}
          to={item.href}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
              isActive
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-muted'
            )
          }
        >
          <item.icon className="w-5 h-5" />
          {item.title}
        </NavLink>
      ))}
    </div>
  );

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Settings</h1>
        <p className="text-muted-foreground">Manage your store settings</p>
      </div>

      <div className="flex flex-col lg:flex-row gap-6">
        {/* Sidebar Navigation */}
        <nav className="lg:w-64 flex-shrink-0">
          <div className="bg-card border border-border rounded-xl p-2 space-y-4">
            {renderNavSection('Business', businessItems)}
            {renderNavSection('Integrations', integrationItems)}
            {renderNavSection('Account', accountItems)}
          </div>
        </nav>

        {/* Content */}
        <div className="flex-1">{children}</div>
      </div>
    </div>
  );
}

function StoreSettings() {
  const [storeName, setStoreName] = useState('');
  const [subdomain, setSubdomain] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await apiClient.get('/settings');
        const settings = response.data;
        setStoreName(settings.name || '');
        setSubdomain(settings.subdomain || '');
      } catch (err) {
        console.error('Failed to fetch settings:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await apiClient.put('/settings', {
        name: storeName,
      });
    } catch (err) {
      console.error('Failed to save settings:', err);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-6">
      <h2 className="text-lg font-semibold">Store Settings</h2>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Store Name</label>
          <input
            type="text"
            value={storeName}
            onChange={(e) => setStoreName(e.target.value)}
            placeholder="Enter your store name"
            className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Store URL</label>
          <div className="flex items-center gap-2">
            <span className="text-muted-foreground">https://</span>
            <input
              type="text"
              value={subdomain}
              disabled
              className="flex-1 px-3 py-2 border border-border rounded-lg bg-muted text-muted-foreground"
            />
            <span className="text-muted-foreground">.printnest.com</span>
          </div>
          <p className="text-xs text-muted-foreground mt-1">Subdomain cannot be changed after registration</p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Currency</label>
            <select className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring">
              <option>USD - US Dollar</option>
              <option>EUR - Euro</option>
              <option>GBP - British Pound</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Timezone</label>
            <select className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring">
              <option>America/New_York (EST)</option>
              <option>America/Los_Angeles (PST)</option>
              <option>Europe/London (GMT)</option>
            </select>
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-border">
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
        >
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}


function NotificationsSettings() {
  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-6">
      <h2 className="text-lg font-semibold">Notification Preferences</h2>

      <div className="space-y-4">
        {[
          { title: 'New Orders', description: 'Get notified when you receive a new order' },
          { title: 'Order Shipped', description: 'Notify when orders are shipped' },
          { title: 'Low Stock', description: 'Alert when product stock is low' },
          { title: 'Customer Messages', description: 'New messages from customers' },
          { title: 'Weekly Reports', description: 'Weekly summary of your store performance' },
        ].map((item) => (
          <div
            key={item.title}
            className="flex items-center justify-between py-3 border-b border-border last:border-0"
          >
            <div>
              <p className="font-medium">{item.title}</p>
              <p className="text-sm text-muted-foreground">{item.description}</p>
            </div>
            <label className="relative inline-flex items-center cursor-pointer">
              <input type="checkbox" className="sr-only peer" defaultChecked />
              <div className="w-11 h-6 bg-muted rounded-full peer peer-checked:bg-primary peer-checked:after:translate-x-full after:content-[''] after:absolute after:top-0.5 after:left-[2px] after:bg-white after:rounded-full after:h-5 after:w-5 after:transition-all"></div>
            </label>
          </div>
        ))}
      </div>
    </div>
  );
}

function PlaceholderSettings({ title }: { title: string }) {
  return (
    <div className="bg-card border border-border rounded-xl p-6">
      <h2 className="text-lg font-semibold mb-4">{title} Settings</h2>
      <p className="text-muted-foreground">Settings coming soon...</p>
    </div>
  );
}

// =====================================================
// SHIPSTATION SETTINGS
// =====================================================

interface ShipStationStore {
  id: number;
  shipstationStoreId: number;
  storeName: string;
  marketplaceName: string | null;
  isActive: boolean;
}

function ShipStationSettings() {
  const [apiKey, setApiKey] = useState('');
  const [apiSecret, setApiSecret] = useState('');
  const [showApiKey, setShowApiKey] = useState(false);
  const [showApiSecret, setShowApiSecret] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [stores, setStores] = useState<ShipStationStore[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Fetch saved settings on mount
  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await apiClient.get('/settings');
        const settings = response.data;
        if (settings.shipstationSettings) {
          setApiKey(settings.shipstationSettings.apiKey || '');
          setApiSecret(settings.shipstationSettings.apiSecret || '');
          setIsConnected(settings.shipstationSettings.isConnected || (settings.shipstationSettings.apiKey && settings.shipstationSettings.apiSecret));
        }
      } catch (err) {
        console.error('Failed to fetch settings:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleConnect = async () => {
    if (!apiKey || !apiSecret) {
      setError('Please enter both API Key and API Secret');
      return;
    }

    setIsConnecting(true);
    setError(null);

    try {
      // TODO: Call API to connect ShipStation
      await new Promise(resolve => setTimeout(resolve, 1500));
      setIsConnected(true);
      // Automatically sync stores after connecting
      handleSyncStores();
    } catch {
      setError('Failed to connect to ShipStation. Please check your credentials.');
    } finally {
      setIsConnecting(false);
    }
  };

  const handleDisconnect = () => {
    setIsConnected(false);
    setApiKey('');
    setApiSecret('');
    setStores([]);
  };

  const handleSyncStores = async () => {
    setIsSyncing(true);
    setError(null);

    try {
      // TODO: Call API to sync stores from ShipStation
      // const response = await apiClient.post('/shipstation/sync-stores');
      // setStores(response.data.stores);
      setError('ShipStation sync not implemented yet. Configure in Settings after connecting.');
    } catch {
      setError('Failed to sync stores from ShipStation.');
    } finally {
      setIsSyncing(false);
    }
  };

  const toggleStoreStatus = (storeId: number) => {
    setStores(stores.map(store =>
      store.id === storeId ? { ...store, isActive: !store.isActive } : store
    ));
  };

  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Connection Card */}
      <div className="bg-card border border-border rounded-xl p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">ShipStation Integration</h2>
            <p className="text-sm text-muted-foreground">
              Connect your ShipStation account to sync orders and stores
            </p>
          </div>
          {isConnected && (
            <div className="flex items-center gap-2 text-green-600">
              <CheckCircle className="w-5 h-5" />
              <span className="text-sm font-medium">Connected</span>
            </div>
          )}
        </div>

        {error && (
          <div className="p-3 bg-destructive/10 border border-destructive/20 rounded-lg text-destructive text-sm">
            {error}
          </div>
        )}

        {!isConnected ? (
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">API Key</label>
              <div className="relative">
                <input
                  type={showApiKey ? 'text' : 'password'}
                  value={apiKey}
                  onChange={(e) => setApiKey(e.target.value)}
                  placeholder="Enter your ShipStation API Key"
                  className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium mb-1">API Secret</label>
              <div className="relative">
                <input
                  type={showApiSecret ? 'text' : 'password'}
                  value={apiSecret}
                  onChange={(e) => setApiSecret(e.target.value)}
                  placeholder="Enter your ShipStation API Secret"
                  className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                />
                <button
                  type="button"
                  onClick={() => setShowApiSecret(!showApiSecret)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showApiSecret ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <p className="text-xs text-muted-foreground">
              You can find your API credentials in ShipStation under Settings &gt; Account &gt; API Settings
            </p>

            <button
              onClick={handleConnect}
              disabled={isConnecting}
              className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-2"
            >
              {isConnecting ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin" />
                  Connecting...
                </>
              ) : (
                'Connect ShipStation'
              )}
            </button>
          </div>
        ) : (
          <div className="flex items-center gap-4">
            <button
              onClick={handleSyncStores}
              disabled={isSyncing}
              className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-2"
            >
              <RefreshCw className={cn("w-4 h-4", isSyncing && "animate-spin")} />
              {isSyncing ? 'Syncing...' : 'Sync Stores'}
            </button>
            <button
              onClick={handleDisconnect}
              className="px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors text-destructive"
            >
              Disconnect
            </button>
          </div>
        )}
      </div>

      {/* Stores List */}
      {isConnected && stores.length > 0 && (
        <div className="bg-card border border-border rounded-xl p-6 space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-semibold">Synced Stores ({stores.length})</h2>
          </div>

          <div className="space-y-3">
            {stores.map((store) => (
              <div
                key={store.id}
                className="flex items-center justify-between p-4 border border-border rounded-lg"
              >
                <div className="flex items-center gap-3">
                  <div className={cn(
                    "w-2 h-2 rounded-full",
                    store.isActive ? "bg-green-500" : "bg-gray-400"
                  )} />
                  <div>
                    <p className="font-medium">{store.storeName}</p>
                    <p className="text-sm text-muted-foreground">
                      {store.marketplaceName} - ID: {store.shipstationStoreId}
                    </p>
                  </div>
                </div>
                <button
                  onClick={() => toggleStoreStatus(store.id)}
                  className={cn(
                    "px-3 py-1 text-sm rounded-lg transition-colors",
                    store.isActive
                      ? "bg-green-100 text-green-700 hover:bg-green-200 dark:bg-green-900/30 dark:text-green-400"
                      : "bg-gray-100 text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-400"
                  )}
                >
                  {store.isActive ? 'Active' : 'Inactive'}
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// =====================================================
// STRIPE SETTINGS
// =====================================================

function StripeSettings() {
  const [publicKey, setPublicKey] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [showSecretKey, setShowSecretKey] = useState(false);
  const [isConnected, setIsConnected] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await apiClient.get('/settings');
        const settings = response.data;
        if (settings.stripeSettings) {
          setPublicKey(settings.stripeSettings.publicKey || '');
          setSecretKey(settings.stripeSettings.secretKey || '');
          setIsConnected(settings.stripeSettings.isConnected || (settings.stripeSettings.secretKey ? true : false));
        }
      } catch (err) {
        console.error('Failed to fetch settings:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await apiClient.put('/settings', {
        stripeSettings: {
          publicKey: publicKey || null,
          secretKey: secretKey || null,
          isConnected: !!(publicKey && secretKey),
        },
      });
      setIsConnected(!!(publicKey && secretKey));
    } catch (err) {
      console.error('Failed to save settings:', err);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold">Stripe Settings</h2>
          <p className="text-sm text-muted-foreground">
            Configure your Stripe payment processing
          </p>
        </div>
        {isConnected && (
          <div className="flex items-center gap-2 text-green-600">
            <CheckCircle className="w-5 h-5" />
            <span className="text-sm font-medium">Connected</span>
          </div>
        )}
      </div>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Publishable Key</label>
          <input
            type="text"
            value={publicKey}
            onChange={(e) => setPublicKey(e.target.value)}
            placeholder="pk_live_..."
            className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Secret Key</label>
          <div className="relative">
            <input
              type={showSecretKey ? 'text' : 'password'}
              value={secretKey}
              onChange={(e) => setSecretKey(e.target.value)}
              placeholder="sk_live_..."
              className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
            />
            <button
              type="button"
              onClick={() => setShowSecretKey(!showSecretKey)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              {showSecretKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <p className="text-xs text-muted-foreground">
          Find your API keys in your Stripe Dashboard under Developers &gt; API Keys
        </p>
      </div>

      <div className="pt-4 border-t border-border">
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
        >
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}

// =====================================================
// AWS SETTINGS
// =====================================================

function AwsSettings() {
  const [accessKeyId, setAccessKeyId] = useState('');
  const [secretAccessKey, setSecretAccessKey] = useState('');
  const [region, setRegion] = useState('us-east-1');
  const [s3Bucket, setS3Bucket] = useState('');
  const [showSecretKey, setShowSecretKey] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await apiClient.get('/settings');
        const settings = response.data;
        if (settings.awsSettings) {
          setAccessKeyId(settings.awsSettings.accessKeyId || '');
          setSecretAccessKey(settings.awsSettings.secretAccessKey || '');
          setRegion(settings.awsSettings.region || 'us-east-1');
          setS3Bucket(settings.awsSettings.s3Bucket || '');
        }
      } catch (err) {
        console.error('Failed to fetch settings:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await apiClient.put('/settings', {
        awsSettings: {
          accessKeyId: accessKeyId || null,
          secretAccessKey: secretAccessKey || null,
          region: region || 'us-east-1',
          s3Bucket: s3Bucket || null,
        },
      });
    } catch (err) {
      console.error('Failed to save settings:', err);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-6">
      <div>
        <h2 className="text-lg font-semibold">AWS Settings</h2>
        <p className="text-sm text-muted-foreground">
          Configure your AWS S3 storage for designs and gangsheets
        </p>
      </div>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Access Key ID</label>
          <input
            type="text"
            value={accessKeyId}
            onChange={(e) => setAccessKeyId(e.target.value)}
            placeholder="AKIA..."
            className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
          />
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">Secret Access Key</label>
          <div className="relative">
            <input
              type={showSecretKey ? 'text' : 'password'}
              value={secretAccessKey}
              onChange={(e) => setSecretAccessKey(e.target.value)}
              placeholder="Enter your secret access key"
              className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
            />
            <button
              type="button"
              onClick={() => setShowSecretKey(!showSecretKey)}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            >
              {showSecretKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            </button>
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium mb-1">Region</label>
            <select
              value={region}
              onChange={(e) => setRegion(e.target.value)}
              className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            >
              <option value="us-east-1">US East (N. Virginia)</option>
              <option value="us-east-2">US East (Ohio)</option>
              <option value="us-west-1">US West (N. California)</option>
              <option value="us-west-2">US West (Oregon)</option>
              <option value="eu-west-1">EU (Ireland)</option>
              <option value="eu-central-1">EU (Frankfurt)</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">S3 Bucket</label>
            <input
              type="text"
              value={s3Bucket}
              onChange={(e) => setS3Bucket(e.target.value)}
              placeholder="my-bucket-name"
              className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
            />
          </div>
        </div>

        <p className="text-xs text-muted-foreground">
          Create an IAM user with S3 access and generate access keys in AWS Console
        </p>
      </div>

      <div className="pt-4 border-t border-border">
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
        >
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}

// =====================================================
// SHIPPING SETTINGS
// =====================================================

function ShippingSettingsPage() {
  const [nestshipperApiKey, setNestshipperApiKey] = useState('');
  const [easypostApiKey, setEasypostApiKey] = useState('');
  const [showNestshipperKey, setShowNestshipperKey] = useState(false);
  const [showEasypostKey, setShowEasypostKey] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    const fetchSettings = async () => {
      try {
        const response = await apiClient.get('/settings');
        const settings = response.data;
        if (settings.shippingSettings) {
          setNestshipperApiKey(settings.shippingSettings.nestshipperApiKey || '');
          setEasypostApiKey(settings.shippingSettings.easypostApiKey || '');
        }
      } catch (err) {
        console.error('Failed to fetch settings:', err);
      } finally {
        setIsLoading(false);
      }
    };
    fetchSettings();
  }, []);

  const handleSave = async () => {
    setIsSaving(true);
    try {
      await apiClient.put('/settings', {
        shippingSettings: {
          nestshipperApiKey: nestshipperApiKey || null,
          easypostApiKey: easypostApiKey || null,
        },
      });
    } catch (err) {
      console.error('Failed to save settings:', err);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 flex items-center justify-center h-64">
        <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-6">
      <div>
        <h2 className="text-lg font-semibold">Shipping Settings</h2>
        <p className="text-sm text-muted-foreground">
          Configure your shipping label providers
        </p>
      </div>

      <div className="space-y-6">
        {/* NestShipper */}
        <div className="space-y-4">
          <h3 className="font-medium">NestShipper</h3>
          <div>
            <label className="block text-sm font-medium mb-1">API Key</label>
            <div className="relative">
              <input
                type={showNestshipperKey ? 'text' : 'password'}
                value={nestshipperApiKey}
                onChange={(e) => setNestshipperApiKey(e.target.value)}
                placeholder="Enter your NestShipper API Key"
                className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
              />
              <button
                type="button"
                onClick={() => setShowNestshipperKey(!showNestshipperKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showNestshipperKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>

        <div className="border-t border-border" />

        {/* EasyPost */}
        <div className="space-y-4">
          <h3 className="font-medium">EasyPost</h3>
          <div>
            <label className="block text-sm font-medium mb-1">API Key</label>
            <div className="relative">
              <input
                type={showEasypostKey ? 'text' : 'password'}
                value={easypostApiKey}
                onChange={(e) => setEasypostApiKey(e.target.value)}
                placeholder="Enter your EasyPost API Key"
                className="w-full px-3 py-2 pr-10 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring font-mono text-sm"
              />
              <button
                type="button"
                onClick={() => setShowEasypostKey(!showEasypostKey)}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                {showEasypostKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div className="pt-4 border-t border-border">
        <button
          onClick={handleSave}
          disabled={isSaving}
          className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
        >
          {isSaving ? 'Saving...' : 'Save Changes'}
        </button>
      </div>
    </div>
  );
}

// =====================================================
// SUBDEALERS SETTINGS
// =====================================================

interface Subdealer {
  id: number;
  email: string;
  firstName: string | null;
  lastName: string | null;
  fullName: string;
  status: number;
  totalCredit: string;
  assignedStores: ShipStationStore[];
  createdAt: string;
}

function SubdealersSettings() {
  const [subdealers, setSubdealers] = useState<Subdealer[]>([]);

  // TODO: Fetch subdealers from API using useEffect

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newSubdealer, setNewSubdealer] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
  });

  const handleCreateSubdealer = async () => {
    // TODO: Call API to create subdealer
    const created: Subdealer = {
      id: subdealers.length + 1,
      email: newSubdealer.email,
      firstName: newSubdealer.firstName || null,
      lastName: newSubdealer.lastName || null,
      fullName: `${newSubdealer.firstName} ${newSubdealer.lastName}`.trim() || newSubdealer.email,
      status: 1,
      totalCredit: '0.00',
      assignedStores: [],
      createdAt: new Date().toISOString().split('T')[0],
    };
    setSubdealers([...subdealers, created]);
    setShowCreateModal(false);
    setNewSubdealer({ email: '', password: '', firstName: '', lastName: '' });
  };

  const handleDeactivate = (id: number) => {
    setSubdealers(subdealers.map(s =>
      s.id === id ? { ...s, status: 0 } : s
    ));
  };

  const handleActivate = (id: number) => {
    setSubdealers(subdealers.map(s =>
      s.id === id ? { ...s, status: 1 } : s
    ));
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-card border border-border rounded-xl p-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-lg font-semibold">Sub-dealers</h2>
            <p className="text-sm text-muted-foreground">
              Manage your sub-dealers and their store assignments
            </p>
          </div>
          <button
            onClick={() => setShowCreateModal(true)}
            className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors flex items-center gap-2"
          >
            <Plus className="w-4 h-4" />
            Add Sub-dealer
          </button>
        </div>
      </div>

      {/* Subdealers List */}
      <div className="bg-card border border-border rounded-xl overflow-hidden">
        <table className="w-full">
          <thead className="bg-muted/50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Sub-dealer
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Assigned Stores
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Credit
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Status
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-muted-foreground uppercase tracking-wider">
                Actions
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {subdealers.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-12 text-center">
                  <Users className="w-12 h-12 mx-auto text-muted-foreground mb-4" />
                  <p className="text-muted-foreground">No sub-dealers yet</p>
                  <p className="text-sm text-muted-foreground mt-1">Add a sub-dealer to get started</p>
                </td>
              </tr>
            ) : null}
            {subdealers.map((subdealer) => (
              <tr key={subdealer.id} className="hover:bg-muted/30">
                <td className="px-6 py-4 whitespace-nowrap">
                  <div>
                    <p className="font-medium">{subdealer.fullName}</p>
                    <p className="text-sm text-muted-foreground">{subdealer.email}</p>
                  </div>
                </td>
                <td className="px-6 py-4">
                  <div className="flex flex-wrap gap-1">
                    {subdealer.assignedStores.length > 0 ? (
                      subdealer.assignedStores.map((store) => (
                        <span
                          key={store.id}
                          className="px-2 py-1 text-xs bg-muted rounded-full"
                        >
                          {store.storeName}
                        </span>
                      ))
                    ) : (
                      <span className="text-sm text-muted-foreground">No stores assigned</span>
                    )}
                  </div>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span className="font-medium">${subdealer.totalCredit}</span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap">
                  <span
                    className={cn(
                      "px-2 py-1 text-xs rounded-full",
                      subdealer.status === 1
                        ? "bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400"
                        : "bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400"
                    )}
                  >
                    {subdealer.status === 1 ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="px-6 py-4 whitespace-nowrap text-right">
                  <div className="flex items-center justify-end gap-2">
                    <button
                      className="p-2 hover:bg-muted rounded-lg transition-colors"
                      title="Manage stores"
                    >
                      <Settings2 className="w-4 h-4" />
                    </button>
                    {subdealer.status === 1 ? (
                      <button
                        onClick={() => handleDeactivate(subdealer.id)}
                        className="p-2 hover:bg-muted rounded-lg transition-colors text-amber-600"
                        title="Deactivate"
                      >
                        <XCircle className="w-4 h-4" />
                      </button>
                    ) : (
                      <button
                        onClick={() => handleActivate(subdealer.id)}
                        className="p-2 hover:bg-muted rounded-lg transition-colors text-green-600"
                        title="Activate"
                      >
                        <CheckCircle className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Create Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className="bg-card border border-border rounded-xl p-6 w-full max-w-md space-y-4">
            <h3 className="text-lg font-semibold">Create Sub-dealer</h3>

            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium mb-1">First Name</label>
                  <input
                    type="text"
                    value={newSubdealer.firstName}
                    onChange={(e) => setNewSubdealer({ ...newSubdealer, firstName: e.target.value })}
                    className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Last Name</label>
                  <input
                    type="text"
                    value={newSubdealer.lastName}
                    onChange={(e) => setNewSubdealer({ ...newSubdealer, lastName: e.target.value })}
                    className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">Email *</label>
                <input
                  type="email"
                  value={newSubdealer.email}
                  onChange={(e) => setNewSubdealer({ ...newSubdealer, email: e.target.value })}
                  className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-1">Password *</label>
                <input
                  type="password"
                  value={newSubdealer.password}
                  onChange={(e) => setNewSubdealer({ ...newSubdealer, password: e.target.value })}
                  className="w-full px-3 py-2 border border-border rounded-lg bg-background focus:outline-none focus:ring-2 focus:ring-ring"
                  required
                />
              </div>
            </div>

            <div className="flex justify-end gap-3 pt-4">
              <button
                onClick={() => setShowCreateModal(false)}
                className="px-4 py-2 border border-border rounded-lg hover:bg-muted transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleCreateSubdealer}
                disabled={!newSubdealer.email || !newSubdealer.password}
                className="px-4 py-2 bg-primary text-primary-foreground rounded-lg hover:bg-primary/90 transition-colors disabled:opacity-50"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function PageLoader() {
  return (
    <div className="flex items-center justify-center h-64">
      <RefreshCw className="w-6 h-6 animate-spin text-muted-foreground" />
    </div>
  );
}

export default function Settings() {
  return (
    <SettingsLayout>
      <Routes>
        <Route index element={<Navigate to="/settings/store" replace />} />
        {/* Business */}
        <Route path="store" element={<StoreSettings />} />
        <Route path="products/*" element={
          <Suspense fallback={<PageLoader />}>
            <ProductsPage />
          </Suspense>
        } />
        <Route path="categories/*" element={
          <Suspense fallback={<PageLoader />}>
            <CategoriesPage />
          </Suspense>
        } />
        <Route path="customers/*" element={
          <Suspense fallback={<PageLoader />}>
            <CustomersPage />
          </Suspense>
        } />
        <Route path="subdealers" element={<SubdealersSettings />} />
        {/* Integrations */}
        <Route path="shipstation" element={<ShipStationSettings />} />
        <Route path="stripe" element={<StripeSettings />} />
        <Route path="aws" element={<AwsSettings />} />
        <Route path="shipping" element={<ShippingSettingsPage />} />
        {/* Account */}
        <Route path="notifications" element={<NotificationsSettings />} />
        <Route path="account" element={<PlaceholderSettings title="Account" />} />
        <Route path="security" element={<PlaceholderSettings title="Security" />} />
        <Route path="branding" element={<PlaceholderSettings title="Branding" />} />
      </Routes>
    </SettingsLayout>
  );
}

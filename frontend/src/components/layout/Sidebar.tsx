import { useState } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  LayoutDashboard,
  ShoppingCart,
  BarChart3,
  Settings,
  HelpCircle,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Store,
  Palette,
  Truck,
  LogOut,
  Layers,
  Link2,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useTenantBranding } from '@/hooks/useTenant';
import { useAuthStore, type UserRole } from '@/stores/authStore';

interface NavSubItem {
  title: string;
  href: string;
}

interface NavItem {
  title: string;
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  badge?: string | number;
  subItems?: NavSubItem[];
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

// Get navigation groups based on user role
function getNavigationGroups(role: UserRole | undefined): NavGroup[] {
  const isProducer = role === 'producer' || role === 'owner' || role === 'admin';
  const isSubdealer = role === 'subdealer';

  // Base navigation for all users
  const baseGroups: NavGroup[] = [
    {
      title: 'Overview',
      items: [
        { title: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
        ...(isProducer ? [{ title: 'Analytics', href: '/analytics', icon: BarChart3 }] : []),
      ],
    },
    {
      title: 'Orders',
      items: [
        {
          title: 'Orders',
          href: '/orders',
          icon: ShoppingCart,
          subItems: [
            { title: 'New Orders', href: '/orders/new-orders' },
            { title: 'Order List', href: '/orders/list' },
          ]
        },
      ],
    },
  ];

  // Producer-only navigation
  if (isProducer) {
    return [
      ...baseGroups,
      {
        title: 'Print on Demand',
        items: [
          { title: 'Design Studio', href: '/design-studio', icon: Palette },
          { title: 'Gangsheet', href: '/gangsheet', icon: Layers },
          { title: 'Mapping', href: '/mapping', icon: Link2 },
          { title: 'Catalog', href: '/catalog', icon: Store },
          { title: 'Fulfillment', href: '/fulfillment', icon: Truck },
        ],
      },
      {
        title: 'Settings',
        items: [
          { title: 'Settings', href: '/settings', icon: Settings },
        ],
      },
    ];
  }

  // Sub-dealer navigation (limited - no settings access)
  if (isSubdealer) {
    return baseGroups;
  }

  // Default navigation (legacy roles)
  return [
    ...baseGroups,
    {
      title: 'Print on Demand',
      items: [
        { title: 'Design Studio', href: '/design-studio', icon: Palette },
        { title: 'Gangsheet', href: '/gangsheet', icon: Layers },
        { title: 'Mapping', href: '/mapping', icon: Link2 },
        { title: 'Catalog', href: '/catalog', icon: Store },
        { title: 'Fulfillment', href: '/fulfillment', icon: Truck },
      ],
    },
    {
      title: 'Settings',
      items: [
        { title: 'Settings', href: '/settings', icon: Settings },
      ],
    },
  ];
}

interface SidebarProps {
  collapsed?: boolean;
  onCollapsedChange?: (collapsed: boolean) => void;
}

export function Sidebar({ collapsed = false, onCollapsedChange }: SidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState(collapsed);
  const [expandedItems, setExpandedItems] = useState<string[]>(['/orders']);
  const branding = useTenantBranding();
  const logout = useAuthStore((state) => state.logout);
  const userRole = useAuthStore((state) => state.user?.role);
  const location = useLocation();

  // Get navigation based on user role
  const navigationGroups = getNavigationGroups(userRole);

  const toggleExpanded = (href: string) => {
    setExpandedItems(prev =>
      prev.includes(href) ? prev.filter(h => h !== href) : [...prev, href]
    );
  };

  const isItemActive = (item: NavItem) => {
    if (item.subItems) {
      return item.subItems.some(sub => location.pathname === sub.href);
    }
    return location.pathname === item.href || location.pathname.startsWith(item.href + '/');
  };

  const handleToggleCollapse = () => {
    const newCollapsed = !isCollapsed;
    setIsCollapsed(newCollapsed);
    onCollapsedChange?.(newCollapsed);
  };

  return (
    <aside
      className={cn(
        'flex flex-col h-screen bg-card border-r border-border transition-all duration-300',
        isCollapsed ? 'w-[80px]' : 'w-[280px]'
      )}
    >
      {/* Logo */}
      <div className="flex items-center h-16 px-4 border-b border-border">
        {branding.logo ? (
          <img
            src={branding.logo}
            alt={branding.name}
            className={cn('h-8 transition-all', isCollapsed ? 'w-8' : 'w-auto')}
          />
        ) : (
          <div className="flex items-center gap-2">
            <div
              className="w-8 h-8 rounded-lg flex items-center justify-center text-white font-bold"
              style={{ backgroundColor: branding.primaryColor }}
            >
              {branding.name.charAt(0)}
            </div>
            {!isCollapsed && (
              <span className="font-semibold text-lg">{branding.name}</span>
            )}
          </div>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 overflow-y-auto py-4 no-scrollbar">
        {navigationGroups.map((group) => (
          <div key={group.title} className="mb-6">
            {!isCollapsed && (
              <h3 className="px-4 mb-2 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                {group.title}
              </h3>
            )}
            <ul className="space-y-1 px-2">
              {group.items.map((item) => {
                const hasSubItems = item.subItems && item.subItems.length > 0;
                const isExpanded = expandedItems.includes(item.href);
                const isActive = isItemActive(item);

                return (
                  <li key={item.href}>
                    {hasSubItems ? (
                      <>
                        <button
                          onClick={() => toggleExpanded(item.href)}
                          className={cn(
                            'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                            'hover:bg-accent hover:text-accent-foreground',
                            isActive
                              ? 'bg-primary/10 text-primary'
                              : 'text-muted-foreground',
                            isCollapsed && 'justify-center'
                          )}
                          title={isCollapsed ? item.title : undefined}
                        >
                          <item.icon className="w-5 h-5 flex-shrink-0" />
                          {!isCollapsed && (
                            <>
                              <span className="flex-1 text-left">{item.title}</span>
                              <ChevronDown
                                className={cn(
                                  'w-4 h-4 transition-transform',
                                  isExpanded && 'rotate-180'
                                )}
                              />
                            </>
                          )}
                        </button>
                        {!isCollapsed && isExpanded && (
                          <ul className="mt-1 ml-4 pl-4 border-l border-border space-y-1">
                            {item.subItems!.map((subItem) => (
                              <li key={subItem.href}>
                                <NavLink
                                  to={subItem.href}
                                  className={({ isActive }) =>
                                    cn(
                                      'flex items-center gap-2 px-3 py-2 rounded-lg text-sm transition-colors',
                                      'hover:bg-accent hover:text-accent-foreground',
                                      isActive
                                        ? 'bg-primary text-primary-foreground font-medium'
                                        : 'text-muted-foreground'
                                    )
                                  }
                                >
                                  {subItem.title}
                                </NavLink>
                              </li>
                            ))}
                          </ul>
                        )}
                      </>
                    ) : (
                      <NavLink
                        to={item.href}
                        className={({ isActive }) =>
                          cn(
                            'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
                            'hover:bg-accent hover:text-accent-foreground',
                            isActive
                              ? 'bg-primary text-primary-foreground'
                              : 'text-muted-foreground',
                            isCollapsed && 'justify-center'
                          )
                        }
                        title={isCollapsed ? item.title : undefined}
                      >
                        <item.icon className="w-5 h-5 flex-shrink-0" />
                        {!isCollapsed && (
                          <>
                            <span className="flex-1">{item.title}</span>
                            {item.badge && (
                              <span className="px-2 py-0.5 text-xs rounded-full bg-primary/10 text-primary">
                                {item.badge}
                              </span>
                            )}
                          </>
                        )}
                      </NavLink>
                    )}
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      {/* Footer */}
      <div className="border-t border-border p-2">
        {/* Help */}
        <NavLink
          to="/help"
          className={cn(
            'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
            'hover:bg-accent hover:text-accent-foreground text-muted-foreground',
            isCollapsed && 'justify-center'
          )}
          title={isCollapsed ? 'Help & Support' : undefined}
        >
          <HelpCircle className="w-5 h-5 flex-shrink-0" />
          {!isCollapsed && <span>Help & Support</span>}
        </NavLink>

        {/* Logout */}
        <button
          onClick={logout}
          className={cn(
            'w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors',
            'hover:bg-destructive/10 hover:text-destructive text-muted-foreground',
            isCollapsed && 'justify-center'
          )}
          title={isCollapsed ? 'Logout' : undefined}
        >
          <LogOut className="w-5 h-5 flex-shrink-0" />
          {!isCollapsed && <span>Logout</span>}
        </button>

        {/* Collapse Toggle */}
        <button
          onClick={handleToggleCollapse}
          className={cn(
            'w-full flex items-center gap-3 px-3 py-2 mt-2 rounded-lg text-sm font-medium transition-colors',
            'hover:bg-accent hover:text-accent-foreground text-muted-foreground',
            isCollapsed && 'justify-center'
          )}
          title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        >
          {isCollapsed ? (
            <ChevronRight className="w-5 h-5" />
          ) : (
            <>
              <ChevronLeft className="w-5 h-5" />
              <span>Collapse</span>
            </>
          )}
        </button>
      </div>
    </aside>
  );
}

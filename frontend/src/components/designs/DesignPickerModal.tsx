import { useState, useMemo } from 'react';
import * as Dialog from '@radix-ui/react-dialog';
import { Button, Input } from '@/components/ui';
import { DesignCard } from './DesignCard';
import { LoadingSpinner, EmptyState } from '@/components/common';
import type { Design } from '@/types';
import { XIcon, SearchIcon, ImageIcon, CheckIcon } from 'lucide-react';
import * as Tabs from '@radix-ui/react-tabs';

// Filter type includes 'all' for filtering purposes
type DesignFilterType = 'all' | 'image' | 'embroidery' | 'vector';
// Actual design type (no 'all')
type DesignType = 'image' | 'embroidery' | 'vector';

interface DesignPickerModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  designs: (Design & { type?: DesignType })[];
  loading?: boolean;
  onSelect: (design: Design) => void;
  selectedDesignId?: string;
  title?: string;
  description?: string;
}

export function DesignPickerModal({
  open,
  onOpenChange,
  designs,
  loading = false,
  onSelect,
  selectedDesignId: initialSelectedId,
  title = 'Select a Design',
  description = 'Choose a design to apply to your product',
}: DesignPickerModalProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<DesignFilterType>('all');
  const [selectedDesignId, setSelectedDesignId] = useState<string | undefined>(
    initialSelectedId
  );

  // Filter designs based on search and type
  const filteredDesigns = useMemo(() => {
    return designs.filter((design) => {
      const matchesSearch =
        !searchQuery ||
        design.name.toLowerCase().includes(searchQuery.toLowerCase());
      const matchesType =
        typeFilter === 'all' ||
        design.type === typeFilter;
      return matchesSearch && matchesType;
    });
  }, [designs, searchQuery, typeFilter]);

  const handleDesignClick = (design: Design) => {
    setSelectedDesignId(design.id);
  };

  const handleConfirm = () => {
    const selectedDesign = designs.find((d) => d.id === selectedDesignId);
    if (selectedDesign) {
      onSelect(selectedDesign);
      onOpenChange(false);
    }
  };

  const handleCancel = () => {
    setSelectedDesignId(initialSelectedId);
    setSearchQuery('');
    setTypeFilter('all');
    onOpenChange(false);
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 flex max-h-[90vh] w-full max-w-4xl -translate-x-1/2 -translate-y-1/2 flex-col rounded-lg border bg-background shadow-lg duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%]">
          {/* Header */}
          <div className="flex items-center justify-between border-b p-4">
            <div>
              <Dialog.Title className="text-lg font-semibold">
                {title}
              </Dialog.Title>
              {description && (
                <Dialog.Description className="text-sm text-muted-foreground">
                  {description}
                </Dialog.Description>
              )}
            </div>
            <Dialog.Close asChild>
              <Button variant="ghost" size="icon" onClick={handleCancel}>
                <XIcon className="h-5 w-5" />
              </Button>
            </Dialog.Close>
          </div>

          {/* Filters */}
          <div className="flex flex-wrap items-center gap-4 border-b p-4">
            {/* Search */}
            <div className="relative flex-1 min-w-[200px]">
              <SearchIcon className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                placeholder="Search designs..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="pl-9"
              />
            </div>

            {/* Type Filter */}
            <Tabs.Root
              value={typeFilter}
              onValueChange={(value) => setTypeFilter(value as DesignFilterType)}
            >
              <Tabs.List className="flex rounded-lg border p-1">
                <Tabs.Trigger
                  value="all"
                  className="rounded-md px-3 py-1.5 text-sm font-medium transition-colors data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
                >
                  All
                </Tabs.Trigger>
                <Tabs.Trigger
                  value="image"
                  className="rounded-md px-3 py-1.5 text-sm font-medium transition-colors data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
                >
                  Image
                </Tabs.Trigger>
                <Tabs.Trigger
                  value="embroidery"
                  className="rounded-md px-3 py-1.5 text-sm font-medium transition-colors data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
                >
                  Embroidery
                </Tabs.Trigger>
                <Tabs.Trigger
                  value="vector"
                  className="rounded-md px-3 py-1.5 text-sm font-medium transition-colors data-[state=active]:bg-primary data-[state=active]:text-primary-foreground"
                >
                  Vector
                </Tabs.Trigger>
              </Tabs.List>
            </Tabs.Root>
          </div>

          {/* Content */}
          <div className="flex-1 overflow-y-auto p-4">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <LoadingSpinner message="Loading designs..." />
              </div>
            ) : filteredDesigns.length === 0 ? (
              <EmptyState
                icon={<ImageIcon className="h-8 w-8 text-muted-foreground" />}
                title="No designs found"
                description="Try adjusting your search or filters"
              />
            ) : (
              <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4">
                {filteredDesigns.map((design) => (
                  <DesignCard
                    key={design.id}
                    design={design}
                    selected={selectedDesignId === design.id}
                    onSelect={handleDesignClick}
                    selectable
                  />
                ))}
              </div>
            )}
          </div>

          {/* Footer */}
          <div className="flex items-center justify-between border-t p-4">
            <div className="text-sm text-muted-foreground">
              {selectedDesignId ? (
                <span className="flex items-center gap-2">
                  <CheckIcon className="h-4 w-4 text-primary" />
                  Design selected
                </span>
              ) : (
                'Click on a design to select it'
              )}
            </div>
            <div className="flex items-center gap-3">
              <Button variant="outline" onClick={handleCancel}>
                Cancel
              </Button>
              <Button onClick={handleConfirm} disabled={!selectedDesignId}>
                Confirm Selection
              </Button>
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

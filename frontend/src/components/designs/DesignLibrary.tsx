import { useState, useMemo } from 'react';
import { Button, Input } from '@/components/ui';
import { DesignCard } from './DesignCard';
import { DesignUploader } from './DesignUploader';
import { EmptyState, LoadingSpinner } from '@/components/common';
import type { Design } from '@/types';
import { cn } from '@/lib/utils';
import {
  SearchIcon,
  UploadIcon,
  ImageIcon,
} from 'lucide-react';
import * as Tabs from '@radix-ui/react-tabs';

// Filter type includes 'all' for filtering purposes
type DesignFilterType = 'all' | 'image' | 'embroidery' | 'vector';
// Actual design type (no 'all')
type DesignType = 'image' | 'embroidery' | 'vector';

interface DesignLibraryProps {
  designs: (Design & { type?: DesignType })[];
  loading?: boolean;
  onSelectDesign?: (design: Design) => void;
  onPreviewDesign?: (design: Design) => void;
  onDeleteDesign?: (design: Design) => void;
  onDownloadDesign?: (design: Design) => void;
  onUploadDesigns?: (files: File[]) => Promise<void>;
  selectable?: boolean;
  selectedDesignId?: string;
  // Pagination
  page?: number;
  pageSize?: number;
  totalCount?: number;
  onPageChange?: (page: number) => void;
  className?: string;
}

export function DesignLibrary({
  designs,
  loading = false,
  onSelectDesign,
  onPreviewDesign,
  onDeleteDesign,
  onDownloadDesign,
  onUploadDesigns,
  selectable = false,
  selectedDesignId,
  page = 1,
  pageSize = 12,
  totalCount,
  onPageChange,
  className,
}: DesignLibraryProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [typeFilter, setTypeFilter] = useState<DesignFilterType>('all');
  const [showUploader, setShowUploader] = useState(false);

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

  const totalPages = totalCount
    ? Math.ceil(totalCount / pageSize)
    : Math.ceil(filteredDesigns.length / pageSize);

  const handleUpload = async (files: File[]) => {
    if (onUploadDesigns) {
      await onUploadDesigns(files);
      setShowUploader(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <LoadingSpinner message="Loading designs..." />
      </div>
    );
  }

  return (
    <div className={cn('space-y-6', className)}>
      {/* Header with Search and Filters */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 items-center gap-3">
          {/* Search */}
          <div className="relative flex-1 max-w-sm">
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

        {/* Upload Button */}
        {onUploadDesigns && (
          <Button onClick={() => setShowUploader(!showUploader)}>
            <UploadIcon className="mr-2 h-4 w-4" />
            Upload Design
          </Button>
        )}
      </div>

      {/* Uploader */}
      {showUploader && onUploadDesigns && (
        <DesignUploader onUpload={handleUpload} />
      )}

      {/* Design Grid */}
      {filteredDesigns.length === 0 ? (
        <EmptyState
          icon={<ImageIcon className="h-8 w-8 text-muted-foreground" />}
          title="No designs found"
          description={
            searchQuery || typeFilter !== 'all'
              ? 'Try adjusting your search or filters'
              : 'Upload your first design to get started'
          }
          action={
            onUploadDesigns && !searchQuery && typeFilter === 'all'
              ? {
                  label: 'Upload Design',
                  onClick: () => setShowUploader(true),
                }
              : undefined
          }
        />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
            {filteredDesigns.map((design) => (
              <DesignCard
                key={design.id}
                design={design}
                selected={selectedDesignId === design.id}
                onSelect={onSelectDesign}
                onPreview={onPreviewDesign}
                onDelete={onDeleteDesign}
                onDownload={onDownloadDesign}
                selectable={selectable}
              />
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && onPageChange && (
            <div className="flex items-center justify-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPageChange(page - 1)}
                disabled={page <= 1}
              >
                Previous
              </Button>
              <span className="text-sm text-muted-foreground">
                Page {page} of {totalPages}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onPageChange(page + 1)}
                disabled={page >= totalPages}
              >
                Next
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

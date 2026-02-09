import { useFormContext } from 'react-hook-form';
import { Button, Card, CardContent } from '@/components/ui';
import type { Design } from '@/types';
import type { OrderWizardData } from './types';
import { ImageIcon, UploadIcon, XIcon } from 'lucide-react';

interface Step2DesignsProps {
  designs: Design[];
  loading?: boolean;
  onOpenDesignPicker: (itemIndex: number) => void;
}

export function Step2Designs({
  designs,
  loading = false,
  onOpenDesignPicker,
}: Step2DesignsProps) {
  const { watch, setValue } = useFormContext<OrderWizardData>();
  const items = watch('items');
  const assignments = watch('assignments') || [];

  const getAssignment = (itemIndex: number) => {
    return assignments.find((a) => a.itemIndex === itemIndex);
  };

  const getDesign = (designId?: string) => {
    if (!designId) return null;
    return designs.find((d) => d.id === designId);
  };

  const handleRemoveDesign = (itemIndex: number) => {
    const newAssignments = assignments.filter((a) => a.itemIndex !== itemIndex);
    setValue('assignments', newAssignments);
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-xl font-semibold">Assign Designs</h2>
        <p className="text-sm text-muted-foreground">
          Select designs for each product in your order. You can skip this step if designs will be assigned later.
        </p>
      </div>

      <div className="space-y-4">
        {items.map((item, index) => {
          const assignment = getAssignment(index);
          const design = getDesign(assignment?.designId);

          return (
            <Card key={index}>
              <CardContent className="p-4">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
                  {/* Product Info */}
                  <div className="flex-1">
                    <h3 className="font-medium">
                      Product #{index + 1}
                    </h3>
                    <p className="text-sm text-muted-foreground">
                      Quantity: {item.quantity}
                    </p>
                  </div>

                  {/* Design Preview / Selection */}
                  <div className="flex items-center gap-4">
                    {design ? (
                      <div className="flex items-center gap-3">
                        <div className="relative h-16 w-16 overflow-hidden rounded-md border bg-muted">
                          <img
                            src={design.thumbnail}
                            alt={design.name}
                            className="h-full w-full object-cover"
                          />
                        </div>
                        <div>
                          <p className="font-medium">{design.name}</p>
                          <p className="text-sm text-muted-foreground">
                            {design.data.width} x {design.data.height}px
                          </p>
                        </div>
                        <Button
                          type="button"
                          variant="ghost"
                          size="icon"
                          onClick={() => handleRemoveDesign(index)}
                          className="text-muted-foreground hover:text-destructive"
                        >
                          <XIcon className="h-4 w-4" />
                        </Button>
                      </div>
                    ) : (
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => onOpenDesignPicker(index)}
                      >
                        <ImageIcon className="mr-2 h-4 w-4" />
                        Select Design
                      </Button>
                    )}

                    {design && (
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => onOpenDesignPicker(index)}
                      >
                        Change
                      </Button>
                    )}
                  </div>
                </div>

                {/* Placement Options */}
                {design && (
                  <div className="mt-4 border-t pt-4">
                    <label className="text-sm font-medium">Placement</label>
                    <div className="mt-2 flex flex-wrap gap-2">
                      {['Front', 'Back', 'Left Sleeve', 'Right Sleeve', 'Pocket'].map(
                        (placement) => (
                          <Button
                            key={placement}
                            type="button"
                            variant={
                              assignment?.placement === placement.toLowerCase()
                                ? 'default'
                                : 'outline'
                            }
                            size="sm"
                            onClick={() => {
                              const newAssignments = assignments.map((a) =>
                                a.itemIndex === index
                                  ? { ...a, placement: placement.toLowerCase() }
                                  : a
                              );
                              setValue('assignments', newAssignments);
                            }}
                          >
                            {placement}
                          </Button>
                        )
                      )}
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Quick Upload Option */}
      <Card className="border-dashed">
        <CardContent className="p-6">
          <div className="flex flex-col items-center justify-center text-center">
            <UploadIcon className="h-10 w-10 text-muted-foreground" />
            <h3 className="mt-4 font-medium">Need to upload a new design?</h3>
            <p className="mt-1 text-sm text-muted-foreground">
              You can upload new designs from the Design Library
            </p>
            <Button type="button" variant="outline" className="mt-4">
              Go to Design Library
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

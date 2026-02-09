import { useState, useCallback } from 'react';
import { useForm, FormProvider } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Card, CardContent } from '@/components/ui';
import { Step1Products } from './Step1Products';
import { Step2Designs } from './Step2Designs';
import { Step3Shipping } from './Step3Shipping';
import { Step4Payment } from './Step4Payment';
import {
  orderWizardSchema,
  wizardSteps,
  type OrderWizardData,
} from './types';
import type { Product, Design } from '@/types';
import { cn } from '@/lib/utils';
import { CheckIcon, ChevronLeftIcon, ChevronRightIcon } from 'lucide-react';

interface OrderWizardProps {
  products: Product[];
  designs: Design[];
  productsLoading?: boolean;
  designsLoading?: boolean;
  onSubmit: (data: OrderWizardData) => void;
  onCancel: () => void;
  submitting?: boolean;
}

export function OrderWizard({
  products,
  designs,
  productsLoading = false,
  designsLoading = false,
  onSubmit,
  onCancel,
  submitting = false,
}: OrderWizardProps) {
  const [currentStepIndex, setCurrentStepIndex] = useState(0);
  const [designPickerOpen, setDesignPickerOpen] = useState(false);
  const [designPickerItemIndex, setDesignPickerItemIndex] = useState<number | null>(null);

  const methods = useForm<OrderWizardData>({
    resolver: zodResolver(orderWizardSchema),
    defaultValues: {
      items: [{ productId: '', variantId: undefined, quantity: 1, price: 0 }],
      assignments: [],
      shippingAddress: {
        firstName: '',
        lastName: '',
        company: '',
        address1: '',
        address2: '',
        city: '',
        state: '',
        postalCode: '',
        country: 'United States',
        phone: '',
      },
      billingAddress: {
        firstName: '',
        lastName: '',
        company: '',
        address1: '',
        address2: '',
        city: '',
        state: '',
        postalCode: '',
        country: 'United States',
        phone: '',
      },
      sameAsBilling: true,
      shippingMethod: '',
      paymentMethod: 'card',
      notes: '',
    },
    mode: 'onChange',
  });

  const currentStep = wizardSteps[currentStepIndex];
  const isFirstStep = currentStepIndex === 0;
  const isLastStep = currentStepIndex === wizardSteps.length - 1;

  const handleOpenDesignPicker = useCallback((itemIndex: number) => {
    setDesignPickerItemIndex(itemIndex);
    setDesignPickerOpen(true);
  }, []);

  const handleSelectDesign = useCallback(
    (designId: string) => {
      if (designPickerItemIndex === null) return;

      const currentAssignments = methods.getValues('assignments') || [];
      const existingIndex = currentAssignments.findIndex(
        (a) => a.itemIndex === designPickerItemIndex
      );

      let newAssignments;
      if (existingIndex >= 0) {
        newAssignments = currentAssignments.map((a, i) =>
          i === existingIndex ? { ...a, designId } : a
        );
      } else {
        newAssignments = [
          ...currentAssignments,
          { itemIndex: designPickerItemIndex, designId, placement: 'front' },
        ];
      }

      methods.setValue('assignments', newAssignments);
      setDesignPickerOpen(false);
      setDesignPickerItemIndex(null);
    },
    [designPickerItemIndex, methods]
  );

  const validateCurrentStep = async (): Promise<boolean> => {
    let fieldsToValidate: (keyof OrderWizardData)[] = [];

    switch (currentStep.id) {
      case 'products':
        fieldsToValidate = ['items'];
        break;
      case 'designs':
        fieldsToValidate = ['assignments'];
        break;
      case 'shipping':
        fieldsToValidate = ['shippingAddress', 'billingAddress', 'shippingMethod'];
        break;
      case 'payment':
        fieldsToValidate = ['paymentMethod'];
        break;
    }

    const result = await methods.trigger(fieldsToValidate);
    return result;
  };

  const handleNext = async () => {
    const isValid = await validateCurrentStep();
    if (isValid && !isLastStep) {
      setCurrentStepIndex((prev) => prev + 1);
    }
  };

  const handleBack = () => {
    if (!isFirstStep) {
      setCurrentStepIndex((prev) => prev - 1);
    }
  };

  const handleSubmit = methods.handleSubmit((data) => {
    onSubmit(data);
  });

  const goToStep = async (stepIndex: number) => {
    if (stepIndex < currentStepIndex) {
      setCurrentStepIndex(stepIndex);
    } else if (stepIndex === currentStepIndex + 1) {
      const isValid = await validateCurrentStep();
      if (isValid) {
        setCurrentStepIndex(stepIndex);
      }
    }
  };

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit} className="space-y-6">
        {/* Progress Steps */}
        <nav className="flex items-center justify-center">
          <ol className="flex items-center space-x-2 sm:space-x-4">
            {wizardSteps.map((step, index) => {
              const isCompleted = index < currentStepIndex;
              const isCurrent = index === currentStepIndex;

              return (
                <li key={step.id} className="flex items-center">
                  <button
                    type="button"
                    onClick={() => goToStep(index)}
                    className={cn(
                      'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
                      isCompleted && 'text-primary',
                      isCurrent && 'bg-primary text-primary-foreground',
                      !isCompleted && !isCurrent && 'text-muted-foreground'
                    )}
                    disabled={index > currentStepIndex + 1}
                  >
                    <span
                      className={cn(
                        'flex h-6 w-6 items-center justify-center rounded-full border text-xs',
                        isCompleted && 'border-primary bg-primary text-primary-foreground',
                        isCurrent && 'border-primary-foreground',
                        !isCompleted && !isCurrent && 'border-muted-foreground'
                      )}
                    >
                      {isCompleted ? (
                        <CheckIcon className="h-4 w-4" />
                      ) : (
                        index + 1
                      )}
                    </span>
                    <span className="hidden sm:inline">{step.title}</span>
                  </button>
                  {index < wizardSteps.length - 1 && (
                    <div
                      className={cn(
                        'ml-2 h-px w-8 sm:ml-4 sm:w-12',
                        index < currentStepIndex ? 'bg-primary' : 'bg-muted'
                      )}
                    />
                  )}
                </li>
              );
            })}
          </ol>
        </nav>

        {/* Step Content */}
        <Card>
          <CardContent className="p-6">
            {currentStep.id === 'products' && (
              <Step1Products products={products} loading={productsLoading} />
            )}
            {currentStep.id === 'designs' && (
              <Step2Designs
                designs={designs}
                loading={designsLoading}
                onOpenDesignPicker={handleOpenDesignPicker}
              />
            )}
            {currentStep.id === 'shipping' && <Step3Shipping />}
            {currentStep.id === 'payment' && <Step4Payment />}
          </CardContent>
        </Card>

        {/* Navigation Buttons */}
        <div className="flex items-center justify-between">
          <Button
            type="button"
            variant="outline"
            onClick={isFirstStep ? onCancel : handleBack}
          >
            <ChevronLeftIcon className="mr-2 h-4 w-4" />
            {isFirstStep ? 'Cancel' : 'Back'}
          </Button>

          {isLastStep ? (
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Creating Order...' : 'Create Order'}
            </Button>
          ) : (
            <Button type="button" onClick={handleNext}>
              Next
              <ChevronRightIcon className="ml-2 h-4 w-4" />
            </Button>
          )}
        </div>
      </form>

      {/* Design Picker Modal - simplified inline version */}
      {designPickerOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
          <div className="max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-lg border bg-background p-6">
            <h3 className="mb-4 text-lg font-semibold">Select a Design</h3>
            <div className="grid grid-cols-3 gap-4">
              {designs.map((design) => (
                <button
                  key={design.id}
                  type="button"
                  onClick={() => handleSelectDesign(design.id)}
                  className="overflow-hidden rounded-lg border p-2 transition-colors hover:border-primary"
                >
                  <img
                    src={design.thumbnail}
                    alt={design.name}
                    className="aspect-square w-full object-cover"
                  />
                  <p className="mt-2 truncate text-sm font-medium">{design.name}</p>
                </button>
              ))}
            </div>
            <div className="mt-4 flex justify-end">
              <Button
                type="button"
                variant="outline"
                onClick={() => setDesignPickerOpen(false)}
              >
                Cancel
              </Button>
            </div>
          </div>
        </div>
      )}
    </FormProvider>
  );
}

import { z } from 'zod';

// Step 1: Products Schema
export const productSelectionSchema = z.object({
  items: z
    .array(
      z.object({
        productId: z.string().min(1, 'Product is required'),
        variantId: z.string().optional(),
        quantity: z.number().min(1, 'Quantity must be at least 1'),
        price: z.number().min(0),
      })
    )
    .min(1, 'At least one product is required'),
});

export type ProductSelectionData = z.infer<typeof productSelectionSchema>;

// Step 2: Designs Schema
export const designAssignmentSchema = z.object({
  assignments: z.array(
    z.object({
      itemIndex: z.number(),
      designId: z.string().optional(),
      designUrl: z.string().optional(),
      placement: z.string().optional(),
    })
  ),
});

export type DesignAssignmentData = z.infer<typeof designAssignmentSchema>;

// Step 3: Shipping Schema
export const shippingSchema = z.object({
  shippingAddress: z.object({
    firstName: z.string().min(1, 'First name is required'),
    lastName: z.string().min(1, 'Last name is required'),
    company: z.string().optional(),
    address1: z.string().min(1, 'Address is required'),
    address2: z.string().optional(),
    city: z.string().min(1, 'City is required'),
    state: z.string().min(1, 'State is required'),
    postalCode: z.string().min(1, 'Postal code is required'),
    country: z.string().min(1, 'Country is required'),
    phone: z.string().optional(),
  }),
  billingAddress: z.object({
    firstName: z.string().min(1, 'First name is required'),
    lastName: z.string().min(1, 'Last name is required'),
    company: z.string().optional(),
    address1: z.string().min(1, 'Address is required'),
    address2: z.string().optional(),
    city: z.string().min(1, 'City is required'),
    state: z.string().min(1, 'State is required'),
    postalCode: z.string().min(1, 'Postal code is required'),
    country: z.string().min(1, 'Country is required'),
    phone: z.string().optional(),
  }),
  sameAsBilling: z.boolean().default(true),
  shippingMethod: z.string().min(1, 'Shipping method is required'),
});

export type ShippingData = z.infer<typeof shippingSchema>;

// Step 4: Payment Schema
export const paymentSchema = z.object({
  paymentMethod: z.enum(['card', 'invoice', 'cod']),
  notes: z.string().optional(),
});

export type PaymentData = z.infer<typeof paymentSchema>;

// Combined Order Data
export const orderWizardSchema = productSelectionSchema
  .merge(designAssignmentSchema)
  .merge(shippingSchema)
  .merge(paymentSchema);

export type OrderWizardData = z.infer<typeof orderWizardSchema>;

// Wizard Step type
export type WizardStep = 'products' | 'designs' | 'shipping' | 'payment';

export interface WizardStepConfig {
  id: WizardStep;
  title: string;
  description: string;
}

export const wizardSteps: WizardStepConfig[] = [
  {
    id: 'products',
    title: 'Products',
    description: 'Select products and quantities',
  },
  {
    id: 'designs',
    title: 'Designs',
    description: 'Assign designs to products',
  },
  {
    id: 'shipping',
    title: 'Shipping',
    description: 'Enter shipping information',
  },
  {
    id: 'payment',
    title: 'Payment',
    description: 'Select payment method',
  },
];

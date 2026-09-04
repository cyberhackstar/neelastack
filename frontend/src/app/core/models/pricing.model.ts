export interface PricingRule {
  id: string;
  serviceKey: string;
  baseLow: number;
  baseHigh: number | null;
  complexityFactor: number;
  scaleFactor: number;
  integrationFactor: number;
  urgencyFactor: number;
  active: boolean;
  version: number;
  notes?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PricingRulePayload {
  serviceKey: string;
  baseLow: number;
  baseHigh: number | null;
  complexityFactor: number;
  scaleFactor: number;
  integrationFactor: number;
  urgencyFactor: number;
  active: boolean;
  notes?: string | null;
}

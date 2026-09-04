export interface Faq {
  id: string;
  question: string;
  answer: string;
  displayOrder: number;
}

export interface FaqPayload {
  question: string;
  answer: string;
  displayOrder?: number;
}

export interface Review {
  id: string;
  authorName: string;
  authorTitle?: string;
  rating: number;
  reviewBody: string;
  published: boolean;
  displayOrder: number;
  createdAt: string;
}

export interface ReviewPayload {
  authorName: string;
  authorTitle?: string;
  rating: number;
  reviewBody: string;
  published: boolean;
  displayOrder?: number;
}

export interface ServiceItem {
  id: string;
  title: string;
  slug: string;
  summary: string;
  description?: string;
  icon?: string;
  startingPrice?: string;
  displayOrder: number;
  published?: boolean;
  faqs?: Faq[];
}

export interface ServicePayload {
  title: string;
  slug: string;
  summary: string;
  description?: string;
  icon?: string;
  startingPrice?: string;
  displayOrder?: number;
  published: boolean;
}

/** Programmatic SEO silo landing page — one tech-stack/engagement combination. */
export interface TechStackPage {
  id: string;
  slug: string;
  h1Title: string;
  metaTitle: string;
  metaDescription: string;
  intro: string;
  bodyContent: string;
  primaryStack: string;
  secondaryStack?: string;
  targetIndustry?: string;
  useCases: string[];
  startingPrice?: string;
  displayOrder: number;
  published: boolean;
}

export interface TechStackPagePayload {
  slug: string;
  h1Title: string;
  metaTitle: string;
  metaDescription: string;
  intro: string;
  bodyContent: string;
  primaryStack: string;
  secondaryStack?: string;
  targetIndustry?: string;
  useCases?: string[];
  startingPrice?: string;
  displayOrder?: number;
  published: boolean;
}

export interface Project {
  id: string;
  title: string;
  slug: string;
  summary: string;
  problemStatement?: string;
  solution?: string;
  outcome?: string;
  coverImageUrl?: string;
  techStack: string[];
  liveUrl?: string;
  repoUrl?: string;
  featured: boolean;
  published?: boolean;
  reviews?: Review[];
  averageRating?: number | null;
  reviewCount?: number;
}

export interface ProjectPayload {
  title: string;
  slug: string;
  summary: string;
  problemStatement?: string;
  solution?: string;
  outcome?: string;
  coverImageUrl?: string;
  techStack: string[];
  liveUrl?: string;
  repoUrl?: string;
  featured: boolean;
  published: boolean;
  displayOrder?: number;
}

export interface BlogPostSummary {
  id: string;
  title: string;
  slug: string;
  excerpt: string;
  coverImageUrl?: string;
  authorName?: string;
  category?: string;
  tags: string[];
  published?: boolean;
  publishedAt: string;
}

export interface BlogPost extends BlogPostSummary {
  content: string;
  metaTitle: string;
  metaDescription: string;
}

export interface BlogPostPayload {
  title: string;
  slug: string;
  excerpt: string;
  content: string;
  coverImageUrl?: string;
  authorName?: string;
  category?: string;
  tags: string[];
  metaTitle: string;
  metaDescription: string;
  published: boolean;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
}

export type InquiryStatus = 'NEW' | 'CONTACTED' | 'QUOTED' | 'WON' | 'LOST';
export type InquiryIntent = 'BUILD' | 'FIX' | 'MODERNIZE' | 'AUDIT' | 'GENERAL';
export type LeadTier = 'HOT' | 'WARM' | 'NURTURE';

export interface InquiryPayload {
  name: string;
  email: string;
  phone?: string;
  company?: string;
  projectType?: string;
  budgetRange?: string;
  message: string;
}

export interface Estimate {
  low: number | null;
  high: number | null;
  currency: string;
  disclaimer: string;
}

export interface Inquiry extends InquiryPayload {
  id: string;
  status: InquiryStatus;
  intent?: InquiryIntent;
  leadScore?: number;
  leadTier?: LeadTier;
  estimate?: Estimate | null;
  createdAt: string;
  /** Module 2: instant-booking link, present only for Tier-1 (HOT) leads when
   *  the feature is configured server-side. See LeadScoringService#isTierOne. */
  bookingUrl?: string | null;
}

export interface EstimatorPayload {
  intent: InquiryIntent;
  projectType?: string;
  existingSystem?: string;
  scopeDetails?: string;
  usersScale?: string;
  integrations?: string[];
  timeline?: string;
  urgency?: string;
  budgetRange?: string;
  name: string;
  email: string;
  phone?: string;
  company?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  referrer?: string;
  landingPage?: string;
}

export interface ArchitectureReviewPayload {
  name: string;
  email: string;
  phone?: string;
  company?: string;
  applicationUrl?: string;
  currentStack: string;
  primaryConcerns?: string[];
  notes?: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  referrer?: string;
  landingPage?: string;
}

export interface EstimatorResponse {
  inquiry: Inquiry;
  estimate: Estimate;
}

// ---- Module 1: Instant Architecture Risk Score (/audit-preview) ----

export interface AuditPreviewPayload {
  techStack: string[];
  bottlenecks: string[];
}

export interface AuditPreviewResult {
  riskScore: number;
  riskLevel: 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL';
  teaserFindings: string[];
  lockedFindingsCount: number;
  disclaimer: string;
}

export interface AuditUnlockPayload extends AuditPreviewPayload {
  name: string;
  email: string;
  phone?: string;
  company: string;
  utmSource?: string;
  utmMedium?: string;
  utmCampaign?: string;
  referrer?: string;
  landingPage?: string;
}

export interface AuditFinding {
  title: string;
  severity: string;
  summary: string;
  recommendation: string;
}

export interface AuditUnlockResult {
  inquiry: Inquiry;
  riskScore: number;
  riskLevel: string;
  findings: AuditFinding[];
  disclaimer: string;
}

// ---- Module 4: post-invoice testimonial loop (/testimonial/:token) ----

export type TestimonialRequestStatus = 'PENDING' | 'SUBMITTED' | 'DECLINED' | 'EXPIRED';

export interface TestimonialRequestPublic {
  clientName: string;
  projectTitle?: string | null;
  status: TestimonialRequestStatus;
}

export interface TestimonialSubmission {
  authorTitle?: string;
  rating: number;
  reviewBody: string;
  videoUrl?: string;
}

export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';

export interface QuotationLineItem {
  description: string;
  amount: number;
}

export interface QuotationPayload {
  inquiryId: string;
  title: string;
  scopeSummary?: string;
  lineItems: QuotationLineItem[];
  currency?: string;
  validUntil?: string;
  notes?: string;
}

export interface Quotation {
  id: string;
  inquiryId: string;
  title: string;
  scopeSummary?: string;
  lineItems: QuotationLineItem[];
  totalAmount: number;
  currency: string;
  status: QuotationStatus;
  validUntil?: string;
  notes?: string;
  responseReason?: string;
  respondedAt?: string;
  sentAt?: string;
  viewCount?: number;
  lastViewedAt?: string;
  createdAt: string;
}

export type EngagementStatus = 'ONBOARDING' | 'IN_PROGRESS' | 'REVIEW' | 'COMPLETED' | 'ON_HOLD';

export interface Engagement {
  id: string;
  clientId: string;
  clientName: string;
  clientEmail: string;
  title: string;
  description?: string;
  status: EngagementStatus;
  startDate?: string;
  targetEndDate?: string;
  createdAt: string;
}

export interface EngagementPayload {
  clientEmail: string;
  inquiryId?: string;
  title: string;
  description?: string;
  startDate?: string;
  targetEndDate?: string;
}

export type MilestoneStatus = 'PENDING' | 'IN_PROGRESS' | 'DONE';

export interface Milestone {
  id: string;
  engagementId: string;
  title: string;
  description?: string;
  status: MilestoneStatus;
  dueDate?: string;
  displayOrder: number;
}

export interface MilestonePayload {
  title: string;
  description?: string;
  dueDate?: string;
  displayOrder?: number;
}

export interface ProjectFile {
  id: string;
  fileName: string;
  fileUrl: string;
  fileType?: string;
  fileSizeBytes?: number;
  uploadedByName: string;
  createdAt: string;
}

export type InvoiceStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED';

export interface Invoice {
  id: string;
  engagementId: string;
  invoiceNumber: string;
  description: string;
  amount: number;
  currency: string;
  status: InvoiceStatus;
  dueDate?: string;
  paidAt?: string;
  createdAt: string;
}

export interface InvoicePayload {
  engagementId: string;
  description: string;
  amount: number;
  currency?: string;
  dueDate?: string;
}

export interface CheckoutOrder {
  razorpayOrderId: string;
  razorpayKeyId: string;
  amountInPaise: number;
  currency: string;
  invoiceNumber: string;
  description: string;
}

export interface AnalyticsSummary {
  totalInquiries: number;
  newInquiries: number;
  totalEngagements: number;
  engagementsByStatus: Record<string, number>;
  totalRevenueCollected: number;
  pendingInvoiceAmount: number;
  totalBlogPosts: number;
  totalProjects: number;
  hotLeads: number;
  openPipelineValue: number;
  wonPipelineValue: number;
  recentInquiries: Inquiry[];
}

/** Mirrors backend SalesIntelligenceDto. Nullable fields mean "no data yet", not zero. */
export interface SalesIntelligence {
  openPipelineValue: number;
  weightedPipelineValue: number;
  wonRevenue: number;
  winRatePercent: number | null;
  averageDealSize: number | null;
  averageSalesCycleDays: number | null;
  averageTimeToFirstViewHours: number | null;
  unviewedProposals: number;
  viewedAwaitingResponse: number;
}

export type AttributionDimension = 'SOURCE' | 'MEDIUM' | 'CAMPAIGN' | 'LANDING_PAGE';

/** Mirrors backend AttributionBreakdownDto. */
export interface AttributionBreakdown {
  dimension: AttributionDimension;
  value: string;
  leadCount: number;
  quotedCount: number;
  wonCount: number;
  wonRevenue: number;
  conversionRatePercent: number | null;
}

/** Mirrors backend RevenueBySourceDto (kept for anything still on the source-only endpoint). */
export interface RevenueBySource {
  source: string;
  leadCount: number;
  quotedCount: number;
  wonCount: number;
  wonRevenue: number;
  conversionRatePercent: number | null;
}

export type FollowUpReason = 'UNVIEWED_REMINDER' | 'VIEWED_NO_RESPONSE';

/** Mirrors backend FollowUpTaskDto. */
export interface FollowUpTask {
  quotationId: string;
  inquiryId: string | null;
  clientName: string | null;
  clientEmail: string | null;
  quotationTitle: string;
  totalAmount: number;
  reason: FollowUpReason;
  sentAt: string | null;
  lastViewedAt: string | null;
  daysSinceLastActivity: number;
}

export interface CaseStudyProof {
  title: string;
  slug: string;
  summary?: string;
  coverImageUrl?: string;
  keyMetrics: string[];
  averageRating?: number;
  reviewCount?: number;
}

export interface PublicQuotation {
  title: string;
  scopeSummary?: string;
  lineItems: QuotationLineItem[];
  totalAmount: number;
  currency: string;
  status: QuotationStatus;
  validUntil?: string;
  clientName: string;
  /** Module 3: contextual social proof for the quoted service — absent when no
   *  published, matching case study exists. Never a generic fallback. */
  relatedCaseStudy?: CaseStudyProof | null;
}

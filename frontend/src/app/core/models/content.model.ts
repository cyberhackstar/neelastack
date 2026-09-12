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
  serviceCategories?: string[];
  keyMetrics?: string[];
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
  serviceCategories?: string[];
  keyMetrics?: string[];
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
  // Only used when clientEmail has no existing account yet — names the invited placeholder
  // account. Falls back to the linked inquiry's name, then the email's local part, if omitted.
  clientName?: string;
  inquiryId?: string;
  title: string;
  description?: string;
  startDate?: string;
  targetEndDate?: string;
}

export type MilestoneStatus = 'PENDING' | 'IN_PROGRESS' | 'AWAITING_APPROVAL' | 'CHANGES_REQUESTED' | 'DONE';

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

export type MessageSenderRole = 'CLIENT' | 'STAFF';

// Attachment summaries on a message omit fileUrl/uploadedByName -- the signed link is
// resolved on demand via GET /engagements/{id}/files using this id, the same way the
// document center does, rather than re-signing every attachment on every message load.
export interface ProjectMessageAttachment {
  id: string;
  fileName: string;
  fileType?: string;
  fileSizeBytes?: number;
  createdAt: string;
}

export interface ProjectMessage {
  id: string;
  engagementId: string;
  senderId: string;
  senderName: string;
  senderRole: MessageSenderRole;
  body: string;
  attachment?: ProjectMessageAttachment;
  createdAt: string;
}

export interface ProjectMessagePayload {
  body: string;
  attachmentFileId?: string;
}

export interface UnreadCount {
  unreadCount: number;
}

export type ProjectActivityType =
  | 'ENGAGEMENT_CREATED'
  | 'ENGAGEMENT_STATUS_CHANGED'
  | 'MILESTONE_CREATED'
  | 'MILESTONE_STATUS_CHANGED'
  | 'MILESTONE_APPROVED'
  | 'MILESTONE_CHANGES_REQUESTED'
  | 'TASK_CREATED'
  | 'TASK_STATUS_CHANGED'
  | 'TASK_ASSIGNED'
  | 'FILE_UPLOADED'
  | 'FILE_DELETED'
  | 'INVOICE_CREATED'
  | 'INVOICE_PAID';

export interface ProjectActivity {
  id: string;
  engagementId: string;
  actorName?: string;
  actorRole?: string;
  activityType: ProjectActivityType;
  summary: string;
  createdAt: string;
}

export type MilestoneApprovalAction = 'APPROVED' | 'CHANGES_REQUESTED';

export interface MilestoneApproval {
  id: string;
  milestoneId: string;
  engagementId: string;
  action: MilestoneApprovalAction;
  comment?: string;
  actorName: string;
  createdAt: string;
}

export type ChangeRequestPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type ChangeRequestStatus = 'SUBMITTED' | 'QUOTED' | 'ACCEPTED' | 'DECLINED' | 'COMPLETED';

export interface ChangeRequest {
  id: string;
  engagementId: string;
  requestedByName: string;
  title: string;
  description: string;
  priority: ChangeRequestPriority;
  status: ChangeRequestStatus;
  estimatedCost?: number;
  estimatedCostCurrency?: string;
  estimatedTimelineDays?: number;
  attachment?: ProjectMessageAttachment;
  createdAt: string;
}

export interface ChangeRequestCreatePayload {
  title: string;
  description: string;
  priority?: ChangeRequestPriority;
  attachmentFileId?: string;
}

export interface ChangeRequestQuotePayload {
  estimatedCost: number;
  estimatedCostCurrency?: string;
  estimatedTimelineDays?: number;
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

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE';

export interface ProjectTask {
  id: string;
  milestoneId: string;
  engagementId: string;
  title: string;
  description?: string;
  status: TaskStatus;
  dueDate?: string;
  clientActionRequired: boolean;
  assigneeName?: string;
  displayOrder: number;
  createdAt: string;
}

export interface ProjectTaskPayload {
  title: string;
  description?: string;
  dueDate?: string;
  clientActionRequired?: boolean;
  displayOrder?: number;
}

export interface StaffSummary {
  id: string;
  fullName: string;
  email: string;
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

// ---------------- Notification engine ----------------

export type NotificationType =
  | 'MILESTONE_READY_FOR_APPROVAL' | 'MILESTONE_APPROVED' | 'MILESTONE_CHANGES_REQUESTED'
  | 'TASK_ACTION_REQUIRED' | 'INVOICE_CREATED' | 'INVOICE_DUE_SOON' | 'INVOICE_OVERDUE' | 'INVOICE_PAID'
  | 'UPI_PAYMENT_SUBMITTED' | 'UPI_PAYMENT_VERIFIED' | 'UPI_PAYMENT_REJECTED'
  | 'PAYMENT_SCHEDULE_INSTALLMENT_DUE' | 'CHANGE_REQUEST_QUOTED' | 'PROJECT_MESSAGE_RECEIVED' | 'GENERAL';

export type NotificationPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

/** Mirrors backend NotificationDto. */
export interface AppNotification {
  id: string;
  engagementId: string | null;
  type: NotificationType;
  priority: NotificationPriority;
  title: string;
  body: string | null;
  relatedEntityType: string | null;
  relatedEntityId: string | null;
  deepLink: string | null;
  read: boolean;
  createdAt: string;
}


export type AdminRole = 'ADMIN' | 'SUPERADMIN';
export interface AdminStaff {
  id: string; fullName: string; email: string; role: AdminRole; enabled: boolean;
  mfaEnabled: boolean; mustChangePassword: boolean; createdAt: string | null;
}

// ---------------- Direct UPI QR payments ----------------

export interface UpiPaymentMethod {
  id: string;
  label: string;
  vpa: string | null;
  payeeName: string | null;
  qrImageUrl: string;
  active: boolean;
  displayOrder: number;
}

export interface UpiPaymentMethodPayload {
  label: string;
  vpa?: string;
  payeeName?: string;
  displayOrder?: number;
}

export type UpiSubmissionStatus = 'PENDING_VERIFICATION' | 'VERIFIED' | 'REJECTED';

export interface UpiSubmission {
  id: string;
  invoiceId: string;
  invoiceNumber: string;
  engagementId: string;
  methodLabel: string;
  submittedByName: string;
  utrReference: string;
  payerUpiId: string | null;
  amountClaimed: number;
  screenshotUrl: string | null;
  status: UpiSubmissionStatus;
  adminNote: string | null;
  createdAt: string;
  verifiedAt: string | null;
}

export interface UpiSubmissionPayload {
  upiMethodId: string;
  utrReference: string;
  payerUpiId?: string;
  amountClaimed: number;
}

// ---------------- Payment schedules ----------------

export type InstallmentStatus = 'PENDING' | 'INVOICED' | 'PAID' | 'OVERDUE';

export interface PaymentScheduleInstallment {
  id: string;
  label: string;
  amount: number;
  percentage: number | null;
  dueDate: string | null;
  status: InstallmentStatus;
  invoiceId: string | null;
  invoiceNumber: string | null;
  displayOrder: number;
}

export interface PaymentSchedule {
  id: string;
  engagementId: string;
  totalAmount: number;
  currency: string;
  paidAmount: number;
  outstandingAmount: number;
  installments: PaymentScheduleInstallment[];
}

export interface PaymentScheduleInstallmentPayload {
  label: string;
  amount: number;
  percentage?: number;
  dueDate?: string;
  displayOrder?: number;
}

export interface PaymentSchedulePayload {
  engagementId: string;
  totalAmount: number;
  currency?: string;
  installments: PaymentScheduleInstallmentPayload[];
}

// ---------------- Project health & action center ----------------

export type ProjectHealthStatus = 'HEALTHY' | 'AT_RISK' | 'CRITICAL';

export interface ProjectHealth {
  engagementId: string;
  status: ProjectHealthStatus;
  reasons: string[];
}

export interface ProjectOperationsSummary {
  atRiskProjects: number;
  criticalProjects: number;
  awaitingClientProjects: number;
  overdueTasks: number;
  overdueInvoices: number;
  pendingMilestoneApprovals: number;
  pendingUpiVerifications: number;
  deadlinesThisWeek: number;
}

export interface ActionItem {
  kind: string;
  relatedId: string;
  title: string;
  description: string | null;
  dueDate: string | null;
  deepLink: string | null;
}

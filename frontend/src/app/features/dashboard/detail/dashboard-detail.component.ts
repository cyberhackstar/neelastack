import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { InvoiceService } from '../../../core/services/invoice.service';
import { RazorpayCheckoutService } from '../../../core/services/razorpay-checkout.service';
import { SeoService } from '../../../core/services/seo.service';
import { StaffService } from '../../../core/services/staff.service';
import {
  ChangeRequest,
  ChangeRequestCreatePayload,
  ChangeRequestPriority,
  Engagement,
  EngagementStatus,
  Invoice,
  Milestone,
  MilestoneApproval,
  MilestoneStatus,
  ProjectActivity,
  ProjectFile,
  ProjectMessage,
  ProjectTask,
  ProjectTaskPayload,
  StaffSummary,
  TaskStatus,
} from '../../../core/models/content.model';
import { isAdminRole } from '../../../core/models/user.model';
import { ProjectWorkspaceComponent } from '../workspace/project-workspace.component';
import { ProjectHealthService } from '../../../core/services/project-health.service';
import { PaymentScheduleService } from '../../../core/services/payment-schedule.service';
import { UpiPaymentService } from '../../../core/services/upi-payment.service';
import { ActionItem, PaymentSchedule, ProjectHealth, UpiPaymentMethod, UpiSubmission } from '../../../core/models/content.model';

@Component({
  selector: 'app-dashboard-detail',
  standalone: true,
  imports: [RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, FormsModule, ProjectWorkspaceComponent],
  templateUrl: './dashboard-detail.component.html',
  styleUrl: './dashboard-detail.component.scss',
})
export class DashboardDetailComponent implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private engagementService = inject(EngagementService);
  private seo = inject(SeoService);
  private authService = inject(AuthService);
  private invoiceService = inject(InvoiceService);
  private razorpayCheckout = inject(RazorpayCheckoutService);
  private staffService = inject(StaffService);
  private healthService = inject(ProjectHealthService);
  private paymentScheduleService = inject(PaymentScheduleService);
  private upiPaymentService = inject(UpiPaymentService);
  private router = inject(Router);
  private fb = inject(FormBuilder);

  engagement = signal<Engagement | null>(null);
  milestones = signal<Milestone[]>([]);
  files = signal<ProjectFile[]>([]);
  invoices = signal<Invoice[]>([]);
  messages = signal<ProjectMessage[]>([]);
  activity = signal<ProjectActivity[]>([]);
  tasks = signal<ProjectTask[]>([]);
  staff = signal<StaffSummary[]>([]);
  approvals = signal<MilestoneApproval[]>([]);
  changeRequests = signal<ChangeRequest[]>([]);
  projectHealth = signal<ProjectHealth | null>(null);
  actionItems = signal<ActionItem[]>([]);
  paymentSchedule = signal<PaymentSchedule | null>(null);
  upiMethods = signal<UpiPaymentMethod[]>([]);
  upiSubmissions = signal<Record<string, UpiSubmission[]>>({});
  upiOpenInvoiceId = signal<string | null>(null);
  upiSubmittingInvoiceId = signal<string | null>(null);
  upiErrors = signal<Record<string, string>>({});
  upiMethodId = signal<Record<string, string>>({});
  upiUtr = signal<Record<string, string>>({});
  upiAmount = signal<Record<string, number>>({});
  upiPayerId = signal<Record<string, string>>({});
  upiScreenshot = signal<Record<string, File | null>>({});

  uploading = signal(false);
  uploadError = signal<string | null>(null);
  addingMilestone = signal(false);
  creatingInvoice = signal(false);
  payingInvoiceId = signal<string | null>(null);
  paymentError = signal<string | null>(null);
  sendingMessage = signal(false);
  messageError = signal<string | null>(null);
  selectedAttachmentId = signal<string>('');
  addingTaskForMilestone = signal<string | null>(null);
  requestingChangesFor = signal<string | null>(null);
  submittingApprovalFor = signal<string | null>(null);
  submittingChangeRequest = signal(false);
  changeRequestError = signal<string | null>(null);
  quotingChangeRequestFor = signal<string | null>(null);
  changeRequestActionFor = signal<string | null>(null);

  private messagePollHandle?: ReturnType<typeof setInterval>;

  readonly engagementStatuses: EngagementStatus[] = ['ONBOARDING', 'IN_PROGRESS', 'REVIEW', 'COMPLETED', 'ON_HOLD'];
  readonly milestoneStatuses: MilestoneStatus[] = ['PENDING', 'IN_PROGRESS', 'AWAITING_APPROVAL', 'CHANGES_REQUESTED', 'DONE'];
  readonly taskStatuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE'];
  readonly changeRequestPriorities: ChangeRequestPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

  milestoneForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: [''],
    dueDate: [''],
  });

  invoiceForm = this.fb.nonNullable.group({
    description: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(1)]],
    dueDate: [''],
  });

  messageForm = this.fb.nonNullable.group({
    body: ['', [Validators.required, Validators.maxLength(5000)]],
  });

  changeRequestForm = this.fb.nonNullable.group({
    title: ['', Validators.required],
    description: ['', Validators.required],
    priority: ['MEDIUM' as ChangeRequestPriority],
  });

  get isAdmin(): boolean {
    return isAdminRole(this.authService.currentUser()?.role);
  }

  private engagementId!: string;

  ngOnInit(): void {
    this.seo.update({ title: 'Project', description: 'Your Neelastack project dashboard.', noindex: true });
    this.engagementId = this.route.snapshot.paramMap.get('id')!;
    this.load();
    this.loadMessages();

    // Lightweight near-real-time feel without standing up WebSocket infrastructure the
    // rest of this app doesn't have yet: poll for new messages while the thread is open.
    // The thread being visibly open for the whole poll cycle is why every refresh also
    // marks it read, same as loadMessages() does on the initial load.
    this.messagePollHandle = setInterval(() => this.loadMessages(), 15000);
  }

  ngOnDestroy(): void {
    if (this.messagePollHandle) clearInterval(this.messagePollHandle);
  }

  private load(): void {
    this.engagementService.getEngagement(this.engagementId).subscribe((e) => this.engagement.set(e));
    this.healthService.getHealth(this.engagementId).subscribe({ next: (h) => this.projectHealth.set(h) });
    this.healthService.getActionItems(this.engagementId).subscribe({ next: (items) => this.actionItems.set(items) });
    this.paymentScheduleService.getForEngagement(this.engagementId).subscribe({ next: (s) => this.paymentSchedule.set(s), error: () => this.paymentSchedule.set(null) });
    if (!this.isAdmin) this.upiPaymentService.listActiveMethods().subscribe({ next: (methods) => this.upiMethods.set(methods) });
    this.engagementService.getMilestones(this.engagementId).subscribe((m) => this.milestones.set(m));
    this.engagementService.getFiles(this.engagementId).subscribe((f) => this.files.set(f));
    this.invoiceService.listForEngagement(this.engagementId).subscribe((inv) => this.invoices.set(inv));

    const activity$ = this.isAdmin
      ? this.engagementService.getActivityAsAdmin(this.engagementId)
      : this.engagementService.getActivity(this.engagementId);
    activity$.subscribe((a) => this.activity.set(a));

    const tasks$ = this.isAdmin
      ? this.engagementService.getTasksAsAdmin(this.engagementId)
      : this.engagementService.getTasks(this.engagementId);
    tasks$.subscribe((t) => this.tasks.set(t));

    if (this.isAdmin) {
      this.staffService.list().subscribe((s) => this.staff.set(s));
    }

    const approvals$ = this.isAdmin
      ? this.engagementService.getMilestoneApprovalsAsAdmin(this.engagementId)
      : this.engagementService.getMilestoneApprovals(this.engagementId);
    approvals$.subscribe((a) => this.approvals.set(a));

    const changeRequests$ = this.isAdmin
      ? this.engagementService.getChangeRequestsAsAdmin(this.engagementId)
      : this.engagementService.getChangeRequests(this.engagementId);
    changeRequests$.subscribe((c) => this.changeRequests.set(c));
  }

  private loadMessages(): void {
    const list$ = this.isAdmin
      ? this.engagementService.getMessagesAsAdmin(this.engagementId)
      : this.engagementService.getMessages(this.engagementId);

    list$.subscribe((msgs) => {
      this.messages.set(msgs);
      // Opening/refreshing the thread while it's on screen counts as reading it — mirrors
      // how a real chat client marks a thread read the moment it's the active view.
      if (msgs.length > 0) {
        const markRead$ = this.isAdmin
          ? this.engagementService.markMessagesReadAsAdmin(this.engagementId)
          : this.engagementService.markMessagesRead(this.engagementId);
        markRead$.subscribe();
      }
    });
  }

  sendMessage(): void {
    if (this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }
    this.sendingMessage.set(true);
    this.messageError.set(null);

    const payload = {
      body: this.messageForm.getRawValue().body,
      attachmentFileId: this.selectedAttachmentId() || undefined,
    };
    const send$ = this.isAdmin
      ? this.engagementService.sendMessageAsAdmin(this.engagementId, payload)
      : this.engagementService.sendMessage(this.engagementId, payload);

    send$.subscribe({
      next: (message) => {
        this.sendingMessage.set(false);
        this.messages.set([...this.messages(), message]);
        this.messageForm.reset({ body: '' });
        this.selectedAttachmentId.set('');
      },
      error: (err) => {
        this.sendingMessage.set(false);
        this.messageError.set(err?.error?.message ?? 'Could not send that message. Please try again.');
      },
    });
  }

  updateEngagementStatus(status: EngagementStatus): void {
    this.engagementService.updateEngagementStatus(this.engagementId, status).subscribe((e) => this.engagement.set(e));
  }

  updateMilestoneStatus(milestoneId: string, status: MilestoneStatus): void {
    this.engagementService.updateMilestoneStatus(milestoneId, status).subscribe((updated) => {
      this.milestones.set(this.milestones().map((m) => (m.id === updated.id ? updated : m)));
    });
  }

  addMilestone(): void {
    if (this.milestoneForm.invalid) {
      this.milestoneForm.markAllAsTouched();
      return;
    }
    this.addingMilestone.set(true);
    this.engagementService.addMilestone(this.engagementId, this.milestoneForm.getRawValue()).subscribe({
      next: (milestone) => {
        this.addingMilestone.set(false);
        this.milestones.set([...this.milestones(), milestone]);
        this.milestoneForm.reset();
      },
      error: () => this.addingMilestone.set(false),
    });
  }

  openAction(item: ActionItem): void {
    if (!item.deepLink) return;
    if (item.deepLink.startsWith('http')) window.location.assign(item.deepLink); else this.router.navigateByUrl(item.deepLink);
  }

  toggleUpi(invoice: Invoice): void {
    if (this.upiOpenInvoiceId() === invoice.id) { this.upiOpenInvoiceId.set(null); return; }
    this.upiOpenInvoiceId.set(invoice.id);
    if (!this.upiAmount()[invoice.id]) this.upiAmount.set({ ...this.upiAmount(), [invoice.id]: Number(invoice.amount) });
    this.upiPaymentService.listSubmissionsForInvoice(invoice.id).subscribe({ next: (items) => this.upiSubmissions.set({ ...this.upiSubmissions(), [invoice.id]: items }) });
  }

  onUpiScreenshot(invoiceId: string, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    if (file && file.size > 5 * 1024 * 1024) { this.upiErrors.set({ ...this.upiErrors(), [invoiceId]: 'Payment proof must be 5 MB or smaller.' }); return; }
    this.upiScreenshot.set({ ...this.upiScreenshot(), [invoiceId]: file });
  }

  setUpiMethod(invoiceId: string, methodId: string): void {
    this.upiMethodId.set({ ...this.upiMethodId(), [invoiceId]: methodId });
  }

  setUpiUtr(invoiceId: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.upiUtr.set({ ...this.upiUtr(), [invoiceId]: value });
  }

  setUpiAmount(invoiceId: string, event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    this.upiAmount.set({ ...this.upiAmount(), [invoiceId]: value });
  }

  setUpiPayerId(invoiceId: string, event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.upiPayerId.set({ ...this.upiPayerId(), [invoiceId]: value });
  }
  submitUpi(invoice: Invoice): void {
    const utr = (this.upiUtr()[invoice.id] ?? '').trim(); const methodId = this.upiMethodId()[invoice.id]; const amount = Number(this.upiAmount()[invoice.id] ?? invoice.amount);
    if (!methodId || !utr || !Number.isFinite(amount) || amount <= 0) { this.upiErrors.set({ ...this.upiErrors(), [invoice.id]: 'Choose a UPI method, enter the UTR and a valid amount.' }); return; }
    this.upiSubmittingInvoiceId.set(invoice.id);
    this.upiPaymentService.submitPayment(invoice.id, { upiMethodId: methodId, utrReference: utr, payerUpiId: (this.upiPayerId()[invoice.id] ?? '').trim() || undefined, amountClaimed: amount }, this.upiScreenshot()[invoice.id]).subscribe({
      next: (submission) => {
        this.upiSubmittingInvoiceId.set(null); this.upiSubmissions.set({ ...this.upiSubmissions(), [invoice.id]: [submission, ...(this.upiSubmissions()[invoice.id] ?? [])] });
        this.upiErrors.set({ ...this.upiErrors(), [invoice.id]: '' });
      },
      error: (e) => { this.upiSubmittingInvoiceId.set(null); this.upiErrors.set({ ...this.upiErrors(), [invoice.id]: e?.error?.message ?? 'Could not submit UPI payment.' }); }
    });
  }

  // ---- Project health ----------------------------------------------------------------
  get milestoneProgressPercent(): number {
    const all = this.milestones();
    if (all.length === 0) return 0;
    const done = all.filter((m) => m.status === 'DONE').length;
    return Math.round((done / all.length) * 100);
  }

  get nextMilestone(): Milestone | undefined {
    return [...this.milestones()]
      .sort((a, b) => a.displayOrder - b.displayOrder)
      .find((m) => m.status !== 'DONE');
  }

  // ---- Tasks (nested under milestones) ----
  tasksForMilestone(milestoneId: string): ProjectTask[] {
    return this.tasks().filter((t) => t.milestoneId === milestoneId);
  }

  addTask(milestoneId: string, titleInput: HTMLInputElement, dueDateInput: HTMLInputElement, clientActionInput: HTMLInputElement): void {
    const title = titleInput.value.trim();
    if (!title) return;

    const payload: ProjectTaskPayload = {
      title,
      dueDate: dueDateInput.value || undefined,
      clientActionRequired: clientActionInput.checked,
    };

    this.addingTaskForMilestone.set(milestoneId);
    this.engagementService.addTask(milestoneId, payload).subscribe({
      next: (task) => {
        this.addingTaskForMilestone.set(null);
        this.tasks.set([...this.tasks(), task]);
        titleInput.value = '';
        dueDateInput.value = '';
        clientActionInput.checked = false;
      },
      error: () => this.addingTaskForMilestone.set(null),
    });
  }

  updateTaskStatus(taskId: string, status: TaskStatus): void {
    this.engagementService.updateTaskStatus(taskId, status).subscribe((updated) => {
      this.tasks.set(this.tasks().map((t) => (t.id === updated.id ? updated : t)));
    });
  }

  assignTask(taskId: string, assigneeEmail: string): void {
    this.engagementService.assignTask(taskId, assigneeEmail || null).subscribe((updated) => {
      this.tasks.set(this.tasks().map((t) => (t.id === updated.id ? updated : t)));
    });
  }

  // Best-effort reverse lookup so the assignee <select> can be pre-selected by email even
  // though ProjectTask only carries assigneeName (staff email isn't otherwise shown on the
  // client-safe task DTO).
  staffEmailByName(name?: string): string {
    if (!name) return '';
    return this.staff().find((s) => s.fullName === name)?.email ?? '';
  }

  // ---- Client approvals (Section 13) ----
  latestChangeRequestFor(milestoneId: string): MilestoneApproval | undefined {
    return this.approvals()
      .filter((a) => a.milestoneId === milestoneId && a.action === 'CHANGES_REQUESTED')
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0];
  }

  approveMilestone(milestoneId: string): void {
    this.submittingApprovalFor.set(milestoneId);
    this.engagementService.approveMilestone(milestoneId).subscribe({
      next: (updated) => {
        this.submittingApprovalFor.set(null);
        this.milestones.set(this.milestones().map((m) => (m.id === updated.id ? updated : m)));
      },
      error: () => this.submittingApprovalFor.set(null),
    });
  }

  openRequestChanges(milestoneId: string): void {
    this.requestingChangesFor.set(milestoneId);
  }

  cancelRequestChanges(): void {
    this.requestingChangesFor.set(null);
  }

  submitRequestChanges(milestoneId: string, commentInput: HTMLTextAreaElement): void {
    const comment = commentInput.value.trim();
    if (!comment) return;

    this.submittingApprovalFor.set(milestoneId);
    this.engagementService.requestMilestoneChanges(milestoneId, comment).subscribe({
      next: (updated) => {
        this.submittingApprovalFor.set(null);
        this.requestingChangesFor.set(null);
        this.milestones.set(this.milestones().map((m) => (m.id === updated.id ? updated : m)));
        // Reflect the new comment immediately without waiting on a full reload -- the real
        // record still comes from the server via MilestoneApprovalService, this is just an
        // optimistic local echo so latestChangeRequestFor() has something to show right away.
        this.approvals.set([
          {
            id: `local-${Date.now()}`,
            milestoneId,
            engagementId: this.engagementId,
            action: 'CHANGES_REQUESTED',
            comment,
            actorName: this.authService.currentUser()?.fullName ?? 'You',
            createdAt: new Date().toISOString(),
          },
          ...this.approvals(),
        ]);
      },
      error: () => this.submittingApprovalFor.set(null),
    });
  }

  // ---- Change requests (Section 14) ----
  submitChangeRequest(): void {
    if (this.changeRequestForm.invalid) {
      this.changeRequestForm.markAllAsTouched();
      return;
    }
    this.submittingChangeRequest.set(true);
    this.changeRequestError.set(null);

    const payload: ChangeRequestCreatePayload = this.changeRequestForm.getRawValue();
    this.engagementService.submitChangeRequest(this.engagementId, payload).subscribe({
      next: (cr) => {
        this.submittingChangeRequest.set(false);
        this.changeRequests.set([cr, ...this.changeRequests()]);
        this.changeRequestForm.reset({ title: '', description: '', priority: 'MEDIUM' });
      },
      error: (err) => {
        this.submittingChangeRequest.set(false);
        this.changeRequestError.set(err?.error?.message ?? 'Could not submit that change request.');
      },
    });
  }

  private replaceChangeRequest(updated: ChangeRequest): void {
    this.changeRequests.set(this.changeRequests().map((c) => (c.id === updated.id ? updated : c)));
  }

  acceptChangeRequest(id: string): void {
    this.changeRequestActionFor.set(id);
    this.engagementService.acceptChangeRequest(id).subscribe({
      next: (updated) => {
        this.changeRequestActionFor.set(null);
        this.replaceChangeRequest(updated);
      },
      error: () => this.changeRequestActionFor.set(null),
    });
  }

  declineChangeRequest(id: string): void {
    this.changeRequestActionFor.set(id);
    this.engagementService.declineChangeRequest(id).subscribe({
      next: (updated) => {
        this.changeRequestActionFor.set(null);
        this.replaceChangeRequest(updated);
      },
      error: () => this.changeRequestActionFor.set(null),
    });
  }

  completeChangeRequest(id: string): void {
    this.changeRequestActionFor.set(id);
    this.engagementService.completeChangeRequest(id).subscribe({
      next: (updated) => {
        this.changeRequestActionFor.set(null);
        this.replaceChangeRequest(updated);
      },
      error: () => this.changeRequestActionFor.set(null),
    });
  }

  openQuoteForm(id: string): void {
    this.quotingChangeRequestFor.set(id);
  }

  cancelQuoteForm(): void {
    this.quotingChangeRequestFor.set(null);
  }

  submitQuote(id: string, costInput: HTMLInputElement, daysInput: HTMLInputElement): void {
    const estimatedCost = Number(costInput.value);
    if (!estimatedCost || estimatedCost <= 0) return;

    this.changeRequestActionFor.set(id);
    this.engagementService
      .quoteChangeRequest(id, {
        estimatedCost,
        estimatedTimelineDays: daysInput.value ? Number(daysInput.value) : undefined,
      })
      .subscribe({
        next: (updated) => {
          this.changeRequestActionFor.set(null);
          this.quotingChangeRequestFor.set(null);
          this.replaceChangeRequest(updated);
        },
        error: () => this.changeRequestActionFor.set(null),
      });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.uploading.set(true);
    this.uploadError.set(null);

    this.engagementService.uploadFile(this.engagementId, file).subscribe({
      next: (uploaded) => {
        this.uploading.set(false);
        this.files.set([uploaded, ...this.files()]);
        input.value = '';
      },
      error: (err) => {
        this.uploading.set(false);
        this.uploadError.set(err?.error?.message ?? 'Upload failed. Please try again.');
        input.value = '';
      },
    });
  }

  deleteFile(fileId: string): void {
    this.engagementService.deleteFile(this.engagementId, fileId).subscribe(() => {
      this.files.set(this.files().filter((f) => f.id !== fileId));
    });
  }

  createInvoice(): void {
    if (this.invoiceForm.invalid) {
      this.invoiceForm.markAllAsTouched();
      return;
    }
    this.creatingInvoice.set(true);
    this.invoiceService
      .createInvoice({ engagementId: this.engagementId, ...this.invoiceForm.getRawValue() })
      .subscribe({
        next: (invoice) => {
          this.creatingInvoice.set(false);
          this.invoices.set([invoice, ...this.invoices()]);
          this.invoiceForm.reset({ description: '', amount: 0, dueDate: '' });
        },
        error: () => this.creatingInvoice.set(false),
      });
  }

  async payInvoice(invoice: Invoice): Promise<void> {
    const user = this.authService.currentUser();
    if (!user) return;

    this.paymentError.set(null);
    this.payingInvoiceId.set(invoice.id);

    try {
      const order = await firstValueFrom(this.invoiceService.createOrder(invoice.id));
      const result = await this.razorpayCheckout.open(order, { name: user.fullName, email: user.email });

      const updated = await firstValueFrom(
        this.invoiceService.verifyPayment(invoice.id, {
          razorpayOrderId: result.razorpay_order_id,
          razorpayPaymentId: result.razorpay_payment_id,
          razorpaySignature: result.razorpay_signature,
        }),
      );

      this.invoices.set(this.invoices().map((i) => (i.id === updated.id ? updated : i)));
    } catch (err: any) {
      this.paymentError.set(err?.error?.message ?? err?.message ?? 'Payment did not complete.');
    } finally {
      this.payingInvoiceId.set(null);
    }
  }

  downloadInvoicePdf(invoice: Invoice): void {
    this.invoiceService.downloadPdf(invoice.id).subscribe((blob) => {
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${invoice.invoiceNumber}.pdf`;
      link.click();
      URL.revokeObjectURL(url);
    });
  }

  // Aligns a message bubble left/right by whether the *role* matches the current viewer.
  // There's no per-user identity carried on the frontend auth session today (only
  // name/email/role), so this treats "all staff" as one side of the conversation and "the
  // client" as the other -- matching the two-party thread the review's mockup describes,
  // rather than pretending to distinguish between individual staff members.
  isOwnMessage(message: ProjectMessage): boolean {
    return this.isAdmin ? message.senderRole === 'STAFF' : message.senderRole === 'CLIENT';
  }

  // Small touch matching the review's own mockup ("Today — You uploaded architecture-v2.pdf")
  // rather than showing the viewer their own full name back at themselves.
  activityActorLabel(a: ProjectActivity): string {
    const me = this.authService.currentUser()?.fullName;
    return a.actorName && a.actorName === me ? 'You' : (a.actorName ?? 'Neelastack');
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '';
    const kb = bytes / 1024;
    return kb < 1024 ? `${kb.toFixed(0)} KB` : `${(kb / 1024).toFixed(1)} MB`;
  }
}

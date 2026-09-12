import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SeoService } from '../../../core/services/seo.service';
import { UpiPaymentService } from '../../../core/services/upi-payment.service';
import { PaymentScheduleService } from '../../../core/services/payment-schedule.service';
import { ProjectHealthService } from '../../../core/services/project-health.service';
import { EngagementService } from '../../../core/services/engagement.service';
import { Engagement, ProjectOperationsSummary, UpiPaymentMethod, UpiSubmission, PaymentSchedule } from '../../../core/models/content.model';

@Component({
  selector: 'app-admin-payments', standalone: true,
  imports: [CommonModule, FormsModule, DatePipe, DecimalPipe],
  templateUrl: './admin-payments.component.html', styleUrl: './admin-payments.component.scss',
})
export class AdminPaymentsComponent implements OnInit {
  private seo = inject(SeoService); private upi = inject(UpiPaymentService); private schedules = inject(PaymentScheduleService);
  private health = inject(ProjectHealthService); private engagements = inject(EngagementService);
  methods = signal<UpiPaymentMethod[]>([]); pending = signal<UpiSubmission[]>([]); ops = signal<ProjectOperationsSummary | null>(null);
  projects = signal<Engagement[]>([]); selectedProjectId = ''; projectSchedule = signal<PaymentSchedule | null>(null);
  newLabel=''; newVpa=''; newPayee=''; qrFile: File|null=null; methodBusy=signal(false); actionBusy=signal<string|null>(null); message=signal<string|null>(null);
  totalAmount: number|null=null; scheduleCurrency='INR'; installmentText=''; scheduleBusy=signal(false); scheduleMessage=signal<string|null>(null);

  ngOnInit(): void { this.seo.update({title:'Payments & Operations',description:'Manage UPI payments, payment plans and project operations.',noindex:true}); this.reload(); this.engagements.listAllForAdmin().subscribe({next:v=>this.projects.set(v)}); }
  reload(): void {
    this.message.set(null);
    this.upi.listAllMethods().subscribe({
      next: v => this.methods.set(v),
      error: e => {
        if (e?.status === 401) {
          this.message.set('Your admin session has expired. Please sign in again.');
        } else if (e?.status === 403) {
          this.message.set('Your account is not currently permitted to manage payment settings.');
        } else {
          this.message.set(e?.error?.message ?? 'Could not load UPI methods.');
        }
      },
    });
    this.upi.listPendingSubmissions().subscribe({
      next: v => this.pending.set(v),
      error: e => {
        if (e?.status !== 401 && e?.status !== 403) {
          this.message.set(e?.error?.message ?? 'Could not load pending UPI submissions.');
        }
      },
    });
    this.health.getOperationsSummary().subscribe({
      next: v => this.ops.set(v),
      error: e => {
        if (e?.status !== 401 && e?.status !== 403) {
          this.message.set(e?.error?.message ?? 'Could not load operations summary.');
        }
      },
    });
  }
  onQr(event: Event): void { const input=event.target as HTMLInputElement; this.qrFile=input.files?.[0]??null; }
  addMethod(): void { if(!this.newLabel.trim()||!this.qrFile){this.message.set('Label and QR image are required.');return;} this.methodBusy.set(true); this.message.set(null); this.upi.createMethod({label:this.newLabel.trim(),vpa:this.newVpa.trim()||undefined,payeeName:this.newPayee.trim()||undefined},this.qrFile).subscribe({next:v=>{this.methods.set([v,...this.methods()]);this.newLabel='';this.newVpa='';this.newPayee='';this.qrFile=null;this.methodBusy.set(false);},error:e=>{this.methodBusy.set(false);this.message.set(e?.error?.message??'Could not create UPI method.');}}); }
  toggleMethod(m: UpiPaymentMethod): void { this.actionBusy.set(m.id); const call=m.active?this.upi.deactivateMethod(m.id):this.upi.activateMethod(m.id); call.subscribe({next:v=>{this.methods.set(this.methods().map(x=>x.id===v.id?v:x));this.actionBusy.set(null)},error:e=>{this.actionBusy.set(null);this.message.set(e?.error?.message??'Could not update UPI method.')}}); }
  deleteMethod(m: UpiPaymentMethod): void { if(!confirm(`Delete ${m.label}?`))return; this.actionBusy.set(m.id); this.upi.deleteMethod(m.id).subscribe({next:()=>{this.methods.set(this.methods().filter(x=>x.id!==m.id));this.actionBusy.set(null)},error:e=>{this.actionBusy.set(null);this.message.set(e?.error?.message??'Could not delete UPI method.')}}); }
  verify(s: UpiSubmission, approve: boolean): void { this.actionBusy.set(s.id); this.upi.verifySubmission(s.id,approve).subscribe({next:()=>{this.pending.set(this.pending().filter(x=>x.id!==s.id));this.actionBusy.set(null);this.reload()},error:e=>{this.actionBusy.set(null);this.message.set(e?.error?.message??'Could not verify payment.')}}); }
  loadSchedule(): void { if(!this.selectedProjectId)return; this.schedules.getForEngagementAsAdmin(this.selectedProjectId).subscribe({next:v=>this.projectSchedule.set(v),error:()=>this.projectSchedule.set(null)}); }
  createSchedule(): void { if(!this.selectedProjectId||!this.totalAmount||!this.installmentText.trim()){this.scheduleMessage.set('Select a project, enter a total, and add installments.');return;} const installments=this.installmentText.split('\n').map((line,i)=>line.split('|').map(x=>x.trim())).filter(a=>a.length>=2).map((a,i)=>({label:a[0],amount:Number(a[1]),dueDate:a[2]||undefined,displayOrder:i})); if(installments.some(x=>!x.label||!Number.isFinite(x.amount)||x.amount<=0)){this.scheduleMessage.set('Each installment must be: Label | Amount | YYYY-MM-DD');return;} this.scheduleBusy.set(true); this.scheduleMessage.set(null); this.schedules.create({engagementId:this.selectedProjectId,totalAmount:this.totalAmount,currency:this.scheduleCurrency,installments}).subscribe({next:v=>{this.projectSchedule.set(v);this.scheduleBusy.set(false);this.scheduleMessage.set('Payment plan created.');},error:e=>{this.scheduleBusy.set(false);this.scheduleMessage.set(e?.error?.message??'Could not create payment plan.');}}); }
  raiseInvoice(id:string):void{this.actionBusy.set(id);this.schedules.raiseInvoice(id).subscribe({next:()=>{this.loadSchedule();this.actionBusy.set(null)},error:e=>{this.actionBusy.set(null);this.scheduleMessage.set(e?.error?.message??'Could not raise invoice.')}})}
}

import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormBuilder, FormArray, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { InquiryService } from '../../../../core/services/inquiry.service';
import { EngagementService } from '../../../../core/services/engagement.service';
import { Inquiry, InquiryStatus, Quotation } from '../../../../core/models/content.model';
import { SeoService } from '../../../../core/services/seo.service';

@Component({
  selector: 'app-admin-inquiry-detail', standalone: true,
  imports: [CommonModule, RouterLink, DatePipe, DecimalPipe, ReactiveFormsModule, FormsModule],
  templateUrl: './admin-inquiry-detail.component.html', styleUrl: './admin-inquiry-detail.component.scss',
})
export class AdminInquiryDetailComponent implements OnInit {
  private route = inject(ActivatedRoute); private seo = inject(SeoService); private inquiryService = inject(InquiryService); private engagementService = inject(EngagementService); private fb = inject(FormBuilder);
  inquiry = signal<Inquiry | null>(null); quotations = signal<Quotation[]>([]); creating = signal(false); sendingId = signal<string | null>(null); sendingError = signal<string | null>(null); creatingEngagement = signal(false); engagementCreated = signal(false); engagementError = signal<string | null>(null); quotationError = signal<string | null>(null); copiedId = signal<string | null>(null);
  readonly statuses: InquiryStatus[] = ['NEW','CONTACTED','QUOTED','WON','LOST'];
  readonly today = new Date().toISOString().slice(0, 10);
  readonly templates = ['Enterprise product build','MVP / product launch','Modernization & rescue','Platform / SaaS build','Custom engagement'];
  engagementForm = this.fb.nonNullable.group({ title:['',Validators.required], description:[''], startDate:[''], targetEndDate:[''] });
  form = this.fb.nonNullable.group({
    title:['',[Validators.required,Validators.maxLength(160)]], scopeSummary:['',[Validators.required,Validators.maxLength(8000)]], currency:['INR',[Validators.required,Validators.pattern(/^[A-Z]{3,8}$/)]], validUntil:[''], notes:[''], executiveSummary:['',[Validators.required,Validators.maxLength(8000)]], deliverables:['',[Validators.required,Validators.maxLength(12000)]], timeline:['',[Validators.required,Validators.maxLength(8000)]], paymentTerms:['30% project initiation\n40% core milestone\n30% production launch',[Validators.required,Validators.maxLength(4000)]], assumptions:['Client provides required content, credentials and timely feedback.'], exclusions:['Third-party subscription fees and material scope changes are excluded unless stated above.'], nextSteps:['Review the secure proposal\nAccept or request a discussion\nSchedule project kickoff',[Validators.required,Validators.maxLength(4000)]], lineItems:this.fb.array([this.buildLineItem()]),
  });
  get lineItems(): FormArray { return this.form.get('lineItems') as FormArray; }
  private buildLineItem(){return this.fb.nonNullable.group({description:['',[Validators.required,Validators.maxLength(200)]],amount:[0,[Validators.required,Validators.min(1),Validators.max(1000000000)]]});}
  ngOnInit():void{this.seo.update({title:'Inquiry',description:'Neelastack inquiry detail.',noindex:true});const id=this.route.snapshot.paramMap.get('id')!;this.load(id);}
  private load(id:string):void{this.inquiryService.getInquiry(id).subscribe({next:i=>{this.inquiry.set(i);this.applyTemplate('Enterprise product build', false);},error:()=>{}});this.inquiryService.getQuotations(id).subscribe({next:q=>this.quotations.set(q)});}
  updateStatus(status:InquiryStatus):void{const i=this.inquiry();if(!i)return;this.inquiryService.updateStatus(i.id,status).subscribe({next:u=>this.inquiry.set(u)});}
  applyTemplate(template:string, overwrite=true):void{const i=this.inquiry(); if(!i)return; const name=i.company||i.name; const price= i.estimate?.low != null && i.estimate?.high != null ? `Indicative range: ₹${i.estimate.low.toLocaleString()} – ₹${i.estimate.high.toLocaleString()}.` : 'Commercials are tailored to the confirmed scope and delivery plan.';
    const presets:Record<string,Partial<Record<string,string>>>={
      'Enterprise product build':{title:`${name} — Enterprise Product Engineering`,executiveSummary:`We understand that ${name} is looking for a production-ready digital product with a clear path from discovery to launch. ${price}`,scopeSummary:'Discovery, architecture, product engineering, quality assurance and production launch, aligned to the agreed requirements.',deliverables:'• Product discovery and technical architecture\n• Production-grade frontend and backend implementation\n• Authentication, integrations and operational controls\n• QA, UAT support and production launch',timeline:'Week 1 · Discovery & architecture\nWeeks 2–4 · Core engineering\nWeek 5 · Integrations & QA\nWeek 6 · UAT & production launch'},
      'MVP / product launch':{title:`${name} — MVP Delivery`,executiveSummary:'A focused MVP engagement designed to validate the core product quickly without compromising the foundations needed for production.',scopeSummary:'Prioritized MVP scope, architecture, implementation, QA and launch.',deliverables:'• MVP feature set\n• Responsive web experience\n• Backend APIs and data model\n• QA and launch support',timeline:'Week 1 · Scope & architecture\nWeeks 2–4 · Build\nWeek 5 · QA & launch'},
      'Modernization & rescue':{title:`${name} — Modernization & Engineering Rescue`,executiveSummary:'A structured modernization plan to reduce operational risk, improve maintainability and create a safer path to continued delivery.',scopeSummary:'Architecture review, prioritized remediation, modernization and production hardening.',deliverables:'• Current-state assessment\n• Target architecture\n• Priority remediation\n• Deployment and observability hardening',timeline:'Week 1 · Assessment\nWeeks 2–3 · Remediation\nWeek 4 · Hardening & handover'}
    };
    const preset=presets[template]||presets['Enterprise product build']; const patch:any={...preset}; if(overwrite)this.form.patchValue(patch); else this.form.patchValue({executiveSummary:preset['executiveSummary'],scopeSummary:preset['scopeSummary'],deliverables:preset['deliverables'],timeline:preset['timeline']});
  }
  addLineItem():void{this.lineItems.push(this.buildLineItem());}
  removeLineItem(i:number):void{if(this.lineItems.length>1)this.lineItems.removeAt(i);}
  total():number{return this.lineItems.controls.reduce((sum,c)=>sum+Number(c.get('amount')?.value||0),0);}
  hasSentQuotation(): boolean { return this.quotations().some(q => q.status !== 'DRAFT'); }
  createQuotation():void{const inquiry=this.inquiry();if(!inquiry||this.form.invalid){this.form.markAllAsTouched();return;}this.creating.set(true);this.quotationError.set(null);this.inquiryService.createQuotation({inquiryId:inquiry.id,...this.form.getRawValue()}).subscribe({next:q=>{this.creating.set(false);this.quotations.set([q,...this.quotations()]);this.form.reset({title:'',scopeSummary:'',currency:'INR',validUntil:'',notes:'',executiveSummary:'',deliverables:'',timeline:'',paymentTerms:'30% project initiation\n40% core milestone\n30% production launch',assumptions:'',exclusions:'',nextSteps:'Review the secure proposal\nAccept or request a discussion\nSchedule project kickoff'});this.lineItems.clear();this.addLineItem();},error:err=>{this.creating.set(false);this.quotationError.set(err?.error?.message||'Could not create the proposal.');}});}
  sendQuotation(id:string):void{this.sendingId.set(id);this.sendingError.set(null);this.inquiryService.sendQuotation(id).subscribe({next:u=>{this.sendingId.set(null);this.sendingError.set(null);this.quotations.set(this.quotations().map(q=>q.id===u.id?u:q));const i=this.inquiry();if(i)this.inquiry.set({...i,status:'QUOTED'});},error:err=>{this.sendingId.set(null);this.sendingError.set(err?.error?.message||'The proposal was not sent. Check the mail configuration and try again.');}});}
  proposalPdfUrl(id:string):string{return this.inquiryService.quotationPdfUrl(id);}
  executiveReportUrl(id:string):string{return this.inquiryService.executiveReportUrl(id);}
  createEngagement():void{const i=this.inquiry();if(!i||this.engagementForm.invalid){this.engagementForm.markAllAsTouched();return;}this.creatingEngagement.set(true);this.engagementError.set(null);this.engagementService.createEngagement({clientEmail:i.email,inquiryId:i.id,...this.engagementForm.getRawValue()}).subscribe({next:()=>{this.creatingEngagement.set(false);this.engagementCreated.set(true);this.updateStatus('WON');},error:err=>{this.creatingEngagement.set(false);this.engagementError.set(err?.error?.message??'Could not create the project. Please try again.');}});}
}

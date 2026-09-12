import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { SeoService } from '../../../core/services/seo.service';
import { AdminStaff, AdminRole } from '../../../core/models/content.model';

@Component({ selector:'app-admin-staff', standalone:true, imports:[CommonModule,FormsModule,DatePipe], templateUrl:'./admin-staff.component.html', styleUrl:'../security/admin-security.component.scss' })
export class AdminStaffComponent implements OnInit {
  private http=inject(HttpClient); private seo=inject(SeoService);
  staff=signal<AdminStaff[]>([]); loading=signal(false); error=signal<string|null>(null); message=signal<string|null>(null); busy=signal<string|null>(null);
  fullName=''; email=''; inviting=signal(false);
  ngOnInit(){this.seo.update({title:'Staff management',description:'Manage Neelastack administrator accounts.',noindex:true});this.load();}
  load(){this.loading.set(true);this.http.get<AdminStaff[]>(`${environment.apiBaseUrl}/admin/staff-management`).subscribe({next:v=>{this.staff.set(v);this.loading.set(false)},error:e=>{this.loading.set(false);this.error.set(e?.error?.message??'Could not load staff.')}})}
  invite(){if(!this.fullName.trim()||!this.email.trim())return;this.inviting.set(true);this.message.set(null);this.http.post<AdminStaff>(`${environment.apiBaseUrl}/admin/staff-management/invite`,{fullName:this.fullName.trim(),email:this.email.trim()}).subscribe({next:v=>{this.staff.set([...this.staff(),v]);this.fullName='';this.email='';this.inviting.set(false);this.message.set('Invitation sent.');},error:e=>{this.inviting.set(false);this.error.set(e?.error?.message??'Could not invite staff member.')}})}
  update(member:AdminStaff){this.busy.set(member.id);this.http.patch<AdminStaff>(`${environment.apiBaseUrl}/admin/staff-management/${member.id}`,{role:member.role,enabled:member.enabled}).subscribe({next:v=>{this.staff.set(this.staff().map(x=>x.id===v.id?v:x));this.busy.set(null)},error:e=>{this.busy.set(null);this.error.set(e?.error?.message??'Could not update staff member.')}})}
  roles: AdminRole[]=['ADMIN','SUPERADMIN'];
}

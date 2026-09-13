import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { environment } from '../../../../environments/environment';
import { SeoService } from '../../../core/services/seo.service';
import { TeamMember } from '../../../core/models/content.model';

@Component({
  selector: 'app-admin-team',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-team.component.html',
  styleUrl: './admin-team.component.scss',
})
export class AdminTeamComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly seo = inject(SeoService);
  private readonly base = `${environment.apiBaseUrl}/admin/team-members`;

  members = signal<TeamMember[]>([]);
  loading = signal(false);
  saving = signal(false);
  deletingId = signal<string | null>(null);
  error = signal<string | null>(null);
  message = signal<string | null>(null);
  editingId = signal<string | null>(null);
  formOpen = signal(false);
  selectedPhoto = signal<File | null>(null);
  previewUrl = signal<string | null>(null);

  name = '';
  role = '';
  bio = '';
  skillsText = '';
  sortOrder = 0;
  active = true;

  ngOnInit(): void {
    this.seo.update({
      title: 'Manage Team Members',
      description: 'SUPERADMIN-only management of the public Neelastack team page.',
      noindex: true,
    });
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<TeamMember[]>(this.base).subscribe({
      next: (members) => {
        this.members.set(members);
        this.loading.set(false);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err?.error?.message ?? 'Could not load team members.');
      },
    });
  }

  startCreate(): void {
    this.resetForm();
    this.editingId.set(null);
    this.formOpen.set(true);
  }

  startEdit(member: TeamMember): void {
    this.editingId.set(member.id);
    this.name = member.name;
    this.role = member.role;
    this.bio = member.bio;
    this.skillsText = (member.skills ?? []).join(', ');
    this.sortOrder = member.sortOrder ?? 0;
    this.active = member.active;
    this.selectedPhoto.set(null);
    this.revokePreview();
    this.previewUrl.set(member.photoUrl ?? null);
    this.error.set(null);
    this.message.set(null);
    this.formOpen.set(true);
  }

  cancel(): void {
    this.formOpen.set(false);
    this.editingId.set(null);
    this.resetForm();
  }

  onPhotoSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedPhoto.set(file);
    this.revokePreview();
    if (file) this.previewUrl.set(URL.createObjectURL(file));
  }

  save(): void {
    this.error.set(null);
    this.message.set(null);
    if (!this.name.trim() || !this.role.trim() || !this.bio.trim()) {
      this.error.set('Name, role and bio are required.');
      return;
    }
    const photo = this.selectedPhoto();
    if (!this.editingId() && !photo) {
      this.error.set('Please choose a profile photo for a new team member.');
      return;
    }
    if (photo) {
      const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/webp']);
      if (!allowedTypes.has(photo.type)) {
        this.error.set('Profile photo must be JPG, PNG or WebP.');
        return;
      }
      if (photo.size > 5 * 1024 * 1024) {
        this.error.set('Profile photo must be 5MB or smaller.');
        return;
      }
    }

    const body = new FormData();
    body.append('name', this.name.trim());
    body.append('role', this.role.trim());
    body.append('bio', this.bio.trim());
    body.append('skills', this.skillsText);
    body.append('sortOrder', String(Number.isFinite(this.sortOrder) ? this.sortOrder : 0));
    body.append('active', String(this.active));
    if (photo) body.append('photo', photo, photo.name);

    this.saving.set(true);
    const id = this.editingId();
    const request = id
      ? this.http.put<TeamMember>(`${this.base}/${id}`, body)
      : this.http.post<TeamMember>(this.base, body);

    request.subscribe({
      next: (member) => {
        this.saving.set(false);
        this.formOpen.set(false);
        this.editingId.set(null);
        this.revokePreview();
        this.previewUrl.set(null);
        this.resetForm();
        this.message.set(id ? 'Team member updated.' : 'Team member added.');
        this.members.set(
          id ? this.members().map((item) => (item.id === member.id ? member : item)) : [...this.members(), member],
        );
      },
      error: (err) => {
        this.saving.set(false);
        if (err?.status === 413) {
          this.error.set('The upload is too large for the server. Please choose a profile photo of 5MB or smaller.');
          return;
        }
        this.error.set(err?.error?.message ?? 'Could not save team member.');
      },
    });
  }

  remove(member: TeamMember): void {
    if (!confirm(`Delete "${member.name}" from the public team page? This cannot be undone.`)) return;
    this.error.set(null);
    this.message.set(null);
    this.deletingId.set(member.id);
    this.http.delete<void>(`${this.base}/${member.id}`).subscribe({
      next: () => {
        this.deletingId.set(null);
        this.members.set(this.members().filter((item) => item.id !== member.id));
        this.message.set('Team member deleted.');
      },
      error: (err) => {
        this.deletingId.set(null);
        this.error.set(err?.error?.message ?? 'Could not delete team member.');
      },
    });
  }

  initials(member: TeamMember): string {
    return member.name
      .trim()
      .split(/\s+/)
      .map((part) => part[0] ?? '')
      .join('')
      .slice(0, 2)
      .toUpperCase();
  }

  private resetForm(): void {
    this.name = '';
    this.role = '';
    this.bio = '';
    this.skillsText = '';
    this.sortOrder = 0;
    this.active = true;
    this.selectedPhoto.set(null);
    this.revokePreview();
    this.previewUrl.set(null);
  }

  private revokePreview(): void {
    const url = this.previewUrl();
    if (url?.startsWith('blob:')) URL.revokeObjectURL(url);
  }
}

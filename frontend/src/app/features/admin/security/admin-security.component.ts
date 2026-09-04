import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MfaService } from '../../../core/services/mfa.service';
import { MfaSetupResponse, MfaStatus } from '../../../core/models/user.model';
import { SeoService } from '../../../core/services/seo.service';

/**
 * Admin's own MFA enrollment/disable UI, against MfaController (see backend
 * dto/mfa/*). Disabling MFA (POST /admin/mfa/disable) is itself a high-risk mutation
 * per StepUpAuthFilter, so submitting that form may trigger the global step-up modal
 * (see StepUpService) before the disable request itself completes — that's expected,
 * not a bug: the endpoint wants a fresh assertion, and the disable form's own code
 * field is the enrollment-account-ownership check, a separate concern.
 */
@Component({
  selector: 'app-admin-security',
  standalone: true,
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './admin-security.component.html',
  styleUrl: './admin-security.component.scss',
})
export class AdminSecurityComponent implements OnInit {
  private mfaService = inject(MfaService);
  private seo = inject(SeoService);
  private fb = inject(FormBuilder);

  status = signal<MfaStatus | null>(null);
  loading = signal(true);
  statusError = signal<string | null>(null);

  // Enrollment flow
  setupData = signal<MfaSetupResponse | null>(null);
  settingUp = signal(false);
  setupError = signal<string | null>(null);
  verifying = signal(false);
  verifyError = signal<string | null>(null);
  recoveryCodes = signal<string[] | null>(null);
  codesCopied = signal(false);

  verifyForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  // Disable flow
  disableFormOpen = signal(false);
  disabling = signal(false);
  disableError = signal<string | null>(null);

  disableForm = this.fb.nonNullable.group({
    password: ['', Validators.required],
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  ngOnInit(): void {
    this.seo.update({ title: 'Security Settings', description: 'Neelastack admin security settings.', noindex: true });
    this.loadStatus();
  }

  private loadStatus(): void {
    this.loading.set(true);
    this.statusError.set(null);
    this.mfaService.status().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
      },
      error: () => {
        this.statusError.set('Could not load your MFA status. Try refreshing the page.');
        this.loading.set(false);
      },
    });
  }

  startEnroll(): void {
    this.settingUp.set(true);
    this.setupError.set(null);
    this.mfaService.setup().subscribe({
      next: (data) => {
        this.settingUp.set(false);
        this.setupData.set(data);
        this.verifyForm.reset({ code: '' });
      },
      error: () => {
        this.settingUp.set(false);
        this.setupError.set('Could not start enrollment. Try again in a moment.');
      },
    });
  }

  cancelEnroll(): void {
    this.setupData.set(null);
    this.verifyError.set(null);
    this.verifyForm.reset({ code: '' });
  }

  submitVerify(): void {
    if (this.verifyForm.invalid) {
      this.verifyForm.markAllAsTouched();
      return;
    }
    this.verifying.set(true);
    this.verifyError.set(null);
    this.mfaService.verify(this.verifyForm.controls.code.value).subscribe({
      next: (res) => {
        this.verifying.set(false);
        this.setupData.set(null);
        this.recoveryCodes.set(res.recoveryCodes);
        this.codesCopied.set(false);
      },
      error: () => {
        this.verifying.set(false);
        this.verifyError.set('That code did not match. Check the time on your device and try the current code.');
      },
    });
  }

  copyRecoveryCodes(): void {
    const codes = this.recoveryCodes();
    if (!codes) return;
    navigator.clipboard?.writeText(codes.join('\n')).then(() => this.codesCopied.set(true));
  }

  acknowledgeRecoveryCodes(): void {
    this.recoveryCodes.set(null);
    this.loadStatus();
  }

  openDisable(): void {
    this.disableFormOpen.set(true);
    this.disableError.set(null);
    this.disableForm.reset({ password: '', code: '' });
  }

  cancelDisable(): void {
    this.disableFormOpen.set(false);
    this.disableError.set(null);
  }

  submitDisable(): void {
    if (this.disableForm.invalid) {
      this.disableForm.markAllAsTouched();
      return;
    }
    this.disabling.set(true);
    this.disableError.set(null);
    const { password, code } = this.disableForm.getRawValue();
    this.mfaService.disable(password, code).subscribe({
      next: () => {
        this.disabling.set(false);
        this.disableFormOpen.set(false);
        this.loadStatus();
      },
      error: () => {
        this.disabling.set(false);
        this.disableError.set('Could not disable MFA — check your password and code and try again.');
      },
    });
  }
}

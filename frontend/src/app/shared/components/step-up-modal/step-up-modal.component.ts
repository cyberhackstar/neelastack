import { Component, effect, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { StepUpService } from '../../../core/services/step-up.service';

/**
 * Mounted once at app root (see AppComponent), same as VerifyBannerComponent —
 * renders nothing until StepUpService.visible() is true, at which point every
 * high-risk admin mutation in flight is effectively paused behind this single prompt.
 */
@Component({
  selector: 'app-step-up-modal',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './step-up-modal.component.html',
  styleUrl: './step-up-modal.component.scss',
})
export class StepUpModalComponent {
  stepUp = inject(StepUpService);
  private fb = inject(FormBuilder);

  form = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  constructor() {
    // Blank the field every time the modal (re)opens, so a previous wrong code
    // never lingers into the next challenge.
    effect(() => {
      if (this.stepUp.visible()) {
        this.form.reset({ code: '' });
      }
    });
  }

  submit(): void {
    if (this.form.invalid || this.stepUp.submitting()) return;
    this.stepUp.submit(this.form.controls.code.value);
  }

  cancel(): void {
    this.stepUp.cancel();
  }
}

import { Component } from '@angular/core';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-google-signin-button',
  standalone: true,
  templateUrl: './google-signin-button.component.html',
  styleUrl: './google-signin-button.component.scss',
})
export class GoogleSigninButtonComponent {
  // Spring Security's OAuth2 login endpoints live at the server root, not under /api/v1.
  readonly googleAuthUrl = `${environment.apiBaseUrl.replace(/\/api\/v1\/?$/, '')}/oauth2/authorization/google`;
}

import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

interface AdminNavLink {
  label: string;
  path: string;
  icon: string;
  /** Only exact-match this route for "active" state (used for the dashboard root). */
  exact?: boolean;
}

interface AdminNavGroup {
  heading: string;
  links: AdminNavLink[];
}

@Component({
  selector: 'app-admin-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './admin-layout.component.html',
  styleUrl: './admin-layout.component.scss',
})
export class AdminLayoutComponent {
  auth = inject(AuthService);

  sidebarOpen = signal(false);

  readonly navGroups: AdminNavGroup[] = [
    {
      heading: 'Overview',
      links: [{ label: 'Dashboard', path: '/admin', icon: 'grid', exact: true }],
    },
    {
      heading: 'Sales',
      links: [{ label: 'Inquiries', path: '/admin/inquiries', icon: 'inbox' }],
    },
    {
      heading: 'Content',
      links: [
        { label: 'Services', path: '/admin/content/services', icon: 'layers' },
        { label: 'Portfolio', path: '/admin/content/projects', icon: 'folder' },
        { label: 'Blog', path: '/admin/content/blog', icon: 'file-text' },
        { label: 'Solutions', path: '/admin/content/solutions', icon: 'compass' },
      ],
    },
    {
      heading: 'Configuration',
      links: [
        { label: 'Pricing rules', path: '/admin/pricing-rules', icon: 'tag' },
        { label: 'Security', path: '/admin/security', icon: 'shield' },
      ],
    },
  ];

  toggleSidebar(): void {
    this.sidebarOpen.set(!this.sidebarOpen());
  }

  closeSidebar(): void {
    this.sidebarOpen.set(false);
  }
}

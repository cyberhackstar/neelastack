import { Routes } from '@angular/router';
import { adminGuard } from './core/guards/admin.guard';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
    title: 'Neelastack — Enterprise-grade Web Applications',
  },
  {
    path: 'services',
    loadComponent: () =>
      import('./features/services/services.component').then((m) => m.ServicesComponent),
    title: 'Services — Neelastack',
  },
  {
    path: 'solutions',
    loadComponent: () =>
      import('./features/solutions/list/solutions-list.component').then((m) => m.SolutionsListComponent),
    title: 'Solutions by Tech Stack — Neelastack',
  },
  {
    path: 'solutions/:slug',
    loadComponent: () =>
      import('./features/solutions/detail/solutions-detail.component').then((m) => m.SolutionsDetailComponent),
  },
  {
    path: 'portfolio',
    loadComponent: () =>
      import('./features/portfolio/list/portfolio-list.component').then((m) => m.PortfolioListComponent),
    title: 'Portfolio — Neelastack',
  },
  {
    path: 'portfolio/:slug',
    loadComponent: () =>
      import('./features/portfolio/detail/portfolio-detail.component').then((m) => m.PortfolioDetailComponent),
  },
  {
    path: 'blog',
    loadComponent: () =>
      import('./features/blog/list/blog-list.component').then((m) => m.BlogListComponent),
    title: 'Blog — Neelastack',
  },
  {
    path: 'blog/:slug',
    loadComponent: () =>
      import('./features/blog/detail/blog-detail.component').then((m) => m.BlogDetailComponent),
  },
  {
    path: 'about',
    loadComponent: () => import('./features/about/about.component').then((m) => m.AboutComponent),
    title: 'About — Neelastack',
  },
  {
    path: 'team',
    loadComponent: () => import('./features/team/team.component').then((m) => m.TeamComponent),
    title: 'Team — Neelastack',
  },
  {
    path: 'estimate',
    loadComponent: () =>
      import('./features/estimator/estimator.component').then((m) => m.EstimatorComponent),
    title: 'Project Estimator — Neelastack',
  },
  {
    path: 'architecture-review',
    loadComponent: () =>
      import('./features/architecture-review/architecture-review.component').then((m) => m.ArchitectureReviewComponent),
    title: 'Free Architecture Review — Neelastack',
  },
  {
    path: 'audit-preview',
    loadComponent: () =>
      import('./features/audit-preview/audit-preview.component').then((m) => m.AuditPreviewComponent),
    title: 'Instant Architecture Risk Score — Neelastack',
  },
  {
    path: 'contact',
    loadComponent: () => import('./features/contact/contact.component').then((m) => m.ContactComponent),
    title: 'Contact — Neelastack',
  },
  {
    path: 'quote/:token',
    loadComponent: () => import('./features/quote/quote.component').then((m) => m.QuoteComponent),
    title: 'Your Quotation — Neelastack',
  },
  {
    path: 'testimonial/:token',
    loadComponent: () => import('./features/testimonial/testimonial.component').then((m) => m.TestimonialComponent),
    title: 'Share Your Feedback — Neelastack',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
    title: 'Sign in — Neelastack',
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component').then((m) => m.RegisterComponent),
    title: 'Create account — Neelastack',
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password.component').then((m) => m.ForgotPasswordComponent),
    title: 'Forgot password — Neelastack',
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/reset-password/reset-password.component').then((m) => m.ResetPasswordComponent),
    title: 'Reset password — Neelastack',
  },
  {
    path: 'verify-email',
    loadComponent: () =>
      import('./features/auth/verify-email/verify-email.component').then((m) => m.VerifyEmailComponent),
    title: 'Verify email — Neelastack',
  },
  {
    path: 'change-password',
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component').then((m) => m.ChangePasswordComponent),
    canActivate: [authGuard],
    title: 'Change password — Neelastack',
  },
  {
    path: 'oauth-callback',
    loadComponent: () =>
      import('./features/auth/oauth-callback/oauth-callback.component').then((m) => m.OAuthCallbackComponent),
    title: 'Signing in — Neelastack',
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/list/dashboard-list.component').then((m) => m.DashboardListComponent),
    canActivate: [authGuard],
    title: 'My Projects — Neelastack',
  },
  {
    path: 'dashboard/:id',
    loadComponent: () =>
      import('./features/dashboard/detail/dashboard-detail.component').then((m) => m.DashboardDetailComponent),
    canActivate: [authGuard],
    title: 'Project — Neelastack',
  },
  {
    path: 'admin',
    loadComponent: () =>
      import('./features/admin/layout/admin-layout.component').then((m) => m.AdminLayoutComponent),
    canActivate: [adminGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/admin/dashboard/admin-dashboard.component').then((m) => m.AdminDashboardComponent),
        title: 'Admin Dashboard — Neelastack',
      },
      {
        path: 'content/services',
        loadComponent: () =>
          import('./features/admin/content/services/admin-services.component').then(
            (m) => m.AdminServicesComponent,
          ),
        title: 'Manage Services — Neelastack Admin',
      },
      {
        path: 'pricing-rules',
        loadComponent: () =>
          import('./features/admin/pricing/admin-pricing.component').then((m) => m.AdminPricingComponent),
        title: 'Manage Pricing Rules — Neelastack Admin',
      },
      {
        path: 'security',
        loadComponent: () =>
          import('./features/admin/security/admin-security.component').then((m) => m.AdminSecurityComponent),
        title: 'Security Settings — Neelastack Admin',
      },
      {
        path: 'content/projects',
        loadComponent: () =>
          import('./features/admin/content/projects/admin-projects.component').then(
            (m) => m.AdminProjectsComponent,
          ),
        title: 'Manage Portfolio — Neelastack Admin',
      },
      {
        path: 'content/blog',
        loadComponent: () =>
          import('./features/admin/content/blog/admin-blog.component').then((m) => m.AdminBlogComponent),
        title: 'Manage Blog — Neelastack Admin',
      },
      {
        path: 'content/solutions',
        loadComponent: () =>
          import('./features/admin/content/solutions/admin-solutions.component').then(
            (m) => m.AdminSolutionsComponent,
          ),
        title: 'Manage Solutions — Neelastack Admin',
      },
      {
        path: 'inquiries',
        loadComponent: () =>
          import('./features/admin/inquiries/list/admin-inquiries-list.component').then(
            (m) => m.AdminInquiriesListComponent,
          ),
        title: 'Inquiries — Neelastack Admin',
      },
      {
        path: 'inquiries/:id',
        loadComponent: () =>
          import('./features/admin/inquiries/detail/admin-inquiry-detail.component').then(
            (m) => m.AdminInquiryDetailComponent,
          ),
        title: 'Inquiry — Neelastack Admin',
      },
    ],
  },
  {
    path: '**',
    loadComponent: () => import('./features/not-found/not-found.component').then((m) => m.NotFoundComponent),
    title: 'Page Not Found — Neelastack',
  },
];

import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface BreadcrumbItem {
  name: string;
  path: string;
}

/**
 * Visible breadcrumb trail. Every page that renders this should pass the exact same
 * `items` array into `SchemaBuilderService.buildBreadcrumbSchema()` for its JSON-LD —
 * this component only handles the on-page visual, not structured data, so the two
 * never drift apart (a `BreadcrumbList` schema with no matching visible trail is a
 * Google structured-data guideline violation, not just a style nicety).
 *
 * Part of the internal-linking silo (item 2): every content tier — Solutions,
 * Services, Portfolio, Blog — links back up its own chain via this same component,
 * closing the loop rather than leaving each page an isolated leaf.
 */
@Component({
  selector: 'app-breadcrumb',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './breadcrumb.component.html',
  styleUrl: './breadcrumb.component.scss',
})
export class BreadcrumbComponent {
  @Input({ required: true }) items: BreadcrumbItem[] = [];
}

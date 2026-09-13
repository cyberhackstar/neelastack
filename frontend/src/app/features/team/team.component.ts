import { Component, OnInit, inject, signal } from "@angular/core";
import { RouterLink } from "@angular/router";
import { HttpClient } from "@angular/common/http";
import { environment } from "../../../environments/environment";
import { TeamMember as ApiTeamMember } from "../../core/models/content.model";
import { SeoService } from "../../core/services/seo.service";
import { TiltDirective } from "../../shared/directives/tilt.directive";
interface TeamMember {
  name: string;
  role: string;
  bio: string;
  initials: string;
  photoUrl: string | null;
  skills: string[];
}
@Component({
  selector: "app-team",
  standalone: true,
  imports: [RouterLink, TiltDirective],
  templateUrl: "./team.component.html",
  styleUrl: "./team.component.scss",
})
export class TeamComponent implements OnInit {
  private seo = inject(SeoService);
  readonly team = signal<TeamMember[]>([]);
  private readonly fallbackTeam: TeamMember[] = [
    { name: "Bhawesh Sharma", role: "Founder & Lead Engineer", bio: "Runs every engagement end-to-end — architecture, backend, frontend, and deployment. Full-stack Java developer with production experience in enterprise Spring Boot systems.", initials: "BS", photoUrl: "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237019/bhawesh_team_xcuzpk.png", skills: ["Spring Boot", "Angular", "Microservices", "PostgreSQL"] },
    { name: "Padmasinha Chitte", role: "Collaborating Engineer", bio: "Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.", initials: "PC", photoUrl: "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/padam_team_x16tpe.png", skills: ["Software Engineering"] },
    { name: "Anuragdeep Srivastav", role: "Collaborating Engineer", bio: "Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.", initials: "AS", photoUrl: "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/anurag_team_lunwb2.png", skills: ["Software Engineering"] },
  ];
  private readonly http = inject(HttpClient);
  ngOnInit(): void {
    this.seo.update({
      title: "Team",
      description:
        "Meet the people behind Neelastack — the engineers trusted to deliver client work.",
      path: "/team",
    });
    this.http.get<ApiTeamMember[]>(`${environment.apiBaseUrl}/public/team-members`).subscribe({
      next: (items) => this.team.set(items.map((m) => ({ ...m, initials: this.initials(m.name) }))),
      error: () => this.team.set(this.fallbackTeam),
    });
  }

  initials(name: string): string {
    return name.trim().split(/\s+/).map((part) => part[0] ?? '').join('').slice(0, 2).toUpperCase();
  }
}

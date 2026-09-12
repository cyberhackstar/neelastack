import { Component, OnInit, inject } from "@angular/core";
import { RouterLink } from "@angular/router";
import { SeoService } from "../../core/services/seo.service";
import { TiltDirective } from "../../shared/directives/tilt.directive";
interface TeamMember {
  name: string;
  role: string;
  bio: string;
  initials: string;
  photoUrl: string;
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
  readonly team: TeamMember[] = [
    {
      name: "Bhawesh Sharma",
      role: "Founder & Lead Engineer",
      bio: "Runs every engagement end-to-end — architecture, backend, frontend, and deployment. Full-stack Java developer with production experience in enterprise Spring Boot systems.",
      initials: "BS",
      photoUrl:
        "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237019/bhawesh_team_xcuzpk.png",

      skills: ["Spring Boot", "Angular", "Microservices", "PostgreSQL"],
    },
    {
      name: "Padmasinha Chitte",
      role: "Collaborating Engineer",
      bio: "Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.",
      initials: "PC",
      photoUrl:
        "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/padam_team_x16tpe.png",
      skills: ["Software Engineering"],
    },
    {
      name: "Anuragdeep Srivastav",
      role: "Collaborating Engineer",
      bio: "Brought in on select engagements that need extra hands or a second set of eyes on architecture decisions.",
      initials: "AS",
      photoUrl:
        "https://res.cloudinary.com/ddrt7emvo/image/upload/v1789237020/anurag_team_lunwb2.png",
      skills: ["Software Engineering"],
    },
  ];
  ngOnInit(): void {
    this.seo.update({
      title: "Team",
      description:
        "Meet the people behind Neelastack — Bhawesh Sharma and the trusted engineers he brings in for larger engagements.",
      path: "/team",
    });
  }
}

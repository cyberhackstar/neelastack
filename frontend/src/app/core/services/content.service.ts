import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import {
  BlogPost,
  BlogPostPayload,
  BlogPostSummary,
  Faq,
  FaqPayload,
  Page,
  Project,
  ProjectPayload,
  Review,
  ReviewPayload,
  ServiceItem,
  ServicePayload,
  TechStackPage,
  TechStackPagePayload,
} from '../models/content.model';

@Injectable({ providedIn: 'root' })
export class ContentService {
  private http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/public`;
  private readonly adminBase = `${environment.apiBaseUrl}/admin`;

  // ---- Public reads ----
  getServices() {
    return this.http.get<ServiceItem[]>(`${this.base}/services`);
  }

  getProjects(featuredOnly = false) {
    const params = new HttpParams().set('featuredOnly', featuredOnly);
    return this.http.get<Project[]>(`${this.base}/projects`, { params });
  }

  getProject(slug: string) {
    return this.http.get<Project>(`${this.base}/projects/${slug}`);
  }

  getBlogPosts(page = 0, size = 9, q?: string, tag?: string) {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q) params = params.set('q', q);
    if (tag) params = params.set('tag', tag);
    return this.http.get<Page<BlogPostSummary>>(`${this.base}/blog`, { params });
  }

  getRelatedPosts(slug: string) {
    return this.http.get<BlogPostSummary[]>(`${this.base}/blog/${slug}/related`);
  }

  getBlogPost(slug: string) {
    return this.http.get<BlogPost>(`${this.base}/blog/${slug}`);
  }

  // ---- Public: programmatic SEO solution pages ----
  getSolutions() {
    return this.http.get<TechStackPage[]>(`${this.base}/solutions`);
  }

  getSolution(slug: string) {
    return this.http.get<TechStackPage>(`${this.base}/solutions/${slug}`);
  }

  // ---- Admin: solution pages ----
  listAllSolutions() {
    return this.http.get<TechStackPage[]>(`${this.adminBase}/solutions`);
  }

  getSolutionById(id: string) {
    return this.http.get<TechStackPage>(`${this.adminBase}/solutions/${id}`);
  }

  createSolution(payload: TechStackPagePayload) {
    return this.http.post<TechStackPage>(`${this.adminBase}/solutions`, payload);
  }

  updateSolution(id: string, payload: TechStackPagePayload) {
    return this.http.put<TechStackPage>(`${this.adminBase}/solutions/${id}`, payload);
  }

  deleteSolution(id: string) {
    return this.http.delete<void>(`${this.adminBase}/solutions/${id}`);
  }

  // ---- Admin: services ----
  listAllServices() {
    return this.http.get<ServiceItem[]>(`${this.adminBase}/services`);
  }

  getServiceById(id: string) {
    return this.http.get<ServiceItem>(`${this.adminBase}/services/${id}`);
  }

  createService(payload: ServicePayload) {
    return this.http.post<ServiceItem>(`${this.adminBase}/services`, payload);
  }

  updateService(id: string, payload: ServicePayload) {
    return this.http.put<ServiceItem>(`${this.adminBase}/services/${id}`, payload);
  }

  deleteService(id: string) {
    return this.http.delete<void>(`${this.adminBase}/services/${id}`);
  }

  // ---- Admin: projects ----
  listAllProjects() {
    return this.http.get<Project[]>(`${this.adminBase}/projects`);
  }

  getProjectById(id: string) {
    return this.http.get<Project>(`${this.adminBase}/projects/${id}`);
  }

  createProject(payload: ProjectPayload) {
    return this.http.post<Project>(`${this.adminBase}/projects`, payload);
  }

  updateProject(id: string, payload: ProjectPayload) {
    return this.http.put<Project>(`${this.adminBase}/projects/${id}`, payload);
  }

  deleteProject(id: string) {
    return this.http.delete<void>(`${this.adminBase}/projects/${id}`);
  }

  // ---- Admin: blog ----
  listAllBlogPosts() {
    return this.http.get<BlogPostSummary[]>(`${this.adminBase}/blog`);
  }

  getBlogPostById(id: string) {
    return this.http.get<BlogPost>(`${this.adminBase}/blog/${id}`);
  }

  createBlogPost(payload: BlogPostPayload) {
    return this.http.post<BlogPost>(`${this.adminBase}/blog`, payload);
  }

  updateBlogPost(id: string, payload: BlogPostPayload) {
    return this.http.put<BlogPost>(`${this.adminBase}/blog/${id}`, payload);
  }

  deleteBlogPost(id: string) {
    return this.http.delete<void>(`${this.adminBase}/blog/${id}`);
  }

  // ---- Admin: service FAQs (FAQPage structured data source) ----
  listFaqs(serviceId: string) {
    return this.http.get<Faq[]>(`${this.adminBase}/services/${serviceId}/faqs`);
  }

  addFaq(serviceId: string, payload: FaqPayload) {
    return this.http.post<Faq>(`${this.adminBase}/services/${serviceId}/faqs`, payload);
  }

  updateFaq(serviceId: string, faqId: string, payload: FaqPayload) {
    return this.http.put<Faq>(`${this.adminBase}/services/${serviceId}/faqs/${faqId}`, payload);
  }

  deleteFaq(serviceId: string, faqId: string) {
    return this.http.delete<void>(`${this.adminBase}/services/${serviceId}/faqs/${faqId}`);
  }

  // ---- Admin: project reviews (Review / AggregateRating structured data source) ----
  listReviews(projectId: string) {
    return this.http.get<Review[]>(`${this.adminBase}/projects/${projectId}/reviews`);
  }

  addReview(projectId: string, payload: ReviewPayload) {
    return this.http.post<Review>(`${this.adminBase}/projects/${projectId}/reviews`, payload);
  }

  updateReview(projectId: string, reviewId: string, payload: ReviewPayload) {
    return this.http.put<Review>(`${this.adminBase}/projects/${projectId}/reviews/${reviewId}`, payload);
  }

  deleteReview(projectId: string, reviewId: string) {
    return this.http.delete<void>(`${this.adminBase}/projects/${projectId}/reviews/${reviewId}`);
  }
}

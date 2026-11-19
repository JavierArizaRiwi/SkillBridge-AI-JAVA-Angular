import { Component, inject, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { CommonModule, DOCUMENT } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription, interval, Subject, combineLatest } from 'rxjs';
import { startWith, switchMap, takeUntil } from 'rxjs/operators';
import { AuditService, BusinessEvent, AuditStats } from '../audit.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html'
})
export class DashboardComponent implements OnInit, OnDestroy {
  private auditService = inject(AuditService);
  private router = inject(Router);
  private document = inject(DOCUMENT);
  private destroy$ = new Subject<void>();
  private pollSubscription?: Subscription;

  // State
  events = signal<BusinessEvent[]>([]);
  stats = signal<AuditStats | null>(null);
  
  loading = signal<boolean>(true);
  error = signal<string | null>(null);
  selectedEvent = signal<BusinessEvent | null>(null);
  
  // Theme
  isDarkMode = signal<boolean>(false);

  // Filters
  filterEventType = signal<string>('');
  filterAction = signal<string>('');
  filterRole = signal<string>('');
  filterResource = signal<string>('');
  searchQuery = signal<string>('');

  // Options for selects
  eventTypes = computed(() => Array.from(new Set(this.events().map(e => e.eventType))).sort());
  actions = computed(() => Array.from(new Set(this.events().map(e => this.getAction(e)))).filter(Boolean).sort());
  roles = computed(() => Array.from(new Set(this.events().map(e => this.getRole(e)))).filter(Boolean).sort());
  resources = computed(() => Array.from(new Set(this.events().map(e => this.getResource(e)))).filter(Boolean).sort());

  // Filtered Events
  filteredEvents = computed(() => {
    let result = this.events();

    const type = this.filterEventType().toLowerCase();
    if (type) result = result.filter(e => e.eventType.toLowerCase() === type);

    const action = this.filterAction().toLowerCase();
    if (action) result = result.filter(e => this.getAction(e).toLowerCase() === action);

    const role = this.filterRole().toLowerCase();
    if (role) result = result.filter(e => this.getRole(e).toLowerCase() === role);

    const resource = this.filterResource().toLowerCase();
    if (resource) result = result.filter(e => this.getResource(e).toLowerCase() === resource);

    const query = this.searchQuery().toLowerCase();
    if (query) {
      result = result.filter(e => 
        this.getUsername(e).toLowerCase().includes(query) ||
        this.getUserId(e).toLowerCase().includes(query) ||
        (e.aggregateId && e.aggregateId.toLowerCase().includes(query)) ||
        (e.correlationId && e.correlationId.toLowerCase().includes(query))
      );
    }
    
    // Sort descending by date
    return result.sort((a, b) => new Date(b.occurredAt).getTime() - new Date(a.occurredAt).getTime());
  });

  // Auth
  currentUser = signal<string>('Admin User');

  ngOnInit() {
    this.extractUserFromToken();
    this.initTheme();
    this.startPolling();
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // --- Theme ---
  initTheme() {
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'dark' || (!savedTheme && window.matchMedia('(prefers-color-scheme: dark)').matches)) {
      this.isDarkMode.set(true);
      this.document.documentElement.classList.add('dark');
    } else {
      this.isDarkMode.set(false);
      this.document.documentElement.classList.remove('dark');
    }
  }

  toggleTheme() {
    this.isDarkMode.update(v => !v);
    if (this.isDarkMode()) {
      this.document.documentElement.classList.add('dark');
      localStorage.setItem('theme', 'dark');
    } else {
      this.document.documentElement.classList.remove('dark');
      localStorage.setItem('theme', 'light');
    }
  }

  // --- Auth ---
  private extractUserFromToken() {
    const token = localStorage.getItem('skillbridge_token');
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        if (payload.sub) {
          this.currentUser.set(payload.sub);
        }
      } catch(e) {}
    }
  }

  logout() {
    localStorage.removeItem('skillbridge_token');
    this.router.navigate(['/login']); 
  }

  // --- Data ---
  startPolling() {
    this.pollSubscription = interval(10000).pipe(
      startWith(0),
      takeUntil(this.destroy$),
      switchMap(() => {
        return combineLatest([
          this.auditService.getStats(),
          this.auditService.getEvents()
        ]).pipe(takeUntil(this.destroy$));
      })
    ).subscribe({
      next: ([stats, events]) => {
        this.stats.set(stats);
        this.events.set(events);
        this.loading.set(false);
        this.error.set(null);
      },
      error: (err) => {
        console.error('Audit Load Error:', err);
        this.error.set('Could not fetch audit data from backend. Retrying...');
        this.loading.set(false);
      }
    });
  }

  manualRefresh() {
    this.loading.set(true);
    this.destroy$.next();
    this.startPolling();
  }

  viewEvent(event: BusinessEvent) {
    this.selectedEvent.set(event);
  }

  closeModal() {
    this.selectedEvent.set(null);
  }

  // --- Helpers ---
  getAction(e: BusinessEvent): string {
    return e.action || e.payload?.action || 'UNKNOWN';
  }
  
  getResource(e: BusinessEvent): string {
    return e.resource || e.aggregateType || 'UNKNOWN';
  }

  getUserId(e: BusinessEvent): string {
    return e.actorUserId || e.payload?.actorUserId || 'SYSTEM';
  }

  getUsername(e: BusinessEvent): string {
    return e.actorUsername || e.payload?.actorUsername || 'SYSTEM';
  }

  getRole(e: BusinessEvent): string {
    return e.actorRole || e.payload?.actorRole || '';
  }

  // Visuals
  getActionBadgeClass(action: string): string {
    const act = action.toUpperCase();
    const base = 'border px-2.5 py-1 rounded-full text-xs font-bold';
    switch (act) {
      case 'LOGIN': return `${base} bg-blue-100 text-blue-800 border-blue-200 dark:bg-blue-900/30 dark:text-blue-300 dark:border-blue-800`;
      case 'REGISTER': return `${base} bg-purple-100 text-purple-800 border-purple-200 dark:bg-purple-900/30 dark:text-purple-300 dark:border-purple-800`;
      case 'CREATE': return `${base} bg-green-100 text-green-800 border-green-200 dark:bg-green-900/30 dark:text-green-300 dark:border-green-800`;
      case 'UPDATE': return `${base} bg-yellow-100 text-yellow-800 border-yellow-200 dark:bg-yellow-900/30 dark:text-yellow-300 dark:border-yellow-800`;
      case 'DELETE': case 'CANCEL': return `${base} bg-red-100 text-red-800 border-red-200 dark:bg-red-900/30 dark:text-red-300 dark:border-red-800`;
      default: return `${base} bg-gray-100 text-gray-800 border-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700`;
    }
  }

  getRoleBadgeClass(role: string): string {
    const r = role.toUpperCase();
    const base = 'border px-2.5 py-1 rounded-full text-xs font-bold';
    switch (r) {
      case 'ADMIN': return `${base} bg-indigo-100 text-indigo-800 border-indigo-200 dark:bg-indigo-900/30 dark:text-indigo-300 dark:border-indigo-800`;
      case 'PROVIDER': return `${base} bg-teal-100 text-teal-800 border-teal-200 dark:bg-teal-900/30 dark:text-teal-300 dark:border-teal-800`;
      case 'CUSTOMER': return `${base} bg-sky-100 text-sky-800 border-sky-200 dark:bg-sky-900/30 dark:text-sky-300 dark:border-sky-800`;
      default: return `${base} bg-slate-100 text-slate-800 border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700`;
    }
  }

  getFormattedPayload(e: BusinessEvent): string {
    return JSON.stringify(e.payload, null, 2);
  }
}

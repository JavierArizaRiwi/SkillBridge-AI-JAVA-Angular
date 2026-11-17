import { ChangeDetectionStrategy, Component, EventEmitter, Input, Output } from '@angular/core';

/** Paginador anterior/siguiente. `page` es base 0, igual que la API. */
@Component({
  selector: 'app-pager',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages > 1) {
      <nav class="mt-8 flex items-center justify-between gap-4" aria-label="Paginación">
        <button type="button" (click)="pageChange.emit(page - 1)" [disabled]="page <= 0"
                class="rounded-full border border-neutral-300 px-4 py-2 text-sm font-medium text-neutral-900 transition-colors hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-40">
          Anterior
        </button>
        <span class="text-sm text-neutral-600 tabular-nums">Página {{ page + 1 }} de {{ totalPages }}</span>
        <button type="button" (click)="pageChange.emit(page + 1)" [disabled]="page >= totalPages - 1"
                class="rounded-full border border-neutral-300 px-4 py-2 text-sm font-medium text-neutral-900 transition-colors hover:bg-neutral-100 disabled:cursor-not-allowed disabled:opacity-40">
          Siguiente
        </button>
      </nav>
    }
  `,
})
export class PagerComponent {
  @Input() page = 0;
  @Input() totalPages = 0;
  @Output() pageChange = new EventEmitter<number>();
}

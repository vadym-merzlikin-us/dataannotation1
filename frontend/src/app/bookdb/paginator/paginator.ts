import { Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-paginator',
  templateUrl: './paginator.html',
  styleUrl: './paginator.scss',
})
export class Paginator {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly totalElements = input.required<number>();
  readonly disabled = input(false);

  readonly pageChange = output<number>();

  protected readonly first = computed(() => this.page() <= 0);
  protected readonly last = computed(() => this.page() >= this.totalPages() - 1);
  protected readonly label = computed(() => {
    const total = this.totalPages();
    return total === 0 ? 'No results' : `Page ${this.page() + 1} of ${total}`;
  });

  protected go(page: number): void {
    if (page < 0 || page > this.totalPages() - 1) {
      return;
    }
    this.pageChange.emit(page);
  }
}

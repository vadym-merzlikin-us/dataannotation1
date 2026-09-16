import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { AnnotationTask, Label } from './annotation-task.model';

/**
 * Same-origin path: nginx proxies /api to the backend in Docker Compose,
 * and `ng serve` proxies it via proxy.conf.json in local development.
 */
const API = '/api/tasks';

@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly http = inject(HttpClient);

  private readonly _tasks = signal<AnnotationTask[]>([]);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);

  readonly tasks = this._tasks.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();

  readonly pending = computed(() => this._tasks().filter((task) => !task.label));
  readonly labelled = computed(() => this._tasks().filter((task) => !!task.label));

  load(): void {
    this._loading.set(true);
    this.http.get<AnnotationTask[]>(API).subscribe({
      next: (tasks) => {
        this._tasks.set(tasks);
        this._error.set(null);
        this._loading.set(false);
      },
      error: (err) => this.fail(err),
    });
  }

  create(text: string): void {
    this.http.post<AnnotationTask>(API, { text }).subscribe({
      next: (created) => {
        this._tasks.update((tasks) => [created, ...tasks]);
        this._error.set(null);
      },
      error: (err) => this.fail(err),
    });
  }

  label(id: string, label: Label): void {
    this.http.put<AnnotationTask>(`${API}/${id}/label`, { label }).subscribe({
      next: (updated) => {
        this._tasks.update((tasks) => tasks.map((task) => (task.id === id ? updated : task)));
        this._error.set(null);
      },
      error: (err) => this.fail(err),
    });
  }

  remove(id: string): void {
    this.http.delete<void>(`${API}/${id}`).subscribe({
      next: () => {
        this._tasks.update((tasks) => tasks.filter((task) => task.id !== id));
        this._error.set(null);
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: HttpErrorResponse): void {
    this._loading.set(false);
    this._error.set(err.error?.detail ?? err.message ?? 'Request failed');
  }
}

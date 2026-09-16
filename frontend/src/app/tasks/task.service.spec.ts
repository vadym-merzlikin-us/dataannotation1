import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AnnotationTask } from './annotation-task.model';
import { TaskService } from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let http: HttpTestingController;

  const task: AnnotationTask = {
    id: 'abc',
    text: 'Shipping was quick',
    createdAt: '2026-09-15T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TaskService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('splits loaded tasks into pending and labelled', () => {
    service.load();
    http.expectOne('/api/tasks').flush([task, { ...task, id: 'def', label: 'positive' }]);

    expect(service.pending().length).toBe(1);
    expect(service.labelled().length).toBe(1);
    expect(service.loading()).toBeFalse();
  });

  it('moves a task to labelled after labelling', () => {
    service.load();
    http.expectOne('/api/tasks').flush([task]);

    service.label('abc', 'negative');
    http.expectOne('/api/tasks/abc/label').flush({ ...task, label: 'negative' });

    expect(service.pending().length).toBe(0);
    expect(service.labelled()[0].label).toBe('negative');
  });

  it('exposes the problem detail when a request fails', () => {
    service.load();
    http.expectOne('/api/tasks').flush(
      { detail: 'boom' },
      { status: 500, statusText: 'Server Error' },
    );

    expect(service.error()).toBe('boom');
  });
});

import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LABELS, Label } from '../annotation-task.model';
import { TaskService } from '../task.service';

@Component({
  selector: 'app-task-board',
  imports: [FormsModule, DatePipe],
  templateUrl: './task-board.html',
  styleUrl: './task-board.scss',
})
export class TaskBoard implements OnInit {
  protected readonly service = inject(TaskService);
  protected readonly labels = LABELS;
  protected readonly draft = signal('');

  ngOnInit(): void {
    this.service.load();
  }

  protected submit(): void {
    const text = this.draft().trim();
    if (!text) {
      return;
    }
    this.service.create(text);
    this.draft.set('');
  }

  protected label(id: string, label: Label): void {
    this.service.label(id, label);
  }

  protected remove(id: string): void {
    this.service.remove(id);
  }
}

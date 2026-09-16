import { Routes } from '@angular/router';
import { BookDbPage } from './bookdb/book-db-page/book-db-page';
import { TaskBoard } from './tasks/task-board/task-board';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'books' },
  { path: 'books', component: BookDbPage, title: 'CodeJunk · Books' },
  { path: 'queue', component: TaskBoard, title: 'CodeJunk · Annotation queue' },
  { path: '**', redirectTo: 'books' },
];

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Author,
  AuthorRequest,
  Book,
  BookRequest,
  PAGE_SIZE,
  PageResponse,
} from './bookdb.model';

const AUTHORS = '/api/bookdb/authors';
const BOOKS = '/api/bookdb/books';

@Injectable({ providedIn: 'root' })
export class BookDbService {
  private readonly http = inject(HttpClient);

  listAuthors(page: number, size = PAGE_SIZE): Observable<PageResponse<Author>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<Author>>(AUTHORS, { params });
  }

  createAuthor(body: AuthorRequest): Observable<Author> {
    return this.http.post<Author>(AUTHORS, body);
  }

  updateAuthor(id: number, body: AuthorRequest): Observable<Author> {
    return this.http.put<Author>(`${AUTHORS}/${id}`, body);
  }

  listBooks(authorId: number, page: number, size = PAGE_SIZE): Observable<PageResponse<Book>> {
    const params = new HttpParams().set('authorId', authorId).set('page', page).set('size', size);
    return this.http.get<PageResponse<Book>>(BOOKS, { params });
  }

  createBook(body: BookRequest): Observable<Book> {
    return this.http.post<Book>(BOOKS, body);
  }

  updateBook(id: number, body: BookRequest): Observable<Book> {
    return this.http.put<Book>(`${BOOKS}/${id}`, body);
  }
}

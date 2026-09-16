import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Collapsible } from '../collapsible/collapsible';
import { Paginator } from '../paginator/paginator';
import { Author, Book, EMPTY_PAGE, PageResponse } from '../bookdb.model';
import { BookDbService } from '../bookdb.service';

/** Blank draft used by both "Add author" and "Add book". */
interface AuthorDraft {
  id: number | null;
  firstName: string;
  secondName: string;
  description: string;
}

interface BookDraft {
  id: number | null;
  name: string;
  description: string;
  authorId: number | null;
}

@Component({
  selector: 'app-book-db-page',
  imports: [FormsModule, Paginator, Collapsible],
  templateUrl: './book-db-page.html',
  styleUrl: './book-db-page.scss',
})
export class BookDbPage implements OnInit {
  private readonly service = inject(BookDbService);

  protected readonly authors = signal<PageResponse<Author>>(EMPTY_PAGE);
  protected readonly books = signal<PageResponse<Book>>(EMPTY_PAGE);

  protected readonly selectedAuthorId = signal<number | null>(null);
  protected readonly selectedBookId = signal<number | null>(null);

  protected readonly authorFormOpen = signal(false);
  protected readonly bookFormOpen = signal(false);

  protected readonly authorDraft = signal<AuthorDraft | null>(null);
  protected readonly bookDraft = signal<BookDraft | null>(null);

  protected readonly loadingAuthors = signal(false);
  protected readonly loadingBooks = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.loadAuthors(0);
  }

  // ---- authors panel ------------------------------------------------------

  protected loadAuthors(page: number): void {
    this.loadingAuthors.set(true);
    this.service.listAuthors(page).subscribe({
      next: (result) => {
        this.authors.set(result);
        this.loadingAuthors.set(false);
        this.error.set(null);
      },
      error: (err) => this.fail(err, () => this.loadingAuthors.set(false)),
    });
  }

  protected selectAuthor(author: Author): void {
    this.selectedAuthorId.set(author.id);
    this.selectedBookId.set(null);

    // Clicking an author opens the author form and closes the book form.
    this.authorDraft.set({
      id: author.id,
      firstName: author.firstName,
      secondName: author.secondName,
      description: author.description ?? '',
    });
    this.authorFormOpen.set(true);
    this.bookFormOpen.set(false);

    this.loadBooks(0);
  }

  protected addAuthor(): void {
    this.authorDraft.set({ id: null, firstName: '', secondName: '', description: '' });
    this.authorFormOpen.set(true);
    this.bookFormOpen.set(false);
  }

  // ---- books panel --------------------------------------------------------

  protected loadBooks(page: number): void {
    const authorId = this.selectedAuthorId();
    if (authorId === null) {
      this.books.set(EMPTY_PAGE);
      return;
    }
    this.loadingBooks.set(true);
    this.service.listBooks(authorId, page).subscribe({
      next: (result) => {
        this.books.set(result);
        this.loadingBooks.set(false);
        this.error.set(null);
      },
      error: (err) => this.fail(err, () => this.loadingBooks.set(false)),
    });
  }

  protected selectBook(book: Book): void {
    this.selectedBookId.set(book.id);

    // Clicking a book opens the book form and closes the author form.
    this.bookDraft.set({
      id: book.id,
      name: book.name,
      description: book.description ?? '',
      authorId: book.authorId,
    });
    this.bookFormOpen.set(true);
    this.authorFormOpen.set(false);
  }

  protected addBook(): void {
    const authorId = this.selectedAuthorId();
    if (authorId === null) {
      return;
    }
    this.selectedBookId.set(null);
    this.bookDraft.set({ id: null, name: '', description: '', authorId });
    this.bookFormOpen.set(true);
    this.authorFormOpen.set(false);
  }

  // ---- saving -------------------------------------------------------------

  protected saveAuthor(): void {
    const draft = this.authorDraft();
    if (!draft || !draft.firstName.trim() || !draft.secondName.trim()) {
      return;
    }
    const body = {
      firstName: draft.firstName.trim(),
      secondName: draft.secondName.trim(),
      description: draft.description.trim(),
    };

    this.saving.set(true);
    const request$ =
      draft.id === null
        ? this.service.createAuthor(body)
        : this.service.updateAuthor(draft.id, body);

    request$.subscribe({
      next: (author) => {
        this.saving.set(false);
        this.error.set(null);
        this.loadAuthors(draft.id === null ? this.lastAuthorPage() : this.authors().page);
        this.selectedAuthorId.set(author.id);
        this.authorDraft.set({
          id: author.id,
          firstName: author.firstName,
          secondName: author.secondName,
          description: author.description ?? '',
        });
        if (draft.id === null) {
          this.loadBooks(0);
        }
      },
      error: (err) => this.fail(err, () => this.saving.set(false)),
    });
  }

  protected saveBook(): void {
    const draft = this.bookDraft();
    if (!draft || !draft.name.trim() || draft.authorId === null) {
      return;
    }
    const body = {
      name: draft.name.trim(),
      description: draft.description.trim(),
      authorId: draft.authorId,
    };

    this.saving.set(true);
    const request$ =
      draft.id === null ? this.service.createBook(body) : this.service.updateBook(draft.id, body);

    request$.subscribe({
      next: (book) => {
        this.saving.set(false);
        this.error.set(null);
        this.selectedBookId.set(book.id);
        this.bookDraft.set({
          id: book.id,
          name: book.name,
          description: book.description ?? '',
          authorId: book.authorId,
        });
        this.loadBooks(this.books().page);
      },
      error: (err) => this.fail(err, () => this.saving.set(false)),
    });
  }

  // ---- helpers ------------------------------------------------------------

  protected authorName(author: Author): string {
    return `${author.secondName}, ${author.firstName}`;
  }

  protected updateAuthorDraft(patch: Partial<AuthorDraft>): void {
    const current = this.authorDraft();
    if (current) {
      this.authorDraft.set({ ...current, ...patch });
    }
  }

  protected updateBookDraft(patch: Partial<BookDraft>): void {
    const current = this.bookDraft();
    if (current) {
      this.bookDraft.set({ ...current, ...patch });
    }
  }

  /** A new author sorts somewhere unknown; land on the last page to find it. */
  private lastAuthorPage(): number {
    return Math.max(0, this.authors().totalPages - 1);
  }

  private fail(err: HttpErrorResponse, cleanup: () => void): void {
    cleanup();
    this.error.set(err.error?.detail ?? err.message ?? 'Request failed');
  }
}

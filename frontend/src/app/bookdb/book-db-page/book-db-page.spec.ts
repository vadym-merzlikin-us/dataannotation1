import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BookDbPage } from './book-db-page';
import { Author, Book, PageResponse } from '../bookdb.model';

function authorPage(content: Author[], totalElements = content.length): PageResponse<Author> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

function bookPage(content: Book[], totalElements = content.length): PageResponse<Book> {
  return { content, page: 0, size: 20, totalElements, totalPages: Math.ceil(totalElements / 20) };
}

const AUTHOR: Author = { id: 7, firstName: 'Ursula', secondName: 'Le Guin', description: 'sf' };
const BOOK: Book = { id: 31, name: 'A Wizard of Earthsea', description: 'first', authorId: 7 };

describe('BookDbPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BookDbPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function createAndLoad() {
    const fixture = TestBed.createComponent(BookDbPage);
    fixture.detectChanges();
    http
      .expectOne((req) => req.url === '/api/bookdb/authors' && req.params.get('size') === '20')
      .flush(authorPage([AUTHOR], 25));
    fixture.detectChanges();
    return fixture;
  }

  it('asks for 20 authors on the first page', () => {
    const fixture = createAndLoad();
    expect(fixture.nativeElement.textContent).toContain('Le Guin');
  });

  it('loads the selected author’s books and opens the author editor', () => {
    const fixture = createAndLoad();
    const firstRow: HTMLButtonElement = fixture.nativeElement.querySelector('.row');
    firstRow.click();
    fixture.detectChanges();

    const req = http.expectOne(
      (r) => r.url === '/api/bookdb/books' && r.params.get('authorId') === '7',
    );
    expect(req.request.params.get('size')).toBe('20');
    req.flush(bookPage([BOOK]));
    fixture.detectChanges();

    const headers = fixture.nativeElement.querySelectorAll('app-collapsible .header');
    expect(headers[0].getAttribute('aria-expanded')).toBe('true'); // author
    expect(headers[1].getAttribute('aria-expanded')).toBe('false'); // book
  });

  it('swaps which editor is open when a book is clicked', () => {
    const fixture = createAndLoad();
    fixture.nativeElement.querySelector('.row').click();
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === '/api/bookdb/books')
      .flush(bookPage([BOOK]));
    fixture.detectChanges();

    const bookRow: HTMLButtonElement =
      fixture.nativeElement.querySelectorAll('.panel')[1].querySelector('.row');
    bookRow.click();
    fixture.detectChanges();

    const headers = fixture.nativeElement.querySelectorAll('app-collapsible .header');
    expect(headers[0].getAttribute('aria-expanded')).toBe('false'); // author collapsed
    expect(headers[1].getAttribute('aria-expanded')).toBe('true'); // book expanded
  });

  it('requests the next author page from the paginator', () => {
    const fixture = createAndLoad();
    const next: HTMLButtonElement = fixture.nativeElement.querySelector(
      'app-paginator button[aria-label="Next page"]',
    );
    next.click();
    fixture.detectChanges();

    const req = http.expectOne((r) => r.url === '/api/bookdb/authors');
    expect(req.request.params.get('page')).toBe('1');
    req.flush(authorPage([], 25));
  });
});

export interface Author {
  id: number;
  firstName: string;
  secondName: string;
  description?: string;
  version: number;
}

export interface Book {
  id: number;
  name: string;
  description?: string;
  authorId: number;
  version: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuthorRequest {
  firstName: string;
  secondName: string;
  description?: string;
  version: number;
}

export interface BookRequest {
  name: string;
  description?: string;
  authorId: number;
  version: number;
}

export const PAGE_SIZE = 20;

export const EMPTY_PAGE: PageResponse<never> = {
  content: [],
  page: 0,
  size: PAGE_SIZE,
  totalElements: 0,
  totalPages: 0,
};

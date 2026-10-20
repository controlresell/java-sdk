package com.controlresell.sdk;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

/**
 * Every element of a list paginated by cursor, page after page, as it is iterated. The fetcher gets the cursor of the
 * page to read ({@code null} for the first one) and must send the same filters each time; the cursor is opaque.
 */
final class Paginator<T, P> implements Iterable<T> {
  interface Page<T> {
    List<T> data();

    String nextCursor();
  }

  private final Function<String, P> fetchPage;
  private final Function<P, Page<T>> view;

  Paginator(Function<String, P> fetchPage, Function<P, Page<T>> view) {
    this.fetchPage = fetchPage;
    this.view = view;
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<>() {
      private Iterator<T> current = List.<T>of().iterator();
      private String cursor;
      private boolean started;
      private boolean done;

      @Override
      public boolean hasNext() {
        while (!current.hasNext()) {
          if (done) return false;
          Page<T> page = view.apply(fetchPage.apply(started ? cursor : null));
          started = true;
          current = page.data().iterator();
          cursor = page.nextCursor();
          done = cursor == null;
        }
        return true;
      }

      @Override
      public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        return current.next();
      }
    };
  }
}

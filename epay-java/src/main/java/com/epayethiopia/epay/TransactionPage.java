package com.epayethiopia.epay;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.epayethiopia.epay.model.Transaction;

/**
 * One page of transactions, plus the means to walk the rest.
 *
 * <p>Iterating a page walks every remaining transaction across every following
 * page, fetching lazily:
 *
 * <pre>{@code
 * for (Transaction transaction : epay.transactions().list()) {
 *     System.out.println(transaction.reference());
 * }
 * }</pre>
 *
 * <p>Use {@link #data()} when you want exactly the current page.
 */
public final class TransactionPage implements Iterable<Transaction> {

    private final List<Transaction> data;
    private final String nextCursor;
    private final boolean hasMore;
    private final Function<String, TransactionPage> fetchNext;

    TransactionPage(
            List<Transaction> data, String nextCursor, boolean hasMore, Function<String, TransactionPage> fetchNext) {
        this.data = List.copyOf(data);
        this.nextCursor = nextCursor;
        this.hasMore = hasMore;
        this.fetchNext = fetchNext;
    }

    /** Up to 10 transactions, newest first: exactly this page. */
    public List<Transaction> data() {
        return data;
    }

    /** The cursor for the next page, or empty on the last page. */
    public Optional<String> nextCursor() {
        return Optional.ofNullable(nextCursor);
    }

    /** Whether more transactions exist beyond this page. */
    public boolean hasMore() {
        return hasMore && nextCursor != null;
    }

    /** Fetches the next page, or returns empty on the last page. */
    public Optional<TransactionPage> nextPage() {
        return hasMore() ? Optional.of(fetchNext.apply(nextCursor)) : Optional.empty();
    }

    /** This page and each following page in turn, fetched as the iteration reaches them. */
    public Iterable<TransactionPage> pages() {
        return () -> new Iterator<>() {
            private TransactionPage upcoming = TransactionPage.this;
            // The page last handed out. Its successor is fetched only when
            // hasNext() is asked, so a caller that stops early never pays for
            // a page it will not read.
            private TransactionPage previous;

            @Override
            public boolean hasNext() {
                if (upcoming == null && previous != null) {
                    upcoming = previous.nextPage().orElse(null);
                    previous = null;
                }
                return upcoming != null;
            }

            @Override
            public TransactionPage next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                previous = upcoming;
                upcoming = null;
                return previous;
            }
        };
    }

    /** Every transaction from this page onward, fetching pages on demand. */
    @Override
    public Iterator<Transaction> iterator() {
        Iterator<TransactionPage> pages = pages().iterator();
        return new Iterator<>() {
            private Iterator<Transaction> current = List.<Transaction>of().iterator();

            @Override
            public boolean hasNext() {
                while (!current.hasNext() && pages.hasNext()) {
                    current = pages.next().data.iterator();
                }
                return current.hasNext();
            }

            @Override
            public Transaction next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return current.next();
            }
        };
    }

    /**
     * A lazy stream of every transaction from this page onward. Short-circuiting
     * operations such as {@code limit} and {@code findFirst} stop fetching early.
     */
    public Stream<Transaction> stream() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED | Spliterator.NONNULL), false);
    }

    /**
     * Collects up to {@code limit} transactions from this page onward. Pass a
     * limit: collecting a busy account's full history is many requests.
     */
    public List<Transaction> toList(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        List<Transaction> collected = new ArrayList<>();
        Iterator<Transaction> it = iterator();
        while (collected.size() < limit && it.hasNext()) {
            collected.add(it.next());
        }
        return collected;
    }
}

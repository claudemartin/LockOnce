package ch.claude_martin.lockonce;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * This allows easy setup of "lazy initialization". This is thread-safe.
 * <p>
 * <p>
 * Example code: <blockquote><code>
 * <pre>
 * // declaration of lazy field:
 * final Supplier&lt;Foo&gt; lazy = Lazy.of(Foo::new);  
 * // Access to the data:
 * Foo foo = lazy.get();
 * </pre>
 * </code></blockquote>
 * 
 * @param <T>
 *          Type of the element that is created lazily.
 * 
 * @author Claude Martin
 */
public final class Lazy<T> implements Supplier<T> {
  private final LockOnce lock = new LockOnce();
  private final Supplier<T> supplier;
  private volatile T value;

  private Lazy(final Supplier<T> supplier) {
    this.supplier = supplier;
  }

  /**
   * Creates new instance of Lazy. You provide a supplier and get one in return.
   * But the element is only created once.
   * 
   * @param <T>
   *          Type of the element that is created lazily.
   * @param supplier
   *          The action to initialize an instance of T.
   * @return A new supplier that will always return the same element.
   */
  public static <T> Lazy<T> of(final Supplier<T> supplier) {
    requireNonNull(supplier, "supplier");
    return new Lazy<>(supplier);
  }

  /**
   * Convenience method to register a "destructor". A lazy value often exists
   * until the JVM shuts down. The result (if it was created) might need some
   * clean up.
   * 
   * @param <T>
   *          Type of the element that is created lazily.
   * @param supplier
   *          The action to initialize an instance of T.
   * @param destructor
   *          Cleanup code to be run at shutdown.
   * @throws SecurityException
   *           If a security manager is present and it denies
   *           <tt>{@link RuntimePermission}("shutdownHooks")</tt>
   * 
   * @return A new supplier that will be cleaned at shutdown.
   */
  public static <T> Lazy<T> of(final Supplier<T> supplier,
      final Consumer<T> destructor) {
    requireNonNull(supplier, "supplier");
    requireNonNull(destructor, "destructor");
    final Lazy<T> lazy = new Lazy<>(supplier);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      final T v = lazy.getValue();
      if (v != null)
        destructor.accept(v);
    }, "Lazy-Destructor"));
    return lazy;
  }

  /**
   * Convenience method to register a closeable resource. A lazy value often
   * exists until the JVM shuts down. The result (if it was created) will be
   * closed. Exceptions thrown by {@link AutoCloseable#close()} are ignored.
   * 
   * @param <T>
   *          Type of the resource that is created lazily.
   * @param supplier
   *          The action to initialize an instance of T.
   * @throws SecurityException
   *           If a security manager is present and it denies
   *           <tt>{@link RuntimePermission}("shutdownHooks")</tt>
   * 
   * @return A new supplier of a resource that will be closed at shutdown.
   */
  public static <T extends AutoCloseable> Lazy<T> ofAutoCloseable(
      final Supplier<T> supplier) {
    requireNonNull(supplier, "supplier");
    return of(supplier, t -> {
      try {
        t.close();
      } catch (final Exception e) {
        // JVM is shutting down. No way to process this. :-(
      }
    });

  }

  /**
   * Gets the result. Returns the value if it exists already, or invokes the
   * supplier of this instance to get it. An existing value can be returned
   * rather fast. But it has to access volatile fields. Inside a scope this
   * method should only be invoked once (assigned to a local variable).
   * <p>
   * The returned value can be null. use {@link #opt()} to get an
   * {@link Optional} instead.
   */
  @Override
  public T get() {
    // equivalent, but a bit slower:
    // this.lock.run(() -> this.value = this.supplier.get());
    if (this.lock.lockOnce())
      try {
        this.value = this.supplier.get();
      } finally {
        this.lock.unlock();
      }
    return this.value;
  }

  /**
   * Result as an {@link Optional}.
   * 
   * @see #get()
   */
  public Optional<T> opt() {
    return Optional.ofNullable(this.get());
  }

  /**
   * Returns the value if it already exists. Access to the result without
   * invoking the code to genreate the value. Instead an empty optional is
   * returned. However, if the value is <code>null</code> you also get an empty
   * Optional.
   * 
   * @see #get()
   * @see #opt()
   * @see #isDone()
   * @return an optional holding the value if present, or an empty optional if
   *         not yet present.
   */
  public Optional<T> peek() {
    return Optional.ofNullable(this.getValue());
  }

  /**
   * Indicates whether the lazy value is already available. This always returns
   * quickly.
   */
  public boolean isDone() {
    return this.lock.isSpent();
  }

  T getValue() {
    return this.value;
  }
}

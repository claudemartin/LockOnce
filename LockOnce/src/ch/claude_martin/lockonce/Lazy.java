package ch.claude_martin.lockonce;

import java.util.function.Supplier;

public final class Lazy<T> implements Supplier<T> {
  private final LockOnce lock = new LockOnce();
  private final Supplier<T> supplier;
  private volatile T value;

  private Lazy(final Supplier<T> supplier) {
    this.supplier = supplier;
  }

  public static <T> Supplier<T> of(final Supplier<T> supplier) {
    return new Lazy<>(supplier);
  }

  @Override
  public T get() {
    if (this.lock.lockOnce())
      try {
        this.value = this.supplier.get();
      } finally {
        this.lock.unlock();
      }
    return this.value;
  }

}

package ch.claude_martin.lockonce;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.Assert;

import org.junit.Test;

public class LockOnceTest {
	// This is not volatile. Changes must be visible on access only thanks to LockOnce.
	static int	check	= 0;

	@Test
	public void test() throws InterruptedException {
		final int loops = 50;
		for (int i = 0; i < loops; i++) {
			final int expected = check + 1;
			final LockOnce lo = new LockOnce();
			final int threads = 20;
			final ExecutorService threadPool = Executors.newFixedThreadPool(threads);

			final Runnable runnable = new Runnable() {
				@Override
				public void run() {
					try {
						if (lo.lockOnce()) {
							try {
								final int copy = check;
								Thread.sleep(100);
								check = copy + 1;
							} finally {
								lo.unlock();
							}
						}
						// value must be correct in any case:
						Assert.assertEquals("Wrong value", expected, check);
					} catch (final InterruptedException e) {
						e.printStackTrace();
					}
				}
			};

			for (int j = 0; j < threads; j++) {
				threadPool.submit(runnable);
			}
			threadPool.shutdown();
			threadPool.awaitTermination(10, TimeUnit.SECONDS);

			Assert.assertEquals("Wrong value", expected, check);
		}

		Assert.assertEquals("Wrong total amount", loops, check);

		try {
			final LockOnce lo = new LockOnce();
			lo.unlock();
			Assert.fail("LockOnce.unlock() did not throw any Exception :-(");
		} catch (final IllegalMonitorStateException re) {
			// ok!
		}
		{
			final LockOnce lo = new LockOnce();
			final AtomicBoolean check = new AtomicBoolean(false);
			final Thread thread = new Thread() {
				@Override
				public void run() {
					try {
						lo.unlock();
						Assert.fail("LockOnce.unlock() did not throw any Exception :-(");
					} catch (final IllegalMonitorStateException re) {
						// ok!
						check.set(true);
					}
				}
			};
			thread.start();
			thread.join();
			Assert.assertTrue(check.get());
		}
	}

}

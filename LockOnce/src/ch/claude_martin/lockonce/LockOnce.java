package ch.claude_martin.lockonce;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/** The <code>LockOnce</code> class represents an object used to guard some block of code that is only to be accessed once.
 * 
 * <p>
 * It is recommended practice to <em>always</em> immediately follow a call to {@code lockOnce} with a {@code try} block, most typically in a
 * before/after construction such as:
 * <pre>{@code
 * try {
 *   if (lo.lockOnce()) try {
 *     // ... method body
 *   } finally {
 *     lo.unlock();
 *   }
 * } catch (final InterruptedException e) {
 *   // ...
 * }}</pre>
 * 
 * @author Claude Martin */
public class LockOnce {

	private static final int		STATE_UNTAPPED	= 0;
	private static final int		STATE_LOCKED		= 1;
	private static final int		STATE_SPENT			= 2;

	private volatile int				state						= STATE_UNTAPPED;

	private final ReentrantLock	lock						= new ReentrantLock();
	private final Condition			unlocked				= this.lock.newCondition();

	/** Returns <code>true</code> on only one invocation. All others get <code>false</code>.
	 * 
	 * Note: It is not necessarily the first invocation that get's <code>true</code>. But other threads will block untill {@link #unlock()} is
	 * called the first time. A returned <code>false</code> is guaranteed to be returned after the receiver of <code>true</code> has finished
	 * and changes all are visible.
	 * 
	 * @throws InterruptedException */
	public boolean lockOnce() throws InterruptedException {
		if (this.state == STATE_SPENT) return false;
		this.lock.lock();
		try {
			if (this.state == STATE_LOCKED)
				this.unlocked.await();
			if (this.state == STATE_SPENT) return false;

			this.state = STATE_LOCKED;
			return true;
		} finally {
			this.lock.unlock();
		}
	}

	/** Unlock the LockOnce mechanism. This should be called from a finally-block. */
	public void unlock() {
		if (this.state == STATE_UNTAPPED) throw new RuntimeException("unlock() can not be called if lockOnce() was never called before.");
		if (this.state == STATE_SPENT) return;
		this.lock.lock();
		try {
			this.state = STATE_SPENT;
			this.unlocked.signalAll();
		} finally {
			this.lock.unlock();
		}
	}
}

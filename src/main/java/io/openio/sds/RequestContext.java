package io.openio.sds;

import static io.openio.sds.common.Check.checkArgument;
import io.openio.sds.common.DeadlineManager;
import io.openio.sds.common.IdGen;

/**
 * Generic parameters and context for all OpenIO SDS requests,
 * including a request ID, a timeout (or deadline), etc.
 *
 * @author Florent Vennetier
 *
 */
public class RequestContext {

    private DeadlineManager dm;

    protected String reqId = null;
    protected int reqStart = -1;
    protected int rawTimeout = 30 * 1000;
    protected int deadline = -1;

    public RequestContext() {
        this.dm = DeadlineManager.instance();
    }

    /* -- Request IDs ----------------------------------------------------- */

    /**
     * Ensure this request has an ID and it is at least 8 characters.
     *
     * @return this
     */
    public RequestContext ensureRequestId() {
        if (this.reqId == null)
            this.reqId = IdGen.requestId();
        else if (this.reqId.length() < 8)
            this.reqId += IdGen.requestId().substring(8 - this.reqId.length());
        return this;
    }

    /**
     * Get the request ID.
     *
     * @return the request ID (auto-generated if not set)
     */
    public String requestId() {
        return this.ensureRequestId().reqId;
    }

    /**
     * Set a request ID.
     *
     * @param reqId
     *            a request ID string. If is null, it will be auto-generated. If
     *            it is less than 8 characters, it will be suffixed.
     * @return this
     */
    public RequestContext withRequestId(String reqId) {
        this.reqId = reqId;
        return this;
    }

    /* -- Deadlines and timeouts ------------------------------------------ */

    /**
     * Get the deadline for this request.
     *
     * If a timeout has been set ({@link #withTimeout(int)}) and
     * {@link #startTiming()} has not been called yet, call it now.
     *
     * @return the overall deadline for this request, in milliseconds
     */
    public int deadline() {
        if (!hasDeadline()) {
            startTiming();
        }
        return this.deadline;
    }

    /**
     * Tell whether this request has a deadline or not. This will always return
     * {@code true} after {@link #startTiming()} or {@link #deadline()} has been
     * called.
     *
     * @return {@code true} if this request has a deadline.
     */
    public boolean hasDeadline() {
        return this.deadline >= 0;
    }

    /**
     * Reset the deadline set on this request. This must be called when reusing
     * a context only for its timeout and request ID.
     *
     * @return {@code this}
     */
    public RequestContext resetDeadline() {
        this.deadline = -1;
        return this;
    }

    /**
     * Start timing this request. If no deadline has been set, compute one from
     * the {@link #timeout()}.
     */
    void startTiming() {
        this.reqStart = dm.now();
        if (!this.hasDeadline())
            this.deadline = dm.timeoutToDeadline(this.rawTimeout, this.reqStart);
    }

    /**
     * Get the timeout for the request.
     *
     * If {@link #hasDeadline()} returns {@code true}, successive calls to this
     * method will return decreasing values, and negative values when the
     * deadline has been exceeded.
     *
     * @return the timeout for this request, in milliseconds
     */
    public int timeout() {
        if (this.hasDeadline())
            return this.dm.deadlineToTimeout(this.deadline);
        return this.rawTimeout;
    }

    /**
     * Set a deadline on the whole request.
     *
     * This will reset any previous timeout set with {@link #withTimeout(int)} to the duration
     * from now to the deadline.
     *
     * @param deadline the deadline in milliseconds
     * @return this
     */
    public RequestContext withDeadline(int deadline) {
        checkArgument(deadline >= 0, "deadline cannot be negative");
        this.deadline = deadline;
        this.rawTimeout = this.dm.deadlineToTimeout(deadline);
        return this;
    }

    /**
     * Set a timeout on the whole request.
     *
     * This will reset any previous deadline set with {@link #withDeadline(int)}.
     *
     * The timeout will be used to compute the deadline when
     * {@link #startTiming()} or {@link #deadline()} is called.
     *
     * @param timeout
     *            a timeout in milliseconds
     * @return this
     */
    public RequestContext withTimeout(int timeout) {
        checkArgument(timeout > 0, "timeout cannot be negative");
        this.rawTimeout = timeout;
        this.resetDeadline();
        return this;
    }
}

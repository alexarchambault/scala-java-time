/*
 * Copyright (c) 2007-2009, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package javax.time.calendar;

import javax.time.CalendricalException;
import javax.time.Instant;
import javax.time.calendar.TimeZone.Discontinuity;

/**
 * Provides implementations of {@link ZoneResolver}.
 * <p>
 * ZoneResolvers is a utility class.
 * All resolvers returned are immutable and thread-safe.
 *
 * @author Stephen Colebourne
 */
public final class ZoneResolvers {

    /**
     * Private constructor.
     */
    private ZoneResolvers() {
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the strict zone resolver which rejects all gaps and overlaps
     * as invalid, resulting in an exception.
     *
     * @return the strict resolver, never null
     */
    public static ZoneResolver strict() {
        return Strict.INSTANCE;
    }

    /**
     * Class implementing strict resolver.
     */
    private static class Strict extends ZoneResolver {
        /** The singleton instance. */
        private static final Strict INSTANCE = new Strict();
        
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            throw new CalendricalException("Local time " + newDateTime + " does not exist in time zone " +
                    zone + " due to a gap in the local time-line");
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            throw new CalendricalException("Local time " + newDateTime +
                    " has two matching offsets, " + discontinuity.getOffsetBefore() +
                    " and " + discontinuity.getOffsetAfter() + ", in time zone " + zone);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the pre-transition zone resolver, which returns the instant
     * one nanosecond before the transition for gaps, and the earlier offset
     * for overlaps.
     *
     * @return the pre-transition resolver, never null
     */
    public static ZoneResolver preTransition() {
        return PreTransition.INSTANCE;
    }

    /**
     * Class implementing preTransition resolver.
     */
    private static class PreTransition extends ZoneResolver {
        /** The singleton instance. */
        private static final PreTransition INSTANCE = new PreTransition();
        
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            Instant instantBefore = discontinuity.getTransitionInstant().minusNanos(1);
            return OffsetDateTime.dateTime(instantBefore, discontinuity.getOffsetBefore());
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return OffsetDateTime.dateTime(newDateTime, discontinuity.getOffsetBefore());
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the post-transition zone resolver, which returns the instant
     * after the transition for gaps, and the later offset for overlaps.
     *
     * @return the post-transition resolver, never null
     */
    public static ZoneResolver postTransition() {
        return PostTransition.INSTANCE;
    }

    /**
     * Class implementing postTransition resolver.
     */
    private static class PostTransition extends ZoneResolver {
        /** The singleton instance. */
        private static final PostTransition INSTANCE = new PostTransition();
        
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return discontinuity.getTransitionDateTimeAfter();
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return OffsetDateTime.dateTime(newDateTime, discontinuity.getOffsetAfter());
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the retain offset resolver, which returns the instant after the
     * transition for gaps, and the same offset for overlaps.
     * <p>
     * This resolver is the same as the {{@link #postTransition()} resolver with
     * one additional rule. When processing an overlap, this resolver attempts
     * to use the same offset as the offset specified in the old date-time.
     * If that offset is invalid then the later offset is chosen
     * <p>
     * This resolver is most commonly useful when adding or subtracting time
     * from a <code>ZonedDateTime</code>.
     *
     * @return the retain offset resolver, never null
     */
    public static ZoneResolver retainOffset() {
        return RetainOffset.INSTANCE;
    }

    /**
     * Class implementing retain offset resolver.
     */
    private static class RetainOffset extends ZoneResolver {
        /** The singleton instance. */
        private static final RetainOffset INSTANCE = new RetainOffset();
        
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return discontinuity.getTransitionDateTimeAfter();
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            if (oldDateTime != null && discontinuity.isValidOffset(oldDateTime.getOffset())) {
                return OffsetDateTime.dateTime(newDateTime, oldDateTime.getOffset());
            }
            return OffsetDateTime.dateTime(newDateTime, discontinuity.getOffsetAfter());
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the push forward resolver, which changes the time of the result
     * in a gap by adding the lenth of the gap.
     * <p>
     * If the discontinuity is a gap, then the resolver will add the length of
     * the gap in seconds to the local time.
     * For example, given a gap from 01:00 to 02:00 and a time of 01:20, this
     * will add one hour to result in 02:20.
     * <p>
     * If the discontinuity is an overlap, then the resolver will choose the
     * later of the two offsets.
     *
     * @return the push forward resolver, never null
     */
    public static ZoneResolver pushForward() {
        return PushForward.INSTANCE;
    }

    /**
     * Class implementing push forward resolver.
     */
    private static class PushForward extends ZoneResolver {
        /** The singleton instance. */
        private static final PushForward INSTANCE = new PushForward();
        
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            LocalDateTime result = newDateTime.plus(discontinuity.getDiscontinuitySize());
            return OffsetDateTime.dateTime(result, discontinuity.getOffsetAfter());
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return OffsetDateTime.dateTime(newDateTime, discontinuity.getOffsetAfter());
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Creates a combined resolver, using two different strategies for gap and overlap.
     * <p>
     * If either argument is <code>null</code> then the {@link #strict()} resolver is used.
     *
     * @param gapResolver  the resolver to use for a gap, null means strict
     * @param overlapResolver  the resolver to use for an overlap, null means strict
     * @return the combination resolver, never null
     */
    public static ZoneResolver combination(ZoneResolver gapResolver, ZoneResolver overlapResolver) {
        gapResolver = (gapResolver == null ? strict() : gapResolver);
        overlapResolver = (overlapResolver == null ? strict() : overlapResolver);
        if (gapResolver == overlapResolver) {
            return gapResolver;
        }
        return new Combination(gapResolver, overlapResolver);
    }

    /**
     * Class implementing combination resolver.
     */
    private static class Combination extends ZoneResolver {
        /** The gap resolver. */
        private final ZoneResolver gapResolver;
        /** The overlap resolver. */
        private final ZoneResolver overlapResolver;
        
        /**
         * Constructor.
         * @param gapResolver  the resolver to use for a gap, not null
         * @param overlapResolver  the resolver to use for an overlap, not null
         */
        public Combination(ZoneResolver gapResolver, ZoneResolver overlapResolver) {
            this.gapResolver = gapResolver;
            this.overlapResolver = overlapResolver;
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleGap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return gapResolver.handleGap(zone, discontinuity, newDateTime, oldDateTime);
        }
        /** {@inheritDoc} */
        @Override
        protected OffsetDateTime handleOverlap(
                TimeZone zone, Discontinuity discontinuity,
                LocalDateTime newDateTime, OffsetDateTime oldDateTime) {
            return overlapResolver.handleOverlap(zone, discontinuity, newDateTime, oldDateTime);
        }
    }

}

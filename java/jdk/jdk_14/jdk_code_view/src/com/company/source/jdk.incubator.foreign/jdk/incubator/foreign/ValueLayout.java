/*
 *  Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *  ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package jdk.incubator.foreign;

import java.lang.constant.ConstantDescs;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodHandleDesc;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A value layout. A value layout is used to model the memory layout associated with values of basic data types, such as <em>integral</em> types
 * (either signed or unsigned) and <em>floating-point</em> types. Each value layout has a size and a byte order (see {@link ByteOrder}).
 *
 * <p>
 * This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; use of identity-sensitive operations (including reference equality
 * ({@code ==}), identity hash code, or synchronization) on instances of
 * {@code ValueLayout} may have unpredictable results and should be avoided.
 * The {@code equals} method should be used for comparisons.
 *
 * @implSpec
 * This class is immutable and thread-safe.
 */
public final class ValueLayout extends AbstractLayout implements MemoryLayout {

    private final ByteOrder order;

    ValueLayout(ByteOrder order, long size) {
        this(order, size, size, Optional.empty());
    }

    ValueLayout(ByteOrder order, long size, long alignment, Optional<String> name) {
        super(OptionalLong.of(size), alignment, name);
        this.order = order;
    }

    /**
     * Returns the value's byte order.
     *
     * @return the value's  byte order.
     */
    public ByteOrder order() {
        return order;
    }

    /**
     * Returns a new value layout with given byte order.
     *
     * @param order the desired byte order.
     * @return a new value layout with given byte order.
     */
    public ValueLayout withOrder(ByteOrder order) {
        return new ValueLayout(order, bitSize(), alignment, optName());
    }

    @Override
    public String toString() {
        return decorateLayoutString(String.format("%s%d",
                order == ByteOrder.BIG_ENDIAN ? "B" : "b",
                bitSize()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!super.equals(other)) {
            return false;
        }
        if (!(other instanceof ValueLayout)) {
            return false;
        }
        ValueLayout v = (ValueLayout)other;
        return order.equals(v.order) &&
            bitSize() == v.bitSize() &&
            alignment == v.alignment;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), order, bitSize(), alignment);
    }

    @Override
    ValueLayout dup(long alignment, Optional<String> name) {
        return new ValueLayout(order, bitSize(), alignment, name);
    }

    @Override
    public Optional<DynamicConstantDesc<ValueLayout>> describeConstable() {
        return Optional.of(DynamicConstantDesc.ofNamed(ConstantDescs.BSM_INVOKE, "value",
                CD_VALUE_LAYOUT, MH_VALUE, bitSize(), order == ByteOrder.BIG_ENDIAN ? BIG_ENDIAN : LITTLE_ENDIAN));
    }

    //hack: the declarations below are to make javadoc happy; we could have used generics in AbstractLayout
    //but that causes issues with javadoc, see JDK-8224052

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueLayout withName(String name) {
        return (ValueLayout)super.withName(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ValueLayout withBitAlignment(long alignmentBits) {
        return (ValueLayout)super.withBitAlignment(alignmentBits);
    }
}

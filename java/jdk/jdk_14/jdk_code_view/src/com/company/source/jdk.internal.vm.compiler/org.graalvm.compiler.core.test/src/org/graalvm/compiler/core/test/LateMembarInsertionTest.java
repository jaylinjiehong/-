/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Red Hat Inc. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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



package org.graalvm.compiler.core.test;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.memory.FixedAccessNode;
import org.graalvm.compiler.nodes.memory.MemoryAccess;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.compiler.core.common.GraalOptions.StressTestEarlyReads;

public class LateMembarInsertionTest extends GraalCompilerTest {

    private final ResolvedJavaType volatileAccessType = getMetaAccess().lookupJavaType(VolatileAccess.class);
    private final ResolvedJavaType regularAccessField = getMetaAccess().lookupJavaType(RegularAccess.class);
    private final ResolvedJavaType volatileAccess2Type = getMetaAccess().lookupJavaType(VolatileAccess2.class);

    static class VolatileAccess {
        static volatile int field;
    }

    static class VolatileAccess2 {
        static volatile int field;
    }

    static class RegularAccess {
        static int field;
    }

    public static int volatileFieldLoadFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = RegularAccess.field;
        return v1 + v2;
    }

    @Test
    public void test01() {
        List<TypePair> accesses = compile("volatileFieldLoadFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), regularAccessField);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldLoad() {
        int v1 = VolatileAccess.field;
        int v2 = VolatileAccess2.field;
        return v1 + v2;
    }

    @Test
    public void test02() {
        List<TypePair> accesses = compile("volatileFieldLoadVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int volatileFieldLoadVolatileFieldStore(int v2) {
        int v1 = VolatileAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test03() {
        List<TypePair> accesses = compile("volatileFieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static int volatileFieldStoreVolatileFieldLoad(int v2) {
        VolatileAccess.field = v2;
        return VolatileAccess2.field;
    }

    @Test
    public void test04() {
        List<TypePair> accesses = compile("volatileFieldStoreVolatileFieldLoad", stressTestEarlyReads());

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isRead());
    }

    public static int fieldLoadVolatileFieldStore(int v2) {
        int v1 = RegularAccess.field;
        VolatileAccess2.field = v2;
        return v1;
    }

    @Test
    public void test05() {
        List<TypePair> accesses = compile("fieldLoadVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), regularAccessField);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isRead());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    public static void volatileFieldStoreVolatileFieldStore(int v1, int v2) {
        VolatileAccess.field = v1;
        VolatileAccess2.field = v2;
    }

    @Test
    public void test06() {
        List<TypePair> accesses = compile("volatileFieldStoreVolatileFieldStore");

        Assert.assertEquals(accesses.size(), 2);
        Assert.assertEquals(accesses.get(0).getType(), volatileAccessType);
        Assert.assertEquals(accesses.get(1).getType(), volatileAccess2Type);
        Assert.assertTrue(accesses.get(0).isWrite());
        Assert.assertTrue(accesses.get(1).isWrite());
    }

    private static OptionValues stressTestEarlyReads() {
        EconomicMap<OptionKey<?>, Object> overrides = OptionValues.newOptionMap();
        overrides.put(StressTestEarlyReads, true);
        return new OptionValues(getInitialOptions(), overrides);
    }

    static class TypePair {
        private boolean isRead;
        private ResolvedJavaType type;

        TypePair(boolean isRead, ResolvedJavaType type) {
            this.isRead = isRead;
            this.type = type;
        }

        public boolean isRead() {
            return isRead;
        }

        public boolean isWrite() {
            return !isRead;
        }

        public ResolvedJavaType getType() {
            return type;
        }
    }

    private List<TypePair> compile(String test, OptionValues options) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test), options);
        return getAccesses(graph);
    }

    private List<TypePair> getAccesses(StructuredGraph graph) {
        StructuredGraph.ScheduleResult schedule = graph.getLastSchedule();
        ControlFlowGraph cfg = schedule.getCFG();
        Block[] blocks = cfg.getBlocks();

        return Arrays.stream(blocks).flatMap(b -> schedule.nodesFor(b).stream()).filter(n -> n instanceof MemoryAccess).map(
                        n -> new TypePair(n instanceof ReadNode, classForAccess((FixedAccessNode) n))).collect(Collectors.toList());
    }

    private List<TypePair> compile(String test) {
        StructuredGraph graph = getFinalGraph(getResolvedJavaMethod(test));
        return getAccesses(graph);
    }

    private ResolvedJavaType classForAccess(FixedAccessNode n) {
        AddressNode address = n.getAddress();
        ValueNode base = address.getBase();
        Stamp stamp = base.stamp(NodeView.DEFAULT);
        MetaAccessProvider metaAccess = getMetaAccess();
        ResolvedJavaType javaType = stamp.javaType(metaAccess);
        if (javaType == metaAccess.lookupJavaType(Class.class) && base instanceof ConstantNode) {
            ConstantReflectionProvider constantReflection = getConstantReflection();
            javaType = constantReflection.asJavaType(base.asConstant());
        }
        return javaType;
    }

}

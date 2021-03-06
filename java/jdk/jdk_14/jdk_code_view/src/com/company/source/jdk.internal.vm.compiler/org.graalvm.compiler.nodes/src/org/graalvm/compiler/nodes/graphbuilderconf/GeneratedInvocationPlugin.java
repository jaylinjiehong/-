/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.nodes.graphbuilderconf;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Abstract class for a plugin generated for a method annotated by {@link NodeIntrinsic} or
 * {@link Fold}.
 */
public abstract class GeneratedInvocationPlugin implements InvocationPlugin {

    private ResolvedJavaMethod executeMethod;

    /**
     * Gets the class of the annotation for which this plugin was generated.
     */
    public abstract Class<? extends Annotation> getSource();

    @Override
    public abstract boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args);

    @Override
    public StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            }
        }
        throw new GraalError("could not find method named \"execute\" in " + c.getName());
    }

    protected boolean checkInjectedArgument(GraphBuilderContext b, ValueNode arg, ResolvedJavaMethod foldAnnotatedMethod) {
        if (arg.isNullConstant()) {
            return true;
        }

        if (IS_IN_NATIVE_IMAGE) {
            // The reflection here is problematic for SVM.
            return true;
        }

        if (b.getMethod().equals(foldAnnotatedMethod)) {
            return false;
        }

        ResolvedJavaMethod thisExecuteMethod = getExecutedMethod(b);
        if (b.getMethod().equals(thisExecuteMethod)) {
            // The "execute" method of this plugin is itself being compiled. In (only) this context,
            // the injected argument of the call to the @Fold annotated method will be non-null.
            if (IS_BUILDING_NATIVE_IMAGE) {
                return false;
            }
            return true;
        }
        throw new AssertionError("must pass null to injected argument of " + foldAnnotatedMethod.format("%H.%n(%p)") + ", not " + arg);
    }

    private ResolvedJavaMethod getExecutedMethod(GraphBuilderContext b) {
        if (executeMethod == null) {
            MetaAccessProvider metaAccess = b.getMetaAccess();
            ResolvedJavaMethod baseMethod = metaAccess.lookupJavaMethod(getExecuteMethod());
            ResolvedJavaType thisClass = metaAccess.lookupJavaType(getClass());
            executeMethod = thisClass.resolveConcreteMethod(baseMethod, thisClass);
        }
        return executeMethod;
    }

    private static Method getExecuteMethod() {
        try {
            return GeneratedInvocationPlugin.class.getMethod("execute", GraphBuilderContext.class, ResolvedJavaMethod.class, InvocationPlugin.Receiver.class, ValueNode[].class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new GraalError(e);
        }
    }

    public final boolean isGeneratedFromFoldOrNodeIntrinsic() {
        return getSource().equals(Fold.class) || getSource().equals(NodeIntrinsic.class);
    }
}

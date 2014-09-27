package org.cf.smalivm.context;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.cf.smalivm.SideEffect;
import org.cf.smalivm.VirtualMachine;

public class ExecutionContext {

    private final VirtualMachine vm;
    private final Map<String, ClassState> classNameToState;
    private final Map<String, SideEffect.Level> classNameToSideEffectType;
    private final Set<String> initializedClasses;
    private final Heap heap;

    private MethodState mstate;
    private int callDepth;
    private ExecutionContext parent;

    public ExecutionContext(VirtualMachine vm) {
        this.vm = vm;
        classNameToState = new HashMap<String, ClassState>();
        classNameToSideEffectType = new HashMap<String, SideEffect.Level>();
        initializedClasses = new HashSet<String>();
        heap = new Heap();
        callDepth = 0;
    }

    public ExecutionContext(ExecutionContext other) {
        vm = other.vm;
        if (other.mstate != null) {
            mstate = new MethodState(other.mstate, this);
        }
        classNameToState = new HashMap<String, ClassState>(other.classNameToState);
        classNameToSideEffectType = new HashMap<String, SideEffect.Level>(other.classNameToSideEffectType);
        initializedClasses = new HashSet<String>(other.initializedClasses);
        heap = new Heap(other.getHeap());
        callDepth = other.getCallDepth();
    }

    public ExecutionContext getChild() {
        ExecutionContext child = new ExecutionContext(this.vm);
        child.setCallDepth(callDepth);
        child.setParent(this);

        MethodState childMethodState = getMethodState().getChild(child);
        child.setMethodState(childMethodState);

        for (String className : classNameToState.keySet()) {
            ClassState childClassState = getClassState(className).getChild(child);
            child.initializeClassState(className, childClassState);
        }

        return child;
    }

    private void setParent(ExecutionContext parent) {
        this.parent = parent;
    }

    public MethodState getMethodState() {
        return mstate;
    }

    public ClassState getClassState(String className) {
        staticallyInitializeClassIfNecessary(className);

        return peekClassState(className);
    }

    ClassState peekClassState(String className) {
        return classNameToState.get(className);
    }

    private boolean isClassInitialized(String className) {
        return initializedClasses.contains(className);
    }

    private ExecutionContext getTemplate() {
        ExecutionContext result = this;
        while (result.getParent() != null) {
            result = result.getParent();
        }

        return result;
    }

    ExecutionContext getParent() {
        return parent;
    }

    public void staticallyInitializeClassIfNecessary(String className) {
        // This method should be called when a class is first used. A usage is:
        // 1.) The invocation of a method declared by the class (not inherited from a superclass)
        // 2.) The invocation of a constructor of the class (covered by #1)
        // 3.) The use or assignment of a field declared by a class (not inherited from a superclass), except for fields
        // that are both static and final, and are initialized by a compile-time constant expression.
        if (!vm.isLocalClass(className) || isClassInitialized(className)) {
            return;
        }

        ExecutionContext templateContext = getTemplate();
        ClassState templateClassState = templateContext.peekClassState(className);
        ClassState cState = new ClassState(templateClassState, this);
        initializeClassState(className, cState);

        SideEffect.Level sideEffectType = SideEffect.Level.NONE;
        String clinitDescriptor = className + "-><clinit>()V";

        if (vm.isLocalMethod(clinitDescriptor)) {
            ExecutionGraph graph = vm.execute(clinitDescriptor, this);
            if (graph == null) {
                // Error executing. Assume the worst.
                setClassSideEffectType(className, SideEffect.Level.STRONG);
            } else {
                sideEffectType = graph.getStrongestSideEffectType();
            }
        } else {
            // No clinit for this class.
        }
        setClassSideEffectType(className, sideEffectType);
    }

    public SideEffect.Level getClassStateSideEffectType(String className) {
        staticallyInitializeClassIfNecessary(className);

        return classNameToSideEffectType.get(className);
    }

    void setClassSideEffectType(String className, SideEffect.Level sideEffectType) {
        classNameToSideEffectType.put(className, sideEffectType);
    }

    public void setClassState(String className, ClassState cState) {
        classNameToState.put(className, cState);
    }

    public void initializeClassState(String className, ClassState cState) {
        setClassState(className, cState);
        initializedClasses.add(className);
    }

    public void setMethodState(MethodState mState) {
        this.mstate = mState;
    }

    public Heap getHeap() {
        return heap;
    }

    public int getCallDepth() {
        return callDepth;
    }

    public void setCallDepth(int callDepth) {
        this.callDepth = callDepth;
    }

}
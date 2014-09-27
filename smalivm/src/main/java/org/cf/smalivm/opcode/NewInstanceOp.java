package org.cf.smalivm.opcode;

import org.cf.smalivm.MethodReflector;
import org.cf.smalivm.SideEffect;
import org.cf.smalivm.VirtualMachine;
import org.cf.smalivm.context.ExecutionContext;
import org.cf.smalivm.context.MethodState;
import org.cf.smalivm.type.LocalInstance;
import org.cf.smalivm.type.UninitializedInstance;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction21c;
import org.jf.dexlib2.iface.reference.TypeReference;

public class NewInstanceOp extends ExecutionContextOp {

    static NewInstanceOp create(Instruction instruction, int address, VirtualMachine vm) {
        String opName = instruction.getOpcode().name;
        int childAddress = address + instruction.getCodeUnits();

        Instruction21c instr = (Instruction21c) instruction;
        int destRegister = instr.getRegisterA();
        TypeReference typeRef = (TypeReference) instr.getReference();
        String className = typeRef.getType();

        return new NewInstanceOp(address, opName, childAddress, destRegister, className, vm);
    }

    private final int destRegister;
    private final String className;
    private final VirtualMachine vm;
    private SideEffect.Level sideEffectType;

    NewInstanceOp(int address, String opName, int childAddress, int destRegister, String className, VirtualMachine vm) {
        super(address, opName, childAddress);

        this.destRegister = destRegister;
        this.className = className;
        this.vm = vm;
        sideEffectType = SideEffect.Level.STRONG;
    }

    @Override
    public int[] execute(ExecutionContext ectx) {
        Object instance = null;
        if (vm.isLocalClass(className)) {
            // New-instance causes static initialization (but not new-array!)
            sideEffectType = ectx.getClassStateSideEffectType(className);
            instance = new LocalInstance(className);
        } else {
            if (MethodReflector.isWhitelisted(className)) {
                sideEffectType = SideEffect.Level.NONE;
            }
            instance = new UninitializedInstance(className);
        }

        MethodState mstate = ectx.getMethodState();
        mstate.assignRegister(destRegister, instance);

        return getPossibleChildren();
    }

    @Override
    public SideEffect.Level sideEffectType() {
        return sideEffectType;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getOpName());
        sb.append(" r").append(destRegister).append(", ").append(className);

        return sb.toString();
    }

}
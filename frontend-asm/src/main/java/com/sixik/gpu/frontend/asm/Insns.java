package com.sixik.gpu.frontend.asm;

/**
 * Мнемоники опкодов JVM (для сообщений {@code GPU0302}) и множество опкодов, которые символьное исполнение
 * {@link MethodLowerer} поддерживает на текущем шаге. {@code asm-util} с его {@code Printer} ради одной таблицы не
 * подключается.
 */
final class Insns {

    /** Мнемоники по номеру опкода (JVMS, глава 6), 0..201. */
    private static final String[] NAMES = (
            "nop aconst_null iconst_m1 iconst_0 iconst_1 iconst_2 iconst_3 iconst_4 iconst_5 lconst_0 lconst_1 "
                    + "fconst_0 fconst_1 fconst_2 dconst_0 dconst_1 bipush sipush ldc ldc_w ldc2_w iload lload fload "
                    + "dload aload iload_0 iload_1 iload_2 iload_3 lload_0 lload_1 lload_2 lload_3 fload_0 fload_1 "
                    + "fload_2 fload_3 dload_0 dload_1 dload_2 dload_3 aload_0 aload_1 aload_2 aload_3 iaload laload "
                    + "faload daload aaload baload caload saload istore lstore fstore dstore astore istore_0 istore_1 "
                    + "istore_2 istore_3 lstore_0 lstore_1 lstore_2 lstore_3 fstore_0 fstore_1 fstore_2 fstore_3 "
                    + "dstore_0 dstore_1 dstore_2 dstore_3 astore_0 astore_1 astore_2 astore_3 iastore lastore fastore "
                    + "dastore aastore bastore castore sastore pop pop2 dup dup_x1 dup_x2 dup2 dup2_x1 dup2_x2 swap "
                    + "iadd ladd fadd dadd isub lsub fsub dsub imul lmul fmul dmul idiv ldiv fdiv ddiv irem lrem frem "
                    + "drem ineg lneg fneg dneg ishl lshl ishr lshr iushr lushr iand land ior lor ixor lxor iinc i2l "
                    + "i2f i2d l2i l2f l2d f2i f2l f2d d2i d2l d2f i2b i2c i2s lcmp fcmpl fcmpg dcmpl dcmpg ifeq ifne "
                    + "iflt ifge ifgt ifle if_icmpeq if_icmpne if_icmplt if_icmpge if_icmpgt if_icmple if_acmpeq "
                    + "if_acmpne goto jsr ret tableswitch lookupswitch ireturn lreturn freturn dreturn areturn return "
                    + "getstatic putstatic getfield putfield invokevirtual invokespecial invokestatic invokeinterface "
                    + "invokedynamic new newarray anewarray arraylength athrow checkcast instanceof monitorenter "
                    + "monitorexit wide multianewarray ifnull ifnonnull goto_w jsr_w").split(" ");

    private Insns() {
    }

    /**
     * @param opcode опкод
     * @return мнемоника либо {@code opcode#N}
     */
    static String name(int opcode) {
        return opcode >= 0 && opcode < NAMES.length ? NAMES[opcode] : "opcode#" + opcode;
    }

    /**
     * Опкоды, которые проверка подмножества не перечисляет отдельно, но символьное исполнение умеет: константы,
     * локали, массивы, стек, арифметика, приведения, {@code *return}. Остальные - {@code GPU0302}.
     *
     * @param opcode опкод
     * @return {@code true}, если поддерживается
     */
    static boolean supported(int opcode) {
        return opcode == 0 // nop
                || (opcode >= 2 && opcode <= 18) // iconst_m1 .. ldc (ASM сводит ldc_w/ldc2_w к LDC)
                || (opcode >= 21 && opcode <= 25) // *load
                || (opcode >= 46 && opcode <= 53 && opcode != 50) // *aload, кроме aaload
                || (opcode >= 54 && opcode <= 58) // *store
                || (opcode >= 79 && opcode <= 86 && opcode != 83) // *astore, кроме aastore
                || (opcode >= 87 && opcode <= 147) // pop .. i2s: стек, арифметика, iinc, приведения
                || (opcode >= 172 && opcode <= 175) || opcode == 177 // ireturn .. dreturn, return
                || opcode == 188 || opcode == 190; // newarray, arraylength
    }
}

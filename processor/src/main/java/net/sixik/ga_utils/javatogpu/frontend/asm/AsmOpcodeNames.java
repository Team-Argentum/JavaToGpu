package net.sixik.ga_utils.javatogpu.frontend.asm;

import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

final class AsmOpcodeNames {

    private static final Map<Integer, String> OPCODE_NAMES = opcodeNames();

    private AsmOpcodeNames() {
    }

    static String nameOf(int opcode) {
        return OPCODE_NAMES.getOrDefault(opcode, "opcode#" + opcode);
    }

    private static Map<Integer, String> opcodeNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (Field field : Opcodes.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            String name = field.getName();
            if (!isOpcodeConstantName(name)) {
                continue;
            }
            try {
                names.putIfAbsent(field.getInt(null), name);
            } catch (IllegalAccessException ignored) {
                // Public ASM opcode constants should always be readable.
            }
        }
        return Map.copyOf(names);
    }

    private static boolean isOpcodeConstantName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!Character.isUpperCase(character) && character != '_' && !Character.isDigit(character)) {
                return false;
            }
        }
        return true;
    }
}

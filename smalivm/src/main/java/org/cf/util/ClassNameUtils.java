package org.cf.util;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class ClassNameUtils {

    private static final Map<String, Class<?>> binaryNameToType;

    private static final BiMap<String, String> internalPrimitiveToBinaryName;
    private static final BiMap<String, String> internalPrimitiveToWrapper;
    static {
        internalPrimitiveToWrapper = HashBiMap.create();
        internalPrimitiveToWrapper.put("I", Integer.class.getName());
        internalPrimitiveToWrapper.put("S", Short.class.getName());
        internalPrimitiveToWrapper.put("J", Long.class.getName());
        internalPrimitiveToWrapper.put("B", Byte.class.getName());
        internalPrimitiveToWrapper.put("D", Double.class.getName());
        internalPrimitiveToWrapper.put("F", Float.class.getName());
        internalPrimitiveToWrapper.put("Z", Boolean.class.getName());
        internalPrimitiveToWrapper.put("C", Character.class.getName());
        internalPrimitiveToWrapper.put("V", Void.class.getName());

        internalPrimitiveToBinaryName = HashBiMap.create();
        internalPrimitiveToBinaryName.put("I", int.class.getName());
        internalPrimitiveToBinaryName.put("S", short.class.getName());
        internalPrimitiveToBinaryName.put("J", long.class.getName());
        internalPrimitiveToBinaryName.put("B", byte.class.getName());
        internalPrimitiveToBinaryName.put("D", double.class.getName());
        internalPrimitiveToBinaryName.put("F", float.class.getName());
        internalPrimitiveToBinaryName.put("Z", boolean.class.getName());
        internalPrimitiveToBinaryName.put("C", char.class.getName());

        // Note: Void is not technically a primitive.
        internalPrimitiveToBinaryName.put("V", void.class.getName());

        binaryNameToType = new HashMap<String, Class<?>>(9);
        binaryNameToType.put("int", Integer.TYPE);
        binaryNameToType.put("short", Short.TYPE);
        binaryNameToType.put("long", Long.TYPE);
        binaryNameToType.put("byte", Byte.TYPE);
        binaryNameToType.put("float", Float.TYPE);
        binaryNameToType.put("double", Double.TYPE);
        binaryNameToType.put("boolean", Boolean.TYPE);
        binaryNameToType.put("char", Character.TYPE);
        binaryNameToType.put("void", Void.TYPE);
    }

    public static enum TypeFormat {
        BINARY, INTERNAL, SOURCE
    }

    static String addDimensionsToBinaryClassName(String className, int dimensionCount) {
        StringBuilder sb = new StringBuilder(className);
        for (int i = 0; i < dimensionCount; i++) {
            sb.append("[]");
        }

        return sb.toString();
    }

    static String addDimensionsToInternalClassName(String className, int dimensionCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dimensionCount; i++) {
            sb.append('[');
        }
        sb.append(className);

        return sb.toString();
    }

    /**
     * Convert a class name in binary format into a class name in the internal or Smali format.
     * For example,
     * "[Ljava.lang.Object;" becomes "[Ljava/lang/Object;"
     * "java.lang.Object" becomes "Ljava/lang/Object;"
     * "int" becomes "I"
     * "[Z" becomes "[Z"
     * 
     * @param binaryName
     * @return the class name in the internal format
     */
    public static String binaryToInternal(String binaryName) {
        String baseName = getComponentBase(binaryName);
        StringBuilder sb = new StringBuilder();
        int dimensionCount = getDimensionCount(binaryName);
        for (int i = 0; i < dimensionCount; i++) {
            sb.append('[');
        }

        String internalPrimitive = internalPrimitiveToBinaryName.inverse().get(baseName);
        if (internalPrimitive != null) {
            return sb.append(internalPrimitive).toString();
        }

        if (dimensionCount > 0 && internalPrimitiveToBinaryName.containsKey(baseName)) {
            return sb.append(baseName).toString();
        }

        if (baseName.endsWith(";")) {
            sb.append(baseName.replace('.', '/'));
        } else {
            sb.append('L').append(baseName.replace('.', '/')).append(';');
        }

        return sb.toString();
    }

    /**
     * Get the base component of an array of any dimension. Works with binary and internal formats.
     * 
     * For example,
     * "[[B" becomes "B"
     * "[[Ljava.lang.Object;" becomes "Ljava.lang.Object;"
     * 
     * @param className
     * @return base component class
     */
    public static String getComponentBase(String className) {
        return className.replace("[", "");
    }

    // Similar to Array.getComponentType
    // works with internal and binary
    /**
     * Similar to Array.getComponentType(). Works with binary and internal formats.
     * 
     * @param className
     * @return component class
     */
    public static String getComponentType(String className) {
        return className.replaceFirst("\\[", "");
    }

    /**
     * Get the dimension count or rank. Works with binary and internal formats.
     * 
     * @param className
     * @return dimension count
     */
    public static int getDimensionCount(String className) {
        // A fancy word for "number of dimensions" is "rank".
        // But getRank() only makes sense if you're a total nerd.
        String baseClassName = className.replace("[", "");

        return className.length() - baseClassName.length();
    }

    /**
     * Get the package name for a given class. Works with all formats.
     * For example,
     * "Lorg/cf/Klazz" gives "org.cf"
     * "org.cf.Klazz" gives "org.cf"
     * 
     * @param className
     * @return package name of class
     */
    public static String getPackageName(String className) {
        String sourceName = toFormat(className, TypeFormat.SOURCE);
        int lastIndex = sourceName.lastIndexOf('.');
        if (lastIndex < 0) {
            return "";
        }

        return sourceName.substring(0, lastIndex);
    }

    /**
     * Get the binary format wrapper class name for a given primitive.
     * 
     * @param className
     * @return wrapper class name or null if not found
     */
    public static @Nullable String getWrapper(String className) {
        String internalName = toFormat(className, TypeFormat.INTERNAL);
        String wrapperName = internalPrimitiveToWrapper.get(getComponentBase(internalName));
        if (null == wrapperName) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int dimensionCount = getDimensionCount(internalName);
        if (dimensionCount > 0) {
            for (int i = 0; i < dimensionCount; i++) {
                sb.append('[');
            }
            sb.append('L').append(wrapperName).append(';');

            return sb.toString();
        }

        return wrapperName;
    }

    /**
     * Get the internal format primitive class name for a given primitive wrapper.
     * 
     * @param className
     * @return primitive class name or null if not found
     */
    public static @Nullable String getPrimitive(String className) {
        String internalName = toFormat(className, TypeFormat.INTERNAL);
        String wrapperName = internalPrimitiveToWrapper.inverse().get(getComponentBase(internalName));
        if (null == wrapperName) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        int dimensionCount = getDimensionCount(internalName);
        if (dimensionCount > 0) {
            for (int i = 0; i < dimensionCount; i++) {
                sb.append('[');
            }
            sb.append('L').append(wrapperName).append(';');

            return sb.toString();
        }

        return wrapperName;
    }

    /**
     * Convert an internal class name format to binary format.
     * 
     * @param internalName
     * @return binary format class name
     */
    public static String internalToBinary(String internalName) {
        String internalPrimitive = internalPrimitiveToBinaryName.get(internalName);
        if (internalPrimitive != null) {
            return internalPrimitive;
        }

        if (internalName.startsWith("[")) {
            return internalName.replace("/", ".");
        } else {
            return internalName.substring(1, internalName.length() - 1).replace("/", ".");
        }
    }

    /**
     * Convert an internal class name format to source format.
     * 
     * @param internalName
     * @return source format class name
     */
    public static String internalToSource(String internalName) {
        // E.g. [Ljava/lang/Object; -> java.lang.Object[]
        StringBuilder sourceName = new StringBuilder();
        String baseClass = getComponentBase(internalName);
        String binaryPrimitive = internalPrimitiveToBinaryName.get(baseClass);
        if (binaryPrimitive != null) {
            sourceName.append(binaryPrimitive);
        } else {
            sourceName.append(baseClass.substring(1, baseClass.length() - 1).replace('/', '.'));
        }

        int dimensions = ClassNameUtils.getDimensionCount(internalName);
        for (int i = 0; i < dimensions; i++) {
            sourceName.append("[]");
        }

        return sourceName.toString();
    }

    /**
     * Works with all class formats.
     * 
     * @param className
     * @return true if class is primitive, otherwise false
     */
    public static boolean isPrimitive(String className) {
        String baseClass = getComponentBase(toFormat(className, TypeFormat.INTERNAL));

        return internalPrimitiveToBinaryName.containsKey(baseClass);
    }

    /**
     * Works with all class formats.
     * 
     * @param className
     * @return true if class is primitive of wrapper, otherwise false
     */
    public static boolean isPrimitiveOrWrapper(String className) {
        return isPrimitive(className) || isWrapper(className);
    }

    /**
     * Works with all class formats.
     * 
     * @param className
     * @return true if class is primitive wrapper, otherwise false
     */
    public static boolean isWrapper(String className) {
        return getWrapper(className) != null;
    }

    /**
     * Convert source format class name to binary format.
     * 
     * @param sourceName
     * @return binary format class name
     */
    public static String sourceToBinary(String sourceName) {
        String sourceBaseName = sourceName.replace("[]", "");
        StringBuilder sb = new StringBuilder();
        int dimensionCount = (sourceName.length() - sourceBaseName.length()) / 2;
        for (int i = 0; i < dimensionCount; i++) {
            sb.append('[');
        }

        String internalPrimitive = internalPrimitiveToBinaryName.inverse().get(sourceBaseName);
        if (internalPrimitive != null) {
            if (dimensionCount > 0) {
                sb.append(internalPrimitive);
            } else {
                sb.append(internalPrimitiveToBinaryName.get(internalPrimitive));
            }
        } else {
            if (dimensionCount > 0) {
                sb.append('L').append(sourceBaseName).append(';');
            } else {
                sb.append(sourceBaseName);
            }

        }

        return sb.toString();
    }

    /**
     * Convert source format class name to internal format.
     * 
     * @param sourceName
     * @return internal format class name
     */
    public static String sourceToInternal(String sourceName) {
        String sourceBaseName = sourceName.replace("[]", "");
        StringBuilder sb = new StringBuilder();
        int dimensionCount = (sourceName.length() - sourceBaseName.length()) / 2;
        for (int i = 0; i < dimensionCount; i++) {
            sb.append('[');
        }

        String internalPrimitive = internalPrimitiveToBinaryName.inverse().get(sourceBaseName);
        if (internalPrimitive != null) {
            sb.append(internalPrimitive);
        } else {
            sb.append('L').append(sourceBaseName.replace('.', '/')).append(';');
        }

        return sb.toString();
    }

    /**
     * Converts a class name of arbitrary format into any other format.
     * 
     * @param className
     * @param format
     * @return class name of format type
     */
    public static String toFormat(String className, TypeFormat format) {
        /*
         * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2
         * https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2.1
         * smali / internal = [Ljava/lang/Object; or Ljava/lang/Object;
         * binary = [Ljava.lang.Object; or java.lang.Object
         * source = java.lang.Object[] or java.lang.Object
         */

        String baseName = getComponentBase(className);
        if (baseName.contains("/") || internalPrimitiveToBinaryName.containsKey(baseName)) {
            // Internal / Smali format, e.g. [Ljava/lang/Object;, I, J, [Z
            switch (format) {
            case INTERNAL:
                return className;
            case BINARY:
                return internalToBinary(className);
            case SOURCE:
                return internalToSource(className);
            }
        } else {
            if (className.endsWith(";")) {
                // Binary format, e.g. [Ljava.lang.Object;, java.lang.Object, int, long, [Z
                switch (format) {
                case INTERNAL:
                    return binaryToInternal(className);
                case BINARY:
                    return className;
                case SOURCE:
                    return internalToSource(binaryToInternal(className));
                }
            } else {
                // Source format, e.g. java.lang.Object[], java.lang.Object, int, long, boolean[]
                // E.g. int, long, boolean
                switch (format) {
                case INTERNAL:
                    return sourceToInternal(className);
                case BINARY:
                    return sourceToBinary(className);
                case SOURCE:
                    return className;
                }
            }
        }

        return className;
    }

    /**
     * Get the internal format class name for a given Java class.
     * 
     * @param klazz
     * @return internal format class name
     */
    public static String toInternal(Class<?> klazz) {
        return binaryToInternal(klazz.getName());
    }

    /**
     * Get the internal format class name for an array of Java classes.
     * 
     * @param classes
     * @return list of internal format class names in the same order as arguments
     */
    public static List<String> toInternal(Class<?>... classes) {
        List<String> names = new LinkedList<String>();
        for (Class<?> klazz : classes) {
            names.add(toInternal(klazz));
        }

        return names;
    }

}

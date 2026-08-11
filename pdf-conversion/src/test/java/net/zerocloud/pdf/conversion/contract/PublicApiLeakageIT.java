package net.zerocloud.pdf.conversion.contract;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.Test;

public final class PublicApiLeakageIT {

    @Test
    public void publicAndProtectedSignaturesUseOnlyProjectOwnedOrJdkTypes()
            throws Exception {
        inspectJar(Paths.get(requiredProperty("providerArtifactPath")));
        inspectJar(Paths.get(requiredProperty("conversionArtifactPath")));
    }

    private static void inspectJar(Path artifact) throws Exception {
        try (JarFile jar = new JarFile(artifact.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!isProjectClass(entry)) {
                    continue;
                }
                Class<?> candidate = Class.forName(
                        toClassName(entry.getName()),
                        false,
                        PublicApiLeakageIT.class.getClassLoader());
                if (isPublicOrProtected(candidate.getModifiers())) {
                    inspectClass(candidate);
                }
            }
        }
    }

    private static boolean isProjectClass(JarEntry entry) {
        return !entry.isDirectory()
                && entry.getName().startsWith("net/zerocloud/pdf/")
                && entry.getName().endsWith(".class")
                && !entry.getName().endsWith("package-info.class");
    }

    private static String toClassName(String entryName) {
        return entryName.substring(0, entryName.length() - 6)
                .replace('/', '.');
    }

    private static void inspectClass(Class<?> type) {
        inspectType("superclass of " + type.getName(), type.getGenericSuperclass());
        inspectTypes("interfaces of " + type.getName(), type.getGenericInterfaces());
        inspectVariables("type variables of " + type.getName(), type.getTypeParameters());
        for (Field field : type.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())) {
                inspectType("field " + field, field.getGenericType());
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isPublicOrProtected(constructor.getModifiers())) {
                inspectTypes("parameters of " + constructor,
                        constructor.getGenericParameterTypes());
                inspectTypes("exceptions of " + constructor,
                        constructor.getGenericExceptionTypes());
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())) {
                inspectType("return type of " + method,
                        method.getGenericReturnType());
                inspectTypes("parameters of " + method,
                        method.getGenericParameterTypes());
                inspectTypes("exceptions of " + method,
                        method.getGenericExceptionTypes());
                inspectVariables("type variables of " + method,
                        method.getTypeParameters());
            }
        }
    }

    private static void inspectVariables(
            String location,
            TypeVariable<?>[] variables) {
        for (TypeVariable<?> variable : variables) {
            inspectTypes(location, variable.getBounds());
        }
    }

    private static void inspectTypes(String location, Type[] types) {
        for (Type type : types) {
            inspectType(location, type);
        }
    }

    private static void inspectType(String location, Type type) {
        if (type == null) {
            return;
        }
        if (type instanceof Class<?>) {
            Class<?> classType = (Class<?>) type;
            if (classType.isArray()) {
                inspectType(location, classType.getComponentType());
            } else if (!classType.isPrimitive()) {
                assertAllowed(location, classType.getName());
            }
            return;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterized = (ParameterizedType) type;
            inspectType(location, parameterized.getRawType());
            inspectType(location, parameterized.getOwnerType());
            inspectTypes(location, parameterized.getActualTypeArguments());
            return;
        }
        if (type instanceof GenericArrayType) {
            inspectType(location,
                    ((GenericArrayType) type).getGenericComponentType());
            return;
        }
        if (type instanceof WildcardType) {
            WildcardType wildcard = (WildcardType) type;
            inspectTypes(location, wildcard.getLowerBounds());
            inspectTypes(location, wildcard.getUpperBounds());
            return;
        }
        if (type instanceof TypeVariable<?>) {
            inspectTypes(location, ((TypeVariable<?>) type).getBounds());
            return;
        }
        throw new AssertionError(location + " has unsupported type " + type);
    }

    private static void assertAllowed(String location, String typeName) {
        boolean allowed = typeName.startsWith("java.")
                || typeName.startsWith("net.zerocloud.pdf.");
        assertTrue(location + " exposes non-project type " + typeName, allowed);
        assertTrue(location + " exposes process implementation type " + typeName,
                !typeName.equals("java.lang.Process")
                        && !typeName.equals("java.lang.ProcessBuilder"));
        assertTrue(location + " exposes network client type " + typeName,
                !typeName.startsWith("java.net.")
                        && !typeName.startsWith("javax.net."));
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }
}

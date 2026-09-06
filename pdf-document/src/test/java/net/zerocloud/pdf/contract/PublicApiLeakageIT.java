package net.zerocloud.pdf.contract;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
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

    private static final String[] FORBIDDEN_PACKAGES = {
        "org.apache.pdfbox",
        "org.apache.fontbox",
        "com.twelvemonkeys",
        "com.ibm.icu",
        "javax.imageio"
    };

    @Test
    public void rejectsIcuTypesInProtectedGenericSurfaces() {
        AssertionError failure = assertThrows(AssertionError.class, () -> inspectClass(IcuLeakProbe.class));
        assertTrue(failure.getMessage().contains("com.ibm.icu.text.BreakIterator"));
    }

    /** Deliberate test-only leak; never part of the scanned shipped artifact. */
    public static final class IcuLeakProbe {
        protected java.util.List<? extends com.ibm.icu.text.BreakIterator> boundaries;
    }

    @Test
    public void publicAndProtectedSignaturesContainNoBackendTypes()
            throws Exception {
        Path artifact = Paths.get(requiredProperty("artifactPath"));

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
        return entryName.substring(0, entryName.length() - ".class".length())
                .replace('/', '.');
    }

    private static void inspectClass(Class<?> type) throws IllegalAccessException {
        assertAllowedName("class " + type.getName(), type.getName());
        inspectAnnotations("class " + type.getName(), type);
        inspectType("superclass of " + type.getName(), type.getGenericSuperclass());
        inspectTypes("interfaces of " + type.getName(), type.getGenericInterfaces());
        inspectTypeVariables("type variables of " + type.getName(), type.getTypeParameters());

        for (Field field : type.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers())) {
                inspectType("field " + field, field.getGenericType());
                inspectAnnotations("field " + field, field);
                inspectPublicConstant(field);
            }
        }

        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isPublicOrProtected(constructor.getModifiers())) {
                inspectTypes("parameters of " + constructor, constructor.getGenericParameterTypes());
                inspectTypes("exceptions of " + constructor, constructor.getGenericExceptionTypes());
                inspectTypeVariables("type variables of " + constructor,
                        constructor.getTypeParameters());
                inspectAnnotations("constructor " + constructor, constructor);
                inspectParameterAnnotations("constructor " + constructor,
                        constructor.getParameterAnnotations());
            }
        }

        for (Method method : type.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers())) {
                inspectType("return type of " + method, method.getGenericReturnType());
                inspectTypes("parameters of " + method, method.getGenericParameterTypes());
                inspectTypes("exceptions of " + method, method.getGenericExceptionTypes());
                inspectTypeVariables("type variables of " + method, method.getTypeParameters());
                inspectAnnotations("method " + method, method);
                inspectParameterAnnotations("method " + method, method.getParameterAnnotations());
            }
        }
    }

    private static void inspectPublicConstant(Field field) throws IllegalAccessException {
        int modifiers = field.getModifiers();
        if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
            return;
        }
        if (field.getType() == String.class) {
            Object value = field.get(null);
            if (value != null) {
                assertAllowedName("constant " + field, value.toString());
            }
        }
        if (field.getType() == Class.class) {
            Object value = field.get(null);
            if (value instanceof Class<?>) {
                assertAllowedName("class constant " + field, ((Class<?>) value).getName());
            }
        }
    }

    private static void inspectAnnotations(String location, AnnotatedElement element) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            assertAllowedName(location + " annotation", annotation.annotationType().getName());
        }
    }

    private static void inspectParameterAnnotations(
            String location,
            Annotation[][] parameterAnnotations) {
        for (Annotation[] annotations : parameterAnnotations) {
            for (Annotation annotation : annotations) {
                assertAllowedName(location + " parameter annotation",
                        annotation.annotationType().getName());
            }
        }
    }

    private static void inspectTypeVariables(String location, TypeVariable<?>[] variables) {
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
            } else {
                assertAllowedName(location, classType.getName());
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
            inspectType(location, ((GenericArrayType) type).getGenericComponentType());
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
        assertAllowedName(location, type.getTypeName());
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void assertAllowedName(String location, String value) {
        for (String forbiddenPackage : FORBIDDEN_PACKAGES) {
            assertFalse(
                    location + " exposes " + value,
                    value.contains(forbiddenPackage));
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Missing system property: " + name);
        }
        return value;
    }
}

package mcphone1165fixture;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflection helpers.
 *
 * Minecraft member names are SRG (field_/func_) because the fixture is compiled straight against the
 * server SRG jar. Addon classes are looked up by name so the fixture does not need them at compile
 * time and a renamed member degrades to a SKIP instead of a NoSuchMethodError.
 */
final class Reflect {

    private Reflect() {}

    static ClassLoader loader() {
        return FixtureMod.class.getClassLoader();
    }

    /** Loads a class and runs its static initialiser; mixins for that target are already applied. */
    static Class<?> load(String name) {
        try {
            return Class.forName(name, true, loader());
        } catch (Throwable t) {
            return null;
        }
    }

    static Method method(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        Method m = owner.getDeclaredMethod(name, params);
        m.setAccessible(true);
        return m;
    }

    /** Finds a public or declared method anywhere up the superclass chain. */
    static Method methodInHierarchy(Class<?> owner, String name, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                return method(c, name, params);
            } catch (NoSuchMethodException ignored) {
                // keep walking up
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
    }

    static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // keep walking up the hierarchy
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    static Object invoke(Method m, Object target, Object... args) throws Exception {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw e;
        }
    }
}

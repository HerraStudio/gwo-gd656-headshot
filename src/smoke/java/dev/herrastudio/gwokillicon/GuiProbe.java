package dev.herrastudio.gwokillicon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reflection probes for the installed GD656 implementation, included only in smoke builds. */
public final class GuiProbe {
    private static final String LOGIC = "org.mods.gd656killicon.server.event.ServerEventLogic";
    private static final String HUD = "org.mods.gd656killicon.client.render.HudElementManager";

    private GuiProbe() {}

    /** Call on the client thread after GD656 has received the actual server packet. */
    public static List<Integer> scrollingKillTypes() throws ReflectiveOperationException {
        Object renderer = scrollingRenderer();
        List<Integer> result = new ArrayList<>();
        for (String queue : List.of("activeIcons", "pendingIcons")) {
            for (Object icon : (List<?>) field(renderer.getClass(), queue).get(renderer)) {
                result.add(field(icon.getClass(), "killType").getInt(icon));
            }
        }
        return List.copyOf(result);
    }

    public static boolean hasActiveScrollingIcon(int type) throws ReflectiveOperationException {
        Object renderer = scrollingRenderer();
        for (Object icon : (List<?>) field(renderer.getClass(), "activeIcons").get(renderer)) {
            if (field(icon.getClass(), "killType").getInt(icon) == type) return true;
        }
        return false;
    }

    public static String scrollingTextureKey(int type) throws ReflectiveOperationException {
        return textureMethod("getTextureKey", type);
    }

    public static String scrollingTexturePath(int type) throws ReflectiveOperationException {
        return textureMethod("getTexturePath", type);
    }

    /** Clear only test-client icon queues between independent fixture kills. */
    public static void clearScrollingIcons() throws ReflectiveOperationException {
        Object renderer = scrollingRenderer();
        for (String queue : List.of("activeIcons", "pendingIcons")) {
            ((List<?>) field(renderer.getClass(), queue).get(renderer)).clear();
        }
    }

    /** Call on the server thread immediately after a nonlethal hit. */
    public static Integer recordedDamageType(UUID victim) throws ReflectiveOperationException {
        Map<?, ?> damageTypes = (Map<?, ?>) field(Class.forName(LOGIC), "lastDamageType").get(null);
        return (Integer) damageTypes.get(victim);
    }

    /** A lethal hit removes lastDamageType; its final classification lives in PendingKill. */
    public static List<PendingKill> pendingKills() throws ReflectiveOperationException {
        List<?> pending = (List<?>) field(Class.forName(LOGIC), "pendingKills").get(null);
        List<PendingKill> result = new ArrayList<>();
        for (Object kill : pending) {
            Class<?> type = kill.getClass();
            result.add(new PendingKill(
                    (UUID) field(type, "victimId").get(kill),
                    field(type, "damageType").getInt(kill),
                    field(type, "delay").getInt(kill)
            ));
        }
        return List.copyOf(result);
    }

    private static Object scrollingRenderer() throws ReflectiveOperationException {
        Map<?, ?> categories = (Map<?, ?>) field(Class.forName(HUD), "renderers").get(null);
        Object category = categories.get("kill_icon");
        if (!(category instanceof Map<?, ?> renderers)) {
            throw new IllegalStateException("GD656 kill_icon renderers are not initialized");
        }
        Object renderer = renderers.get("scrolling");
        if (renderer == null) throw new IllegalStateException("GD656 scrolling renderer is absent");
        return renderer;
    }

    private static String textureMethod(String name, int type) throws ReflectiveOperationException {
        Object renderer = scrollingRenderer();
        Method method = renderer.getClass().getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        return (String) method.invoke(renderer, type);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    public record PendingKill(UUID victimId, int damageType, int delay) {}
}

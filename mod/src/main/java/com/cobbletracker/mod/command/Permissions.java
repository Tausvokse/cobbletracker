package com.cobbletracker.mod.command;

import com.cobbletracker.mod.CobbleTracker;
import java.lang.reflect.Method;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Optional LuckPerms checks with vanilla permission-level fallback. */
public final class Permissions {

    private Permissions() {
    }

    public static final int LEVEL_ANYONE = 0;
    public static final int LEVEL_OPERATOR = 2;

    public static final String GUI = "cobbletracker.command.gui";
    public static final String NOTIFY = "cobbletracker.command.notify";
    public static final String WAYPOINT = "cobbletracker.command.waypoint";
    public static final String THEME = "cobbletracker.command.theme";
    public static final String LASTLEGEND = "cobbletracker.command.lastlegend";
    public static final String CHECKLEGENDARY = "cobbletracker.command.checklegendary";
    public static final String SPAWNLEGENDARY = "cobbletracker.command.spawnlegendary";
    public static final String RELOAD = "cobbletracker.command.reload";
    public static final String ADMIN = "cobbletracker.command.admin";
    public static final String FAKEHIT = "cobbletracker.command.fakehit";

    private static volatile State state = State.UNRESOLVED;
    private static Method getUser;
    private static Method getCachedData;
    private static Method getPermissionData;
    private static Method checkPermission;
    private static Object userManager;

    private enum State { UNRESOLVED, AVAILABLE, ABSENT }

    public static boolean check(CommandSourceStack source, String node, int fallbackLevel) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Boolean fromLuckPerms = luckPerms(player, node);
            if (fromLuckPerms != null) {
                return fromLuckPerms;
            }
        }
        return source.hasPermission(fallbackLevel);
    }

    public static boolean check(ServerPlayer player, String node, int fallbackLevel) {
        Boolean fromLuckPerms = luckPerms(player, node);
        return fromLuckPerms != null ? fromLuckPerms : player.hasPermissions(fallbackLevel);
    }

    private static Boolean luckPerms(ServerPlayer player, String node) {
        if (!resolve()) {
            return null;
        }
        try {
            Object user = getUser.invoke(userManager, player.getUUID());
            if (user == null) {
                return null;  
            }
            Object data = getPermissionData.invoke(getCachedData.invoke(user));
            Object tristate = checkPermission.invoke(data, node);
            String result = String.valueOf(tristate);
            if ("TRUE".equalsIgnoreCase(result)) {
                return Boolean.TRUE;
            }
            if ("FALSE".equalsIgnoreCase(result)) {
                return Boolean.FALSE;
            }
            return null;  
        } catch (Throwable t) {
            CobbleTracker.LOGGER.warn("LuckPerms lookup failed - falling back to operator levels", t);
            state = State.ABSENT;
            return null;
        }
    }

    private static boolean resolve() {
        State current = state;
        if (current == State.AVAILABLE) {
            return true;
        }
        if (current == State.ABSENT) {
            return false;
        }
        synchronized (Permissions.class) {
            if (state != State.UNRESOLVED) {
                return state == State.AVAILABLE;
            }
            try {
                Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object api = provider.getMethod("get").invoke(null);
                userManager = Class.forName("net.luckperms.api.LuckPerms").getMethod("getUserManager").invoke(api);
                getUser = Class.forName("net.luckperms.api.model.user.UserManager")
                        .getMethod("getUser", java.util.UUID.class);
                Class<?> userClass = Class.forName("net.luckperms.api.model.user.User");
                getCachedData = userClass.getMethod("getCachedData");
                getPermissionData = Class.forName("net.luckperms.api.cacheddata.CachedDataManager")
                        .getMethod("getPermissionData");
                checkPermission = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData")
                        .getMethod("checkPermission", String.class);
                state = State.AVAILABLE;
                CobbleTracker.LOGGER.info("LuckPerms detected - cobbletracker.command.* nodes are active");
            } catch (ClassNotFoundException e) {
                state = State.ABSENT;  
            } catch (Throwable t) {
                state = State.ABSENT;
                CobbleTracker.LOGGER.warn("LuckPerms is present but its API couldn't be reached - "
                        + "falling back to operator levels", t);
            }
            return state == State.AVAILABLE;
        }
    }
}

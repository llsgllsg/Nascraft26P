package me.bounser.nascraft.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public final class FoliaScheduler {

    private static final boolean FOLIA;

    private static Method GLOBAL_REGION_SCHEDULER_GET;
    private static Method GLOBAL_RUN;
    private static Method GLOBAL_RUN_DELAYED;
    private static Method GLOBAL_RUN_AT_FIXED_RATE;
    private static Method GLOBAL_EXECUTE;

    private static Method ASYNC_SCHEDULER_GET;
    private static Method ASYNC_RUN_NOW;
    private static Method ASYNC_RUN_DELAYED;
    private static Method ASYNC_RUN_AT_FIXED_RATE;

    private static Method REGION_SCHEDULER_GET;
    private static Method REGION_RUN_LOCATION;
    private static Method REGION_EXECUTE_LOCATION;

    private static Method ENTITY_GET_SCHEDULER;
    private static Method ENTITY_RUN;
    private static Method ENTITY_RUN_DELAYED;
    private static Method ENTITY_EXECUTE;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        FOLIA = folia;

        if (FOLIA) {
            try {
                Class<?> serverClass = Bukkit.getServer().getClass();

                GLOBAL_REGION_SCHEDULER_GET = serverClass.getMethod("getGlobalRegionScheduler");
                Class<?> globalRegionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
                GLOBAL_RUN = globalRegionScheduler.getMethod("run", Plugin.class, java.util.function.Consumer.class);
                GLOBAL_RUN_DELAYED = globalRegionScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class);
                GLOBAL_RUN_AT_FIXED_RATE = globalRegionScheduler.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class);
                GLOBAL_EXECUTE = globalRegionScheduler.getMethod("execute", Plugin.class, Runnable.class);

                ASYNC_SCHEDULER_GET = serverClass.getMethod("getAsyncScheduler");
                Class<?> asyncScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.AsyncScheduler");
                ASYNC_RUN_NOW = asyncScheduler.getMethod("runNow", Plugin.class, java.util.function.Consumer.class);
                ASYNC_RUN_DELAYED = asyncScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, long.class, TimeUnit.class);
                ASYNC_RUN_AT_FIXED_RATE = asyncScheduler.getMethod("runAtFixedRate", Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class);

                REGION_SCHEDULER_GET = serverClass.getMethod("getRegionScheduler");
                Class<?> regionScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.RegionScheduler");
                REGION_RUN_LOCATION = regionScheduler.getMethod("run", Plugin.class, Location.class, java.util.function.Consumer.class);
                REGION_EXECUTE_LOCATION = regionScheduler.getMethod("execute", Plugin.class, Location.class, Runnable.class);

                ENTITY_GET_SCHEDULER = Entity.class.getMethod("getScheduler");
                Class<?> entityScheduler = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
                ENTITY_RUN = entityScheduler.getMethod("run", Plugin.class, java.util.function.Consumer.class, Runnable.class);
                ENTITY_RUN_DELAYED = entityScheduler.getMethod("runDelayed", Plugin.class, java.util.function.Consumer.class, Runnable.class, long.class);
                ENTITY_EXECUTE = entityScheduler.getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Failed to bootstrap Folia scheduler reflection", e);
            }
        }
    }

    private FoliaScheduler() {}

    public static boolean isFolia() { return FOLIA; }

    // Run on the global region thread (Folia) or the main thread (Paper).
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = GLOBAL_REGION_SCHEDULER_GET.invoke(Bukkit.getServer());
                GLOBAL_EXECUTE.invoke(scheduler, plugin, task);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runGlobalLater(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) delayTicks = 1;
        if (FOLIA) {
            try {
                Object scheduler = GLOBAL_REGION_SCHEDULER_GET.invoke(Bukkit.getServer());
                GLOBAL_RUN_DELAYED.invoke(scheduler, plugin, asConsumer(task), delayTicks);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    public static void runGlobalTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks <= 0) initialDelayTicks = 1;
        if (periodTicks <= 0) periodTicks = 1;
        if (FOLIA) {
            try {
                Object scheduler = GLOBAL_REGION_SCHEDULER_GET.invoke(Bukkit.getServer());
                GLOBAL_RUN_AT_FIXED_RATE.invoke(scheduler, plugin, asConsumer(task), initialDelayTicks, periodTicks);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // Async

    public static void runAsync(Plugin plugin, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = ASYNC_SCHEDULER_GET.invoke(Bukkit.getServer());
                ASYNC_RUN_NOW.invoke(scheduler, plugin, asConsumer(task));
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        if (delayTicks <= 0) delayTicks = 1;
        if (FOLIA) {
            try {
                Object scheduler = ASYNC_SCHEDULER_GET.invoke(Bukkit.getServer());
                ASYNC_RUN_DELAYED.invoke(scheduler, plugin, asConsumer(task), delayTicks * 50L, TimeUnit.MILLISECONDS);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
        }
    }

    public static void runAsyncTimer(Plugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (initialDelayTicks <= 0) initialDelayTicks = 1;
        if (periodTicks <= 0) periodTicks = 1;
        if (FOLIA) {
            try {
                Object scheduler = ASYNC_SCHEDULER_GET.invoke(Bukkit.getServer());
                ASYNC_RUN_AT_FIXED_RATE.invoke(scheduler, plugin, asConsumer(task), initialDelayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks);
        }
    }

    // Region

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = REGION_SCHEDULER_GET.invoke(Bukkit.getServer());
                REGION_EXECUTE_LOCATION.invoke(scheduler, plugin, location, task);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    // Entity-bound

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (FOLIA) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                ENTITY_RUN.invoke(scheduler, plugin, asConsumer(task), null);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static void runAtEntityLater(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (delayTicks <= 0) delayTicks = 1;
        if (FOLIA) {
            try {
                Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                ENTITY_RUN_DELAYED.invoke(scheduler, plugin, asConsumer(task), null, delayTicks);
            } catch (ReflectiveOperationException e) {
                throw rethrow(e);
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    // Helpers

    private static java.util.function.Consumer<Object> asConsumer(Runnable task) {
        return ignored -> task.run();
    }

    private static RuntimeException rethrow(ReflectiveOperationException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException) return (RuntimeException) cause;
        return new RuntimeException(cause);
    }
}

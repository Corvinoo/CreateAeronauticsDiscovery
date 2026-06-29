package me.corvino.aeronauticsdiscovery.scheduler;

import me.corvino.aeronauticsdiscovery.CreateAeronauticsDiscovery;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TaskScheduler {

    private static final class Holder {
        static final TaskScheduler INSTANCE = new TaskScheduler();
    }

    private final ConcurrentLinkedDeque<ScheduledTask> syncTasks;
    private ScheduledExecutorService asyncExecutor;
    private volatile MinecraftServer server;
    private volatile boolean running;

    public static void setup() {
        // trigger class loading or this thing will never be running (or too late)
        CreateAeronauticsDiscovery.LOGGER.info("Loading task scheduler..");
    }

    static {
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerTick);
    }

    private TaskScheduler() {
        this.syncTasks = new ConcurrentLinkedDeque<>();
        this.asyncExecutor = createAsyncExecutor();
        this.running = false;
    }


    public static TaskScheduler getInstance() {
        return Holder.INSTANCE;
    }

    private static ScheduledExecutorService createAsyncExecutor() {
        return Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofPlatform()
                        .name("scheduler-async-", 0)
                        .daemon(true)
                        .factory()
        );
    }

    public CompletableFuture<Void> runSyncTask(Runnable task) {
        return scheduleSyncTask(task, 0);
    }

    public CompletableFuture<Void> runSyncTaskLater(Runnable task, long delayTicks) {
        return scheduleSyncTask(task, delayTicks);
    }

    public CompletableFuture<Void> runSyncTask(Consumer<MinecraftServer> task) {
        return scheduleSyncTask(() -> {
            if (server != null) {
                task.accept(server);
            }
        }, 0);
    }

    public CompletableFuture<Void> runAsyncTask(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }

    public CompletableFuture<Void> runAsyncTaskLater(Runnable task, long delayMs) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        asyncExecutor.schedule(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        return future;
    }

    public <T> CompletableFuture<T> runAsyncTask(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, asyncExecutor);
    }

    public <T> CompletableFuture<T> runAsyncWithSyncCallback(
            Supplier<T> asyncTask,
            Consumer<T> syncCallback) {
        return runAsyncTask(asyncTask)
                .thenCompose(result -> runSyncTask(() -> syncCallback.accept(result))
                        .thenApply(v -> result));
    }

    public Cancellable runSyncTaskRepeating(Runnable task, long initialDelay, long periodTicks) {
        RepeatingTask repeatingTask = new RepeatingTask(task, initialDelay, periodTicks);
        syncTasks.add(repeatingTask);
        return repeatingTask;
    }

    private CompletableFuture<Void> scheduleSyncTask(Runnable task, long delayTicks) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ScheduledTask scheduledTask = new ScheduledTask(
                () -> {
                    try {
                        task.run();
                        future.complete(null);
                    } catch (Exception e) {
                        future.completeExceptionally(e);
                    }
                },
                delayTicks,
                false
        );
        syncTasks.add(scheduledTask);
        return future;
    }

    public Cancellable runAsyncTaskRepeating(Runnable task, long initialDelayMs, long periodMs) {
        RepeatingAsyncTask repeatingTask = new RepeatingAsyncTask(task, initialDelayMs, periodMs);
        repeatingTask.start(asyncExecutor);
        return repeatingTask;
    }

    public CompletableFuture<Void> runAsyncTaskRepeatingUntil(
            Consumer<CompletableFuture<Void>> task,
            long periodMs,
            long timeoutMs) {

        CompletableFuture<Void> resultFuture = new CompletableFuture<>();
        resultFuture.orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        var cancellable = runAsyncTaskRepeating(() -> {
            if (resultFuture.isDone()) {
                return;
            }
            task.accept(resultFuture);
        }, 0, periodMs);


        resultFuture.whenComplete((v, ex) -> cancellable.cancel());

        return resultFuture;
    }

    private void tick() {
        if (!running || server == null) return;

        var iterator = syncTasks.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            task.tick();

            if (task.isReady() && !task.isCancelled()) {
                task.run();

                if (task.isRepeating()) {
                    task.reset();
                } else {
                    iterator.remove();
                }
            } else if (task.isCancelled()) {
                iterator.remove();
            }
        }
    }

    public void shutdown() {
        running = false;
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        syncTasks.clear();
    }

    private static void onServerStarted(ServerStartedEvent event) {
        TaskScheduler instance = getInstance();
        instance.server = event.getServer();
        instance.running = true;

        instance.reset();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        getInstance().shutdown();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        getInstance().tick();
    }

    private synchronized void reset() {
        syncTasks.clear();

        if (asyncExecutor.isShutdown() || asyncExecutor.isTerminated()) {
            asyncExecutor = createAsyncExecutor();
        }
    }

    private static class ScheduledTask {
        private final Runnable task;
        private final long delayTicks;
        private final boolean repeating;
        private long currentTick;
        private boolean cancelled;

        ScheduledTask(Runnable task, long delayTicks, boolean repeating) {
            this.task = task;
            this.delayTicks = delayTicks;
            this.repeating = repeating;
            this.currentTick = 0;
            this.cancelled = false;
        }

        void tick() {
            if (!cancelled) {
                currentTick++;
            }
        }

        boolean isReady() {
            return currentTick >= delayTicks;
        }

        void run() {
            if (!cancelled) {
                task.run();
            }
        }

        void reset() {
            currentTick = 0;
        }

        protected void resetCounter() {
            this.currentTick = 0;
        }

        void cancel() {
            cancelled = true;
        }

        boolean isCancelled() {
            return cancelled;
        }

        boolean isRepeating() {
            return repeating;
        }

        protected long getCurrentTick() {
            return currentTick;
        }
    }

    private static class RepeatingTask extends ScheduledTask implements Cancellable {
        private boolean cancelled;

        RepeatingTask(Runnable task, long initialDelay, long periodTicks) {
            super(task, initialDelay, true);
            this.cancelled = false;
        }

        @Override
        void reset() {
            resetCounter();
        }

        @Override
        public void cancel() {
            this.cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        boolean isRepeating() {
            return true;
        }
    }

    private static class RepeatingAsyncTask implements Cancellable {
        private final Runnable task;
        private final long initialDelayMs;
        private final long periodMs;
        private volatile boolean cancelled;
        private ScheduledFuture<?> future;

        RepeatingAsyncTask(Runnable task, long initialDelayMs, long periodMs) {
            this.task = task;
            this.initialDelayMs = initialDelayMs;
            this.periodMs = periodMs;
            this.cancelled = false;
        }

        void start(ScheduledExecutorService executor) {
            future = executor.scheduleAtFixedRate(
                    () -> {
                        if (!cancelled) {
                            task.run();
                        }
                    },
                    initialDelayMs,
                    periodMs,
                    TimeUnit.MILLISECONDS
            );
        }

        @Override
        public void cancel() {
            cancelled = true;
            if (future != null) {
                future.cancel(false);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    public interface Cancellable {
        void cancel();
        boolean isCancelled();
    }
}
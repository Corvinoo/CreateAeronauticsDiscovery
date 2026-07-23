package me.corvino.aeronauticsdiscovery.scheduler;

import me.corvino.aeronauticsdiscovery.util.ModLog;
import static me.corvino.aeronauticsdiscovery.util.LogCategory.GENERAL;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TaskScheduler {
    private static final TaskScheduler INSTANCE = new TaskScheduler();

    public static TaskScheduler getInstance() {
        return INSTANCE;
    }

    /**
     * Call during mod init to trigger class loading before the first server tick.
     */
    public static void setup() {
        ModLog.info(GENERAL, "Loading task scheduler...");
    }

    static {
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerStarted);
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TaskScheduler::onServerTick);
    }

    private final ConcurrentLinkedDeque<SyncTask> syncTasks = new ConcurrentLinkedDeque<>();
    private volatile ScheduledExecutorService asyncExecutor = buildAsyncExecutor();
    private volatile MinecraftServer server;
    private volatile boolean running;

    private TaskScheduler() {
    }

    /**
     * Runs {@code task} on the next server tick.
     */
    public CompletableFuture<Void> runSync(Runnable task) {
        return scheduleSync(task, 0, 0);
    }

    /**
     * Runs {@code task} on the server thread, supplying the current server instance.
     */
    public CompletableFuture<Void> runSync(Consumer<MinecraftServer> task) {
        return scheduleSync(() -> {
            if (server != null) task.accept(server);
        }, 0, 0);
    }

    /**
     * Runs {@code task} on the server tick after {@code delayTicks} have elapsed.
     */
    public CompletableFuture<Void> runSyncLater(Runnable task, long delayTicks) {
        return scheduleSync(task, delayTicks, 0);
    }

    /**
     * Runs {@code task} repeatedly on the server thread.
     *
     * @param initialDelay ticks before the first execution
     * @param period       ticks between subsequent executions
     */
    public Cancellable runSyncRepeating(Runnable task, long initialDelay, long period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
        SyncTask t = new SyncTask(task, initialDelay, period);
        syncTasks.add(t);
        return t;
    }


    public CompletableFuture<Void> runSyncRepeatingUntil(Consumer<CompletableFuture<Void>> task,
                                                         long periodTicks,
                                                         long timeoutTicks) {
        long timeoutMs = timeoutTicks * 50L; // 1 tick = 50ms at 20 TPS (1/20s)
        CompletableFuture<Void> resultFuture =
                new CompletableFuture<Void>().orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        Cancellable poll = runSyncRepeating(() -> {
            if (!resultFuture.isDone()) task.accept(resultFuture);
        }, 0, periodTicks);

        resultFuture.whenComplete((v, ex) -> poll.cancel());
        return resultFuture;
    }

    public CompletableFuture<Void> runAsync(Runnable task) {
        return CompletableFuture.runAsync(task, asyncExecutor);
    }

    public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, asyncExecutor);
    }

    /**
     * Runs {@code task} off-thread after {@code delayMs} milliseconds.
     */
    public CompletableFuture<Void> runAsyncLater(Runnable task, long delayMs) {
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

    /**
     * Runs an async task then delivers the result to a sync callback.
     */
    public <T> CompletableFuture<T> runAsyncWithSyncCallback(Supplier<T> asyncTask,
                                                             Consumer<T> syncCallback) {
        return runAsync(asyncTask)
                .thenCompose(result ->
                        runSync(() -> syncCallback.accept(result)).thenApply(v -> result));
    }

    public Cancellable runAsyncRepeating(Runnable task, long initialDelayMs, long periodMs) {
        AsyncRepeatingTask t = new AsyncRepeatingTask(task, initialDelayMs, periodMs);
        t.start(asyncExecutor);
        return t;
    }

    /**
     * Polls {@code task} every {@code periodMs} ms off-thread until the supplied
     * future is completed (by the task itself) or {@code timeoutMs} elapses.
     *
     * <p>The consumer receives the shared future; complete it to stop polling.
     */
    public CompletableFuture<Void> runAsyncRepeatingUntil(Consumer<CompletableFuture<Void>> task,
                                                          long periodMs,
                                                          long timeoutMs) {
        // orTimeout returns a new future — keep that one so the timeout actually fires.
        CompletableFuture<Void> resultFuture =
                new CompletableFuture<Void>().orTimeout(timeoutMs, TimeUnit.MILLISECONDS);

        Cancellable poll = runAsyncRepeating(() -> {
            if (!resultFuture.isDone()) task.accept(resultFuture);
        }, 0, periodMs);

        resultFuture.whenComplete((v, ex) -> poll.cancel());
        return resultFuture;
    }

    private static void onServerStarted(ServerStartedEvent event) {
        TaskScheduler s = getInstance();
        s.server = event.getServer();
        s.running = true;
        s.restart();
    }

    private static void onServerStopping(ServerStoppingEvent event) {
        getInstance().shutdown();
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        getInstance().tick();
    }

    private void tick() {
        if (!running || server == null) return;

        for (var it = syncTasks.iterator(); it.hasNext(); ) {
            SyncTask task = it.next();

            if (task.isCancelled()) {
                it.remove();
                continue;
            }

            if (task.tickAndReady()) {
                task.run();
                if (task.isRepeating()) task.resetCounter();
                else it.remove();
            }
        }
    }

    private void shutdown() {
        running = false;
        syncTasks.clear();
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS))
                asyncExecutor.shutdownNow();
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void restart() {
        syncTasks.clear();
        ScheduledExecutorService old = asyncExecutor;
        asyncExecutor = buildAsyncExecutor();
        if (!old.isShutdown()) old.shutdownNow();
    }

    private CompletableFuture<Void> scheduleSync(Runnable body, long delayTicks, long period) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        syncTasks.add(new SyncTask(() -> {
            try {
                body.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }, delayTicks, period));
        return future;
    }

    private static ScheduledExecutorService buildAsyncExecutor() {
        return Executors.newScheduledThreadPool(
                Runtime.getRuntime().availableProcessors(),
                Thread.ofPlatform().name("scheduler-async-", 0).daemon(true).factory());
    }

    /**
     * A sync task that is both one-shot and repeating depending on whether
     * {@code period > 0}
     */
    private static final class SyncTask implements Cancellable {
        private final Runnable body;
        private final long delay;
        private final long period;  // 0 = one-shot; >0 = repeating
        private long counter;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        SyncTask(Runnable body, long delay, long period) {
            this.body = body;
            this.delay = delay;
            this.period = period;
            this.counter = 0;
        }

        boolean tickAndReady() {
            return ++counter > (isRepeating() && counter > delay ? delay + period - 1 : delay);
        }

        void run() {
            if (!cancelled.get()) body.run();
        }

        void resetCounter() {
            counter = delay; // next threshold will be delay + period
        }

        boolean isRepeating() {
            return period > 0;
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    private static final class AsyncRepeatingTask implements Cancellable {
        private final Runnable body;
        private final long initialDelayMs;
        private final long periodMs;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private ScheduledFuture<?> future;

        AsyncRepeatingTask(Runnable body, long initialDelayMs, long periodMs) {
            this.body = body;
            this.initialDelayMs = initialDelayMs;
            this.periodMs = periodMs;
        }

        void start(ScheduledExecutorService executor) {
            future = executor.scheduleAtFixedRate(
                    () -> {
                        if (!cancelled.get()) body.run();
                    },
                    initialDelayMs, periodMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
            if (future != null) future.cancel(false);
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get();
        }
    }

    public interface Cancellable {
        void cancel();

        boolean isCancelled();
    }
}
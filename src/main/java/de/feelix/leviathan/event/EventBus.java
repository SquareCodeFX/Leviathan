package de.feelix.leviathan.event;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.exceptions.ApiMisuseException;
import de.feelix.leviathan.exceptions.EventBusException;
import de.feelix.leviathan.util.Preconditions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Simple, thread-safe EventBus supporting sync/async dispatch, priorities, and cancellation.
 */
public class EventBus {

    private static final Comparator<Subscriber> PRIORITY_DESC =
            Comparator.comparingInt((Subscriber s) -> s.priority.weight()).reversed();

    private final Map<Class<?>, CopyOnWriteArrayList<Subscriber>> byEventType = new ConcurrentHashMap<>();
    private final Map<Listener, List<Subscriber>> byListener = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public EventBus() {
        this(defaultExecutor("Leviathan-EventBus"));
    }

    public EventBus(@NotNull ExecutorService executor) {
        this.executor = Preconditions.checkNotNull(executor, "executor");
    }

    private static ExecutorService defaultExecutor(String poolName) {
        AtomicLong idx = new AtomicLong(1);
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, poolName + "-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        return Executors.newCachedThreadPool(tf);
    }

    /**
     * Register all {@link Subscribe}-annotated listener methods on the given instance.
     */
    public void register(@NotNull Listener listener) {
        Preconditions.checkNotNull(listener, "listener");
        List<Subscriber> found = new ArrayList<>();
        Class<?> type = listener.getClass();
        for (Method m : getAllDeclaredMethods(type)) {
            Subscribe ann = m.getAnnotation(Subscribe.class);
            if (ann == null) continue;
            validateListenerMethod(m);
            Class<?> eventType = m.getParameterTypes()[0];
            @SuppressWarnings("unchecked")
            Class<? extends Event> cast = (Class<? extends Event>) eventType;
            Subscriber sub = new Subscriber(listener, m, ann.priority(), ann.async(), ann.ignoreCancelled(), cast);
            byEventType.computeIfAbsent(cast, k -> new CopyOnWriteArrayList<>()).add(sub);
            found.add(sub);
        }
        if (!found.isEmpty()) {
            // Sort each affected event type list by priority
            found.stream().map(s -> s.eventType).collect(Collectors.toSet()).forEach(et -> {
                CopyOnWriteArrayList<Subscriber> list = byEventType.get(et);
                if (list != null) {
                    list.sort(PRIORITY_DESC);
                }
            });
            byListener.put(listener, List.copyOf(found));
        }
    }

    /**
     * Unregister all listeners belonging to the given instance.
     */
    public void unregister(@NotNull Listener listener) {
        Preconditions.checkNotNull(listener, "listener");
        List<Subscriber> subs = byListener.remove(listener);
        if (subs == null || subs.isEmpty()) return;
        Set<Class<?>> affected = subs.stream().map(s -> s.eventType).collect(Collectors.toSet());
        for (Class<?> et : affected) {
            CopyOnWriteArrayList<Subscriber> list = byEventType.get(et);
            if (list != null) {
                list.removeIf(s -> s.owner == listener);
            }
        }
    }

    /**
     * Post an event synchronously on the current thread. Handlers marked async=true
     * are executed on the async executor and do not affect synchronous cancellation order.
     *
     * @return the same event instance (for fluent usage)
     */
    public <E extends Event> @NotNull E post(@NotNull E event) {
        Preconditions.checkNotNull(event, "event");
        List<Subscriber> subs = resolveSubscribers(event.getClass());
        if (subs.isEmpty()) return event;
        for (Subscriber s : subs) {
            if (!shouldInvokeForCancellation(s, event)) continue;
            if (s.async) {
                executor.execute(() -> safeInvoke(s, event));
            } else {
                safeInvoke(s, event);
            }
        }
        return event;
    }

    /**
     * Post an event entirely asynchronously. All listeners are invoked in priority order on the bus executor,
     * allowing cancellation to consistently affect later handlers.
     */
    public <E extends Event> @NotNull CompletableFuture<E> postAsync(@NotNull E event) {
        Preconditions.checkNotNull(event, "event");
        List<Subscriber> subs = resolveSubscribers(event.getClass());
        if (subs.isEmpty()) return CompletableFuture.completedFuture(event);
        return CompletableFuture.supplyAsync(() -> {
            for (Subscriber s : subs) {
                if (!shouldInvokeForCancellation(s, event)) continue;
                safeInvoke(s, event);
            }
            return event;
        }, executor);
    }

    /** Stop the executor if it was created by this bus. Users may manage lifecycle externally. */
    public void shutdown() {
        executor.shutdown();
    }

    private boolean shouldInvokeForCancellation(Subscriber s, Event event) {
        if (event instanceof Cancellable c) {
            return !c.isCancelled() || s.ignoreCancelled;
        }
        return true;
    }

    private void safeInvoke(Subscriber s, Event event) {
        try {
            s.method.invoke(s.owner, event);
        } catch (IllegalAccessException e) {
            throw new EventBusException("Listener method not accessible: " + s.method, e);
        } catch (InvocationTargetException e) {
            Throwable target = e.getTargetException() == null ? e : e.getTargetException();
            throw new EventBusException("Listener threw exception: " + s.method + " -> " + target, target);
        }
    }

    private void validateListenerMethod(Method m) {
        if (Modifier.isStatic(m.getModifiers())) {
            throw new ApiMisuseException("@Subscribe methods must be non-static: " + m);
        }
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 1) {
            throw new ApiMisuseException("@Subscribe methods must take exactly one parameter: " + m);
        }
        if (!Event.class.isAssignableFrom(params[0])) {
            throw new ApiMisuseException("@Subscribe parameter must implement Event: " + m);
        }
        try {
            m.setAccessible(true);
        } catch (Exception ignored) {
            // best-effort: will still work for public methods
        }
    }

    private List<Subscriber> resolveSubscribers(Class<?> eventClass) {
        // Collect subscribers for event class and its supertypes/interfaces that are Events
        LinkedHashSet<Subscriber> all = new LinkedHashSet<>();
        for (Class<?> type : eventHierarchy(eventClass)) {
            CopyOnWriteArrayList<Subscriber> list = byEventType.get(type);
            if (list != null) all.addAll(list);
        }
        if (all.isEmpty()) return Collections.emptyList();
        List<Subscriber> res = new ArrayList<>(all);
        res.sort(PRIORITY_DESC);
        return res;
    }

    private List<Method> getAllDeclaredMethods(Class<?> type) {
        List<Method> out = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            Collections.addAll(out, c.getDeclaredMethods());
            c = c.getSuperclass();
        }
        return out;
    }

    private List<Class<?>> eventHierarchy(Class<?> type) {
        List<Class<?>> out = new ArrayList<>();
        Class<?> c = type;
        while (c != null && c != Object.class) {
            if (Event.class.isAssignableFrom(c)) out.add(c);
            c = c.getSuperclass();
        }
        // also consider interfaces implemented that extend Event
        // maintain relative order: concrete class first, then interfaces
        Set<Class<?>> ifaces = new LinkedHashSet<>();
        collectEventInterfaces(type, ifaces);
        for (Class<?> itf : ifaces) {
            if (Event.class.isAssignableFrom(itf)) out.add(itf);
        }
        return out;
    }

    private void collectEventInterfaces(Class<?> c, Set<Class<?>> out) {
        if (c == null || c == Object.class) return;
        for (Class<?> itf : c.getInterfaces()) {
            out.add(itf);
            collectEventInterfaces(itf, out);
        }
        collectEventInterfaces(c.getSuperclass(), out);
    }

    private static final class Subscriber {
        final Listener owner;
        final Method method;
        final EventPriority priority;
        final boolean async;
        final boolean ignoreCancelled;
        final Class<? extends Event> eventType;

        Subscriber(Listener owner, Method method, EventPriority priority, boolean async, boolean ignoreCancelled,
                   Class<? extends Event> eventType) {
            this.owner = Objects.requireNonNull(owner);
            this.method = Objects.requireNonNull(method);
            this.priority = Objects.requireNonNull(priority);
            this.async = async;
            this.ignoreCancelled = ignoreCancelled;
            this.eventType = Objects.requireNonNull(eventType);
        }
    }
}

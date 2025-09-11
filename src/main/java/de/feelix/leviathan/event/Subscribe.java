package de.feelix.leviathan.event;

import java.lang.annotation.*;

/**
 * Marks a method as an event listener.
 *
 * Method requirements:
 * - must be non-static
 * - must accept exactly one parameter that implements Event
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {
    EventPriority priority() default EventPriority.NORMAL;

    /** If true, this handler is executed on the EventBus' async executor for post(). */
    boolean async() default false;

    /** If true, will be invoked even when a Cancellable event is cancelled. */
    boolean ignoreCancelled() default false;
}

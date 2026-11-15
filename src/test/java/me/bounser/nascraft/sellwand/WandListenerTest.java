package me.bounser.nascraft.sellwand;

import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression tests for the sell-wand NPE: clicking air produces a {@link PlayerInteractEvent}
 * whose {@code getClickedBlock()} is null. The listener must bail out before touching it.
 *
 * <p>{@link WandListener} is instantiated with Objenesis because its constructor needs the
 * fully initialised {@link WandsManager} (and therefore a running server). Both tested paths
 * return before any state is used, so skipping the constructor is safe here.
 */
class WandListenerTest {

    private static WandListener newListener() {
        return new ObjenesisStd().newInstance(WandListener.class);
    }

    @Test
    void clickingAirDoesNotThrowAndDoesNotUseTheBlock() {
        WandListener listener = newListener();
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.isCancelled()).thenReturn(false);
        when(event.getClickedBlock()).thenReturn(null);

        assertDoesNotThrow(() -> listener.onContainerClick(event));

        verify(event, never()).useInteractedBlock();
        verify(event, never()).getItem();
        verify(event, never()).getPlayer();
    }

    @Test
    void cancelledEventIsIgnoredBeforeTheNullCheck() {
        WandListener listener = newListener();
        PlayerInteractEvent event = mock(PlayerInteractEvent.class);
        when(event.isCancelled()).thenReturn(true);

        listener.onContainerClick(event);

        verify(event, never()).getClickedBlock();
        verify(event, never()).useInteractedBlock();
    }
}

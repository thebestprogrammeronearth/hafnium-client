package dev.hafnium.client.core.agent;

import dev.hafnium.client.core.ClientThread;
import dev.hafnium.client.core.event.EventBus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.hafnium.client.core.agent.AgentState.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentStateMachineTest {
    long clock;

    AgentStateMachine machine(EventBus bus) {
        return new AgentStateMachine("test-agent", TransitionTable.standard(), bus, ClientThread.DIRECT, () -> clock);
    }

    @Test
    void fullLifecycleIsRecordedAndPosted() {
        EventBus bus = new EventBus();
        List<AgentStateEvent> events = new ArrayList<>();
        bus.subscribe(AgentStateEvent.class, "t", events::add);
        AgentStateMachine m = machine(bus);
        assertEquals(IDLE, m.state());
        m.transition(PLANNING, "objective received");
        clock = 5;
        m.transition(RUNNING, "plan ready");
        m.transition(PAUSED, "player took over");
        m.transition(RUNNING, "resumed");
        m.transition(RECOVERING, "path blocked");
        m.transition(RUNNING, "recovered");
        clock = 9;
        m.transition(COMPLETED, "done");
        m.transition(IDLE, "reset");
        assertEquals(8, m.history().size());
        assertEquals(8, events.size());
        assertEquals(new AgentTransition("test-agent", RUNNING, COMPLETED, "done", 9), m.history().get(6));
        assertEquals("reset", m.reason());
    }

    @Test
    void illegalTransitionsThrowAndLeaveStateUnchanged() {
        AgentStateMachine m = machine(null);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> m.transition(RUNNING, "skip planning"));
        assertTrue(e.getMessage().contains("IDLE -> RUNNING"));
        assertEquals(IDLE, m.state());
        assertFalse(m.tryTransition(COMPLETED, "nope"));
        m.transition(PLANNING, "");
        m.transition(FAILED, "no plan");
        assertThrows(IllegalStateException.class, () -> m.transition(RUNNING, "from failed"));
        assertTrue(m.tryTransition(IDLE, "reset"));
    }

    @Test
    void hooksRunExitThenEnterAndCannotRecurse() {
        AgentStateMachine m = machine(null);
        List<String> order = new ArrayList<>();
        m.onExit(IDLE, t -> order.add("exit IDLE"));
        m.onEnter(PLANNING, t -> order.add("enter PLANNING"));
        m.onEnter(RUNNING, t -> m.transition(PAUSED, "nested")); // must be rejected (logged), not applied
        m.transition(PLANNING, "");
        m.transition(RUNNING, "");
        assertEquals(List.of("exit IDLE", "enter PLANNING"), order);
        assertEquals(RUNNING, m.state(), "a transition requested from inside a hook is refused");
    }

    @Test
    void customTablesExtendTheLifecycle() {
        TransitionTable table = TransitionTable.standard().toBuilder()
                .allow(IDLE, RUNNING)          // e.g. an agent with nothing to plan
                .forbid(RUNNING, PAUSED)       // e.g. an agent that cannot pause
                .build();
        AgentStateMachine m = new AgentStateMachine("custom", table, null, ClientThread.DIRECT, () -> 0);
        m.transition(RUNNING, "direct start");
        assertFalse(m.canTransition(PAUSED));
        assertThrows(IllegalArgumentException.class, () -> TransitionTable.builder().allow(IDLE, IDLE));
    }

    @Test
    void offThreadTransitionsAreRejected() throws Exception {
        Thread owner = Thread.currentThread();
        ClientThread ct = new ClientThread() {
            @Override
            public boolean isCurrent() {
                return Thread.currentThread() == owner;
            }

            @Override
            public void execute(Runnable task) {
            }
        };
        AgentStateMachine m = new AgentStateMachine("x", TransitionTable.standard(), null, ct, () -> 0);
        Throwable[] err = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                m.transition(PLANNING, "");
            } catch (Throwable e) {
                err[0] = e;
            }
        });
        t.start();
        t.join();
        assertInstanceOf(IllegalStateException.class, err[0]);
        assertEquals(IDLE, m.state());
    }
}

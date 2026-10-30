package net.sneakycharactermanager.paper.storage;

import java.util.*;

/** Main-thread ownership tokens. Player object identity distinguishes reconnects. */
public final class ResultFence implements AutoCloseable {
    public record Ticket(String player, String lane, Object session, long sequence) {}
    private final Map<String, Map<String, Ticket>> tickets = new HashMap<>();
    private long sequence;
    private boolean closed;
    public Ticket capture(String player, String lane, Object session) {
        Ticket ticket = new Ticket(player, lane, session, ++sequence);
        if (!closed) tickets.computeIfAbsent(player, unused -> new HashMap<>()).put(lane, ticket);
        return ticket;
    }
    public boolean current(Ticket ticket, Object session) {
        return !closed && ticket.session() == session
                && tickets.getOrDefault(ticket.player(), Map.of()).get(ticket.lane()) == ticket;
    }
    public void release(Ticket ticket) {
        Map<String, Ticket> lanes = tickets.get(ticket.player());
        if (lanes != null) {
            lanes.remove(ticket.lane(), ticket);
            if (lanes.isEmpty()) tickets.remove(ticket.player());
        }
    }
    public void forget(String player) { tickets.remove(player); }
    @Override public void close() { closed = true; tickets.clear(); }
}

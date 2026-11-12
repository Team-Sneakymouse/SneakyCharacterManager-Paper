package net.sneakycharactermanager.paper.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class ProxyListRepliesTest {
    @Test void chunksReassembleByOffsetInAnyOrder() {
        var reply = ProxyListReplies.expect("out-of-order");
        ProxyListReplies.accept("out-of-order", 5, 3, List.of("d", "e"));
        assertFalse(reply.isDone());
        ProxyListReplies.accept("out-of-order", 5, 0, List.of("a", "b", "c"));
        assertEquals(List.of("a", "b", "c", "d", "e"), reply.join());
    }
    @Test void emptyReplyCompletesWithOneChunk() {
        var reply = ProxyListReplies.expect("empty");
        ProxyListReplies.accept("empty", 0, 0, List.of());
        assertEquals(List.of(), reply.join());
    }
    @Test void inconsistentChunkFailsInsteadOfReturningPartialData() {
        var reply = ProxyListReplies.expect("bad");
        ProxyListReplies.accept("bad", 2, 0, List.of("a"));
        ProxyListReplies.accept("bad", 3, 1, List.of("b"));
        assertThrows(CompletionException.class, reply::join);
    }
    @Test void chunkPastTheEndFails() {
        var reply = ProxyListReplies.expect("overflow");
        ProxyListReplies.accept("overflow", 2, 1, List.of("a", "b"));
        assertThrows(CompletionException.class, reply::join);
    }
    @Test void unknownRequestIsIgnoredAndCallerCannotCompleteSharedState() {
        ProxyListReplies.accept("nobody-asked", 1, 0, List.of("x"));
        var reply = ProxyListReplies.expect("copy");
        reply.complete(List.of("forged"));
        ProxyListReplies.accept("copy", 1, 0, List.of("real"));
        var second = ProxyListReplies.expect("copy-2");
        ProxyListReplies.fail("copy-2", new IllegalStateException("no carrier"));
        assertThrows(CompletionException.class, second::join);
    }
}

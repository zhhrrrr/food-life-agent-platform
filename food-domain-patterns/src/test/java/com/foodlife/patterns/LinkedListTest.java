package com.foodlife.patterns;

import com.foodlife.patterns.framework.link.model2.chain.LinkedList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkedListTest {
    @Test void indexedAccessRejectsEmptyNegativeAndPastEndPositions() {
        LinkedList<String> list = new LinkedList<>("checks");
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
        list.addFirst("first"); list.addLast("last");
        assertEquals("first", list.get(0)); assertEquals("last", list.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(2));
    }
}

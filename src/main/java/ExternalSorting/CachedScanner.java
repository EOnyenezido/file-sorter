package ExternalSorting;

import java.util.Scanner;

/**
* This is essentially a wrapper on java.util.Scanner to keep the last word in memory.
* It is necessary as the min/max heap used to merge needs check the current word across
* all the temp files several times to maintain the files in sorted order.
* */

public class CachedScanner {
    private final Scanner sc;
    private String cache;

    public CachedScanner(Scanner scanner) {
        this.sc = scanner;
        readNewWord();
    }

    public String peek() {
        return this.cache;
    }

    public boolean isEmpty() {
        return this.cache == null;
    }

    public void close() {
        this.sc.close();
    }

    public String pop() {
        String curr = peek();
        readNewWord();
        return curr;
    }

    private void readNewWord() {
        this.cache = this.sc.hasNext() ? sc.next() : null;
    }
}

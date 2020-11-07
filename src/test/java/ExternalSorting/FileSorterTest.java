package ExternalSorting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSorterTest {

    private static final String[] sampleData = {"Lorem", "ipsum", "dolor", "sit", "amet", "consectetur",
            "adipiscing", "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore",
            "et", "dolore", "magna", "aliqua", "Ut", "enim", "ad", "minim", "veniam"};
    private static final String[] sampleMergeData = {"quis", "nostrud", "exercitation", "ullamco", "laboris",
            "nisi", "ut", "aliquip", "ex", "ea", "commodo", "consequat"};
    private static final String[] EXPECTED_SORTED_ASC = {"ad", "adipiscing", "aliqua", "amet", "consectetur", "do",
            "dolor", "dolore", "eiusmod", "elit", "enim", "et", "incididunt", "ipsum", "labore", "Lorem",
            "magna", "minim", "sed", "sit", "tempor", "ut", "Ut", "veniam"};
    private static final String[] EXPECTED_SORTED_DESC = {"veniam", "ut", "Ut", "tempor", "sit", "sed", "minim",
            "magna", "Lorem", "labore", "ipsum", "incididunt", "et", "enim", "elit", "eiusmod", "dolore", "dolor",
            "do", "consectetur", "amet", "aliqua", "adipiscing", "ad"};
    private static final String[] EXPECTED_MERGED = {"ad adipiscing aliqua aliquip amet commodo consectetur " +
            "consequat do dolor dolore ea eiusmod elit enim et ex exercitation incididunt ipsum labore laboris " +
            "Lorem magna minim nisi nostrud quis sed sit tempor ullamco ut ut Ut veniam "};

    @Test
    public void shouldCorrectlyEstimateFreeMemory() {
        /*
         * When an estimate of the free memory on the JVM is made
         * Then it should be less than the max memory configuration
         *   and the approximation margin should be within 2kb
         * */
        // Arrange
        Runtime currentRunTime = Runtime.getRuntime();

        // Act
        long estimate = FileSorter.getEstimatedFreeMemory();
        long maxMemory = currentRunTime.maxMemory();
        long usedMemory = currentRunTime.totalMemory() - currentRunTime.freeMemory();

        // Assert
        // Estimate is less than max memory
        assertTrue(estimate < maxMemory);
        // Estimate margin is within 2kb
        // It should be exact but leaving a 2kb approximation margin for java runtime.getruntime().freememory()
        assertTrue(estimate - (maxMemory - usedMemory) <= 2000);
    }
}

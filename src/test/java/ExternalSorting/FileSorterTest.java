package ExternalSorting;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    public void shouldCorrectlyEstimateBlockSize() throws Exception {
        /*
         * When an estimate is made for the preferred block size
         * Then it should not be too small
         *  and it should throw an exception if it is bigger than the available free memory
         * */
        // Arrange
        long freeMemoryEstimate = FileSorter.getEstimatedFreeMemory();

        // Act
        long tinyBlockSizeEstimate = FileSorter.getEstimatedBlockSize(1000, 1024, freeMemoryEstimate);
        long okayBlockSizeEstimate = FileSorter.getEstimatedBlockSize(Long.MAX_VALUE, 1024, freeMemoryEstimate);

        // Assert

        // when the block size is too small it should be optimized to half of the free memory
        assertEquals(tinyBlockSizeEstimate, freeMemoryEstimate / 2);

        // when the block size is okay, it should still be less than the free memory
        assertTrue(okayBlockSizeEstimate < freeMemoryEstimate);

        // when the block size is more than the available free memory, it should throw an exception
        Exception exception = assertThrows(Exception.class, () -> {
            long excessBlockSizeEstimate = FileSorter.getEstimatedBlockSize(1000000000, 1, freeMemoryEstimate);
        });
        String expectedMessage = "Cannot create enough temporary files to fit a sort file. Please check maxTmpFiles";
        String actualMessage = exception.getMessage();
        assertEquals(actualMessage, expectedMessage);
    }

    @Test
    public void shouldCorrectlyEstimateStringSize() {
        /*
         * Given a string
         * When an size estimate is made
         * Then should be at least twice it's length
         */
        for (int i = 0; i < 100; i++) {
            // Arrange
            StringBuilder sb = new StringBuilder();
            // Generate random string
            while (sb.length() < i) sb.append((char) ThreadLocalRandom.current().nextInt(97, 122 + 1));

            // Act
            long estimate = FileSorter.getEstimatedStringSize(sb.toString());

            // Assert
            assertTrue(estimate >= 2 * sb.length());
        }
    }

    @Test
    public void shouldSortAscAndSaveTempFile() throws IOException {
        /*
        * Given an unsorted list of strings
        * When sortAndSaveTempFile method is called with an ascending comparator
        * Then should sort the list of string in ascending order and save in a temp file
        * */
        // Arrange
        List<String> unsortedLineChunk = Arrays.asList(sampleData);
        Comparator<String> ascComparator = (a, b) -> a.toLowerCase().compareTo(b.toLowerCase());

        // Act
        File file = FileSorter.sortAndSaveTempFile(unsortedLineChunk, ascComparator, null);

        // Assert
        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
        List<String> sorted = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                sorted.add(line);
            }
        }
        assertArrayEquals(sorted.toArray(), EXPECTED_SORTED_ASC);
    }

    @Test
    public void shouldSortDescAndSaveTempFile() throws IOException {
        /*
         * Given an unsorted list of strings
         * When sortAndSaveTempFile method is called with an descending comparator
         * Then should sort the list of string in descending order and save in a temp file
         * */
        // Arrange
        List<String> unsortedLineChunk = Arrays.asList(sampleData);
        Comparator<String> descComparator = (a, b) -> b.toLowerCase().compareTo(a.toLowerCase());

        // Act
        File file = FileSorter.sortAndSaveTempFile(unsortedLineChunk, descComparator, null);

        // Assert
        assertNotNull(file);
        assertTrue(file.exists());
        assertTrue(file.length() > 0);
        List<String> sorted = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                sorted.add(line);
            }
        }

        assertArrayEquals(sorted.toArray(), EXPECTED_SORTED_DESC);
    }

    @Test
    public void shouldMergeSortedTempFiles() throws IOException {
        // Arrange
        List<String> unsortedLineChunk1 = Arrays.asList(sampleData);
        List<String> unsortedLineChunk2 = Arrays.asList(sampleMergeData);
        Comparator<String> ascComparator = (a, b) -> a.toLowerCase().compareTo(b.toLowerCase());
        File tempFile1 = FileSorter.sortAndSaveTempFile(unsortedLineChunk1, ascComparator, null);
        File tempFile2 = FileSorter.sortAndSaveTempFile(unsortedLineChunk2, ascComparator, null);
        List<File> tempFileList = new ArrayList<>();
        tempFileList.add(tempFile1);
        tempFileList.add(tempFile2);
        File tempOutputFile = File.createTempFile("test_output", ".txt", null);
        tempOutputFile.deleteOnExit();
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempOutputFile)));

        // Act
        FileSorter.mergeSortedTempFiles(ascComparator, tempFileList, fileWriter, 100);

        // Assert
        assertNotNull(tempOutputFile);
        assertTrue(tempOutputFile.exists());
        assertTrue(tempOutputFile.length() > 0);
        List<String> merged = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(tempOutputFile))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                merged.add(line);
            }
        }

        assertArrayEquals(merged.toArray(), EXPECTED_MERGED);
        // word wrap
        assertTrue(merged.get(0).split(" ").length <= 100);
    }

    @Test
    public void shouldMergeSortedTempFilesAndWrapLine() throws IOException {
        // Arrange
        List<String> unsortedLineChunk1 = Arrays.asList(sampleData);
        List<String> unsortedLineChunk2 = Arrays.asList(sampleMergeData);
        Comparator<String> ascComparator = (a, b) -> a.toLowerCase().compareTo(b.toLowerCase());
        File tempFile1 = FileSorter.sortAndSaveTempFile(unsortedLineChunk1, ascComparator, null);
        File tempFile2 = FileSorter.sortAndSaveTempFile(unsortedLineChunk2, ascComparator, null);
        List<File> tempFileList = new ArrayList<>();
        tempFileList.add(tempFile1);
        tempFileList.add(tempFile2);
        File tempOutputFile = File.createTempFile("test_output", ".txt", null);
        tempOutputFile.deleteOnExit();
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempOutputFile)));

        // Act
        FileSorter.mergeSortedTempFiles(ascComparator, tempFileList, fileWriter, -1);

        // Assert
        assertNotNull(tempOutputFile);
        assertTrue(tempOutputFile.exists());
        assertTrue(tempOutputFile.length() > 0);
        List<String> merged = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(tempOutputFile))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                merged.add(line);
            }
        }
        // word wrap
        assertTrue(merged.get(0).split(" ").length <= 1);
    }

    @Test
    public void shouldCreateSortedTempFiles_WhenGivenUnsortedLargeFile() throws Exception {
        // Arrange
        long freeMemory = FileSorter.getEstimatedFreeMemory();
        Comparator<String> ascComparator = (a, b) -> a.toLowerCase().compareTo(b.toLowerCase());
        File tempInputFile = File.createTempFile("test_input", ".txt", null);
        tempInputFile.deleteOnExit();
        OutputStream out = new FileOutputStream(tempInputFile);
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(out));
        fileWriter.write(String.join(" ", sampleData));
        fileWriter.close();
        Scanner fileScanner = new Scanner(tempInputFile);

        // Act
        List<File> sortedTempFiles = FileSorter.createSortedTempFiles(tempInputFile.length(), 1025,
                freeMemory, fileScanner, ascComparator, null);

        // Assert
        for (File file : sortedTempFiles) {
            assertNotNull(file);
            assertTrue(file.exists());
            assertTrue(file.length() > 0);
            List<String> tempData = new ArrayList<>();
            try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = fileReader.readLine()) != null) {
                    tempData.add(line);
                }
            }

            assertArrayEquals(tempData.toArray(), EXPECTED_SORTED_ASC);
        }
    }

    @Test
    public void shouldThrowAnExceptionAndExit_WhenNotGivenRequiredParameters() {
        // Act and Assert
        Exception exception = assertThrows(Exception.class, () -> {
            Properties props = new Properties();
            FileSorter testFileSorter = new FileSorter(props);
            testFileSorter.main(new String[]{"--maxtmpfiles", "0"});
        });
        String expectedMessage = "The following parameters are required: " +
                "Input file, Output file";
        String actualMessage = exception.getMessage();
        assertEquals(actualMessage, expectedMessage);
    }

    @Test
    public void shouldSortAndSaveToAnOutputFile_WhenGivenAnUnsortedInputFile() throws Exception {
        // Arrange
        File tempInputFile = File.createTempFile("test_input", ".txt", null);
        tempInputFile.deleteOnExit();
        File tempOutputFile = File.createTempFile("test_input", ".txt", null);
        tempOutputFile.deleteOnExit();
        OutputStream out = new FileOutputStream(tempInputFile);
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(out));
        fileWriter.write(String.join(" ", sampleData));
        fileWriter.close();

        // Act
        FileSorter testFileSorter = new FileSorter(null); // using command line props
        testFileSorter.main(new String[]{"--maxtmpfiles", "1024", "--inputfile", tempInputFile.toString(),
            "--outputfile", tempOutputFile.toString(), "--tmpfilesdirectory", ".",
            "--order", "asc", "--wordwrap", "100"});

        // Assert
        assertNotNull(tempOutputFile);
        assertTrue(tempOutputFile.exists());
        assertTrue(tempOutputFile.length() > 0);
        List<String> sorted = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(tempOutputFile))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                sorted.add(line);
            }
        }

        assertTrue(sorted.get(0).contains(String.join(" ", EXPECTED_SORTED_ASC)));
    }
}

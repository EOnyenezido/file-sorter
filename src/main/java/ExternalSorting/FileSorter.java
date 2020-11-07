package ExternalSorting;

import java.util.Properties;

/**
 * Sorts a large input file by splitting it into sorted temp files and merging them into a sorted output file.
 *
 * This class essentially takes in a large file, and uses an estimate of the free memory to split the file
 * into sorted chunks that fit in the free memory and saves these chunks as temp files.
 *
 * It then merges the sorted temp files using the merge k sorted lists algorithm to produce a final sorted file
 * */
public class FileSorter {
    private static int OBJ_OVERHEAD;
    private static Properties props;

    /**
     * Constructor for initializing an instance with custom properties.
     * Properties can be null if so it defaults to internal values.
     *
     * @param props Properties object containing maxTmpFiles, inputFile, outputFile,
     *              order, wordWrap and tmpFilesDirectory
     * */
    public FileSorter(Properties props) {
        FileSorter.props = props;
        String dataModel = System.getProperty("sun.arch.data.model");
        // If unknown JVM bit model, default to 64 bits
        boolean IS_64_BIT_JVM = dataModel == null || !dataModel.contains("32");
        int OBJ_HEADER = IS_64_BIT_JVM ? 16 : 8;
        int ARR_HEADER = IS_64_BIT_JVM ? 24 : 12;
        int OBJ_REF = IS_64_BIT_JVM ? 8 : 4;
        int INT_FIELDS = 12;
        OBJ_OVERHEAD = OBJ_HEADER + INT_FIELDS + OBJ_REF + ARR_HEADER;
    }

    /**
    * This method essentially attempts to estimate the available free memory.
    * It first calls the gc to clear up unused objects and then estimates the
    * free memory is the configured max memory -Xmx/mx minus the used memory
    *
    * @return The estimated free memory
    * */
    public static long getEstimatedFreeMemory() {
        System.gc(); // Call the garbage collector to free up memory first

        Runtime currentRunTime = Runtime.getRuntime();
        // Used memory is the currently allocated memory minus the free memory
        long usedMemory = currentRunTime.totalMemory() - currentRunTime.freeMemory();

        // presumably free memory
        return Runtime.getRuntime().maxMemory() - usedMemory;
    }

    /**
    * This method estimates a block size which is how much data we can read into memory at a chunk
    * It also ensures that this block size is not more than the free memory and not
    * excessively less than free memory such that it becomes inefficient
    *
    * @param fileSize The size of the input file to be processed
    * @param maxTmpFiles The maximum number of temp files to create
    * @param freeMemory The amount of JVM memory that is free and available for use
    *
    * @return The estimated size of a block which we can read into memory safely
    *
    * @throws Exception If the estimated block size is above the free memory then the file cannot be processed
    *                   unless the maxTmpFiles is increased
    * */
    public static long getEstimatedBlockSize(long fileSize, int maxTmpFiles, long freeMemory)
            throws Exception {
        // We make sure we are not creating more files than maxTmpFiles
        long blockSize = fileSize / maxTmpFiles + (fileSize % maxTmpFiles == 0 ? 0 : 1);

        // If the block size is greater than free memory, we cannot proceed with the configured
        // maxTmpFiles. Throw an error
        if (blockSize > freeMemory) {
            throw new Exception("Cannot create enough temporary files to fit a sort file. Please check maxTmpFiles");
        }

        // If the block size if far less than the free memory, we are creating to many
        // temporary files, so we increase it
        blockSize = Math.max(blockSize, freeMemory / 2);

        return blockSize;
    }

    /**
    * This method essentially estimates a string size in bytes
    * It estimates 2 bytes per character plus an object overheard
    * based on the JVM bit size
    *
    * @param str String for which to estimate size
    *
    * @return The estimated string size
    * */
    public static long getEstimatedStringSize(String str) {
        return (str.length() * 2) + OBJ_OVERHEAD;
    }
}

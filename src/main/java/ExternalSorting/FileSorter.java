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
}

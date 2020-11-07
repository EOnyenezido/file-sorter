package ExternalSorting;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

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

    /**
    * This method sorts a chunk of words that are within the block size limit
    * and saves the sorted chunk to a temp file to clear up memory for the next chunk.
    *
    * @param unsortedLineChunk A list of words to sort and save in a temp file
    * @param comparator Comparator to use to sort the list of words
    * @param tmpDirectory Directory to save the sorted temp file
    *
    * @return The sorted temp file
    *
    * @throws IOException General IOException if unable to access any file(s)
    * */
    public static File sortAndSaveTempFile(List<String> unsortedLineChunk, Comparator<String> comparator,
        File tmpDirectory) throws IOException {
        // Sort the unsorted line chunk
        unsortedLineChunk.sort(comparator); // TODO - Compare with parallel sort
        // Create a temp file and delete it on exit
        File newTempFile = File.createTempFile("sorted", ".txt", tmpDirectory);
        newTempFile.deleteOnExit();
        // Write the tmp file to disk
        OutputStream out = new FileOutputStream(newTempFile);
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(out));
        for (String line : unsortedLineChunk) {
            fileWriter.write(line);
            fileWriter.newLine();
        }
        fileWriter.close();
        return newTempFile;
    }

    /**
    * This uses a min/max heap and the merge k sorted lists algorithm to merge the sorted temp files.
    *
    * Why use java.util.Scanner here instead of java.io.BufferedFileReader? The scanner uses
    * a smaller buffer than the buffered reader and since the files are sorted word for word, it is
    * more memory efficient to read a single word than to read a line chunk and split it into words
    *
    * @param comparator Comparator for merging the words in the sorted temp files
    * @param tmpFiles A list of the sorted temp files to be merged into output file
    * @param fileWriter A simple buffered file writer for writing the output file
    * @param wordWrap Number of words before wrapping to a new line
    *
    * @throws IOException General IOException if unable to access any file(s)
    * */
    public static void mergeSortedTempFiles(Comparator<String> comparator, List<File> tmpFiles,
        BufferedWriter fileWriter, int wordWrap) throws IOException {
        // Min/Max heap depending on the comparator passed
        PriorityQueue<CachedScanner> queue = new PriorityQueue<>((o1, o2) -> comparator.compare(o1.peek(), o2.peek()));
        // Add all the temp file scanners to the heap
        for (File file : tmpFiles) {
            Scanner s = new Scanner(file);
            CachedScanner sc = new CachedScanner(s);
            if (!sc.isEmpty()) {
                queue.add(sc);
            }
        }
        // Go through heap and write the sorted words to output file
        // Wrapped in a try-finally block so if an exception occurs the file writer
        // and file scanners will always be closed
        try {
            int counter = 0;
            while (!queue.isEmpty()) {
                CachedScanner currScanner = queue.poll();
                String word = currScanner.pop();
                fileWriter.write(word);
                fileWriter.write(" ");
                if (++counter >= wordWrap) { // wrap line after wordWrap words per line
                    fileWriter.newLine();
                    counter = 0;
                }
                if (currScanner.isEmpty()) {
                    currScanner.close();
                } else {
                    queue.add(currScanner);
                }
            }
        } finally {
            fileWriter.close();
            // Just in case an exception occurs before the priority queue is empty, to avoid any memory leaks
            for (CachedScanner sc : queue) {
                sc.close();
            }
        }
    }

    /**
    * This method essentially takes a large file and scans a set block into memory
    * This block is then sorted and saved to a temp file. The list of temp files are
    * returned so they can be merged into a new output file
    *
    * @param fileSize Size of the input file, used to estimate block size
    * @param maxTmpFiles Maximum number of temporary files to create, used to estimate block size
    * @param freeMemory Estimated free memory, used to estimate block size
    * @param fileScanner Scanner for the input file to be sorted
    * @param comparator Comparator used to sort the words, ascending or descending order
    * @param tmpDirectory Directory to place the temp files, files will be deleted after
    *
    * @return The list of the sorted temp files
    *
    * @throws Exception If unable to read from the input file or save a temp file
    * */
    public static List<File> createSortedTempFiles(long fileSize, int maxTmpFiles, long freeMemory,
        Scanner fileScanner, Comparator<String> comparator, File tmpDirectory) throws Exception {
        List<File> files = new ArrayList<>();
        long maxBlockSize = getEstimatedBlockSize(fileSize, maxTmpFiles, freeMemory);
        Set<String> distinctWords = new HashSet<>();

        try {
            while (fileScanner.hasNext()) {
                long currBlockSize = 0;
                // read lines from the file until we hit the max block size
                while (currBlockSize < maxBlockSize && fileScanner.hasNext()) {
                    String word = fileScanner.next();
                    distinctWords.add(word);
                    currBlockSize += getEstimatedStringSize(word);
                }
                File currFile = sortAndSaveTempFile(new ArrayList<>(distinctWords), comparator, tmpDirectory);
                files.add(currFile);
                distinctWords.clear();
                System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                        + ": Temp file : " + currFile.toString() + " created successfully.");
            }
        } finally {
            // close the file scanner
            fileScanner.close();
        }

        return files;
    }

    public static void main(String[] args) throws Exception {
        // Internal default values
        long startTime = System.currentTimeMillis();
        int maxTempFiles = 1024, wordWrap = 100;
        File inputFile, outputFile;
        File tmpFilesDirectory = new File(".");
        String order = "asc";

        // TODO - Replace with logger
        System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                + ": Loading Configuration from File.");

        // Try to load configuration file, default to internal values if not found
        try {
            File configFile = new File("config.properties");
            FileReader reader = new FileReader(configFile);
            if (props == null) {
                props = new Properties();
                props.load(reader);
            }
            reader.close();
        } catch (FileNotFoundException ex) {
            // file does not exist continue with defaults
            props = props == null ? new Properties() : props;
            System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                    + ": Configuration file not found. Using command line variables or internal defaults.");
        } catch (IOException ex) {
            // I/O error continue with defaults
            props = props == null ? new Properties() : props;
            System.err.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                    + ": Unable to load configuration file: " + ex.getMessage()
                    + ". Using command line variables or internal defaults");
        }

        /*
        * Load configuration from config file or command line arguments
        * Order of precedence is command line > config file > internal defaults
        * */
        // config file
        maxTempFiles = props.getProperty("maxTempFiles") != null ? Integer.parseInt(props.getProperty("maxTempFiles"))
            : maxTempFiles;
        inputFile = props.getProperty("inputFile") != null ? new File(props.getProperty("inputFile"))
            : null;
        outputFile = props.getProperty("outputFile") != null ? new File(props.getProperty("outputFile"))
            : null;
        tmpFilesDirectory = props.getProperty("tmpFilesDirectory") != null ? new File(props.getProperty("tmpFilesDirectory"))
            : tmpFilesDirectory;
        order = props.getProperty("order") != null ? props.getProperty("order") : order;
        wordWrap = props.getProperty("wordWrap") != null ? Integer.parseInt(props.getProperty("wordWrap")) : wordWrap;
        // command line
        for (int param = 0; param < args.length; param++) {
            if (args[param].equals("--maxtmpfiles") && args.length > param + 1) {
                int val = Integer.parseInt(args[++param]);
                if (val < 1) { // make sure it is valid
                    System.err.println("Invalid max temp file value: " + val
                            + ". Continuing with internal default: " + maxTempFiles);
                } else {
                    maxTempFiles = val;
                }
            } else if (args[param].equals("--tmpfilesdirectory") && args.length > param + 1) {
                tmpFilesDirectory = new File(args[++param]);
            } else if (args[param].equals("--inputfile") && args.length > param + 1) {
                inputFile = new File(args[++param]);
            } else if (args[param].equals("--outputfile") && args.length > param + 1) {
                outputFile = new File(args[++param]);
            } else if (args[param].equals("--order") && args.length > param + 1) {
                order = args[++param];
            } else if (args[param].equals("--wordwrap") && args.length > param + 1) {
                wordWrap = Integer.parseInt(args[++param]);
            }
        }

        // Make sure none of the required values are null
        if (inputFile == null || outputFile == null) {
            String message = "The following parameters are required: "
                    + (inputFile == null ? "Input file, " : "")
                    + (outputFile == null ? "Output file, " : "");
            System.err.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                    + ": " + message);
            throw new Exception(message);
        }

        // Sort ascending order or descending order
        Comparator<String> comparator = order.equals("desc") ? (a, b) -> b.toLowerCase().compareTo(a.toLowerCase())
                : (a, b) -> a.toLowerCase().compareTo(b.toLowerCase());

        // Split the input file into sorted temp files
        System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                + ": Begin splitting large file into temporary sorted smaller files.");
        Scanner fileScanner = new Scanner(inputFile);
        List<File> sortedTempFiles = createSortedTempFiles(inputFile.length(), maxTempFiles,
                getEstimatedFreeMemory(), fileScanner, comparator, tmpFilesDirectory);

        // Merge the sorted temp files
        System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                + ": Begin merging temporary sorted files to sorted output file - " + outputFile.toString());
        BufferedWriter fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile, true)));
        mergeSortedTempFiles(comparator, sortedTempFiles, fileWriter, wordWrap);
        System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                + ": Sorted output file created successfully.");
        System.out.println(new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.sss").format(new Date())
                + ": Total processing time(ms) - " + (System.currentTimeMillis() - startTime));
    }
}

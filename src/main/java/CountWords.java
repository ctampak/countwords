import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * Should work in JDK8+.
 *
 *  * Test:
 *  $ javac optimized.java; echo "one two three one two two" | java optimized
 *
 * This is using custom buffering to cache read characters up to a new word.
 * It's marginally faster than StringTokenizer implementation (probably because I still allocate same number of Strings on tokenization, I just use different buffer size).
 *
 * x10 original benchmark:
 Language      | Simple | Optimized | Notes
 ------------- | ------ | --------- | -----
 Java          |   1.11 |      1.08 | by Iulian Plesoianu
 * using x20 benchmark (doubled the original load):
 Language      | Simple | Optimized | Notes
 ------------- | ------ | --------- | -----
 Java          |  16.86 |     16.48 | by Iulian Plesoianu
 */

public class CountWords {
    private static final char LINE_END = '\n';
    private static final int LINE_BUFFER_SIZE = 64 * 1024;
    private static final int CHAR_BUFFER_SIZE = 200;
    private static final int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws IOException {
//        HashingStrategy<String> strategy = new HashingStrategy<>() {
//            @Override
//            public int computeHashCode(String s) {
//                if (s.length() > 5) {
//                    return MurmurHash2.hash32(s, 0, 5);
//                }
//                return MurmurHash2.hash32(s);
//            }
//
//            @Override
//            public boolean equals(String s, String e1) {
//                return s.equals(e1);
//            }
//        };
        ForkJoinPool executor = new ForkJoinPool(NUMBER_OF_THREADS);
        final Map<String, Long> mutableMap;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            List<String> allLines = new ArrayList<>();

            String line;
            while((line = reader.readLine()) != null) {
                allLines.add(line);
            }

            mutableMap = executor.submit(() ->
                    allLines.parallelStream()
                            .flatMap(CountWords::lineToWords)
                            .collect(
                                    Collectors.groupingByConcurrent(word -> word, Collectors.counting())
                            )
            ).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        List<Map.Entry<String, Long>> countsAsList = new ArrayList<>(mutableMap.entrySet());
        countsAsList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        StringBuilder output = new StringBuilder();
        for (Map.Entry<String, Long> e : countsAsList) {
            output.append(e.getKey());
            output.append(' ');
            output.append(e.getValue());
            output.append('\n');
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));
        writer.write(output.toString());
        writer.flush();
    }

    private static List<String> words(int nrChars, char[] lineBuffer) {
        List<String> result = new ArrayList<>();
        StringBuilder wordBuffer = new StringBuilder(CHAR_BUFFER_SIZE);
        for (int i = 0; i < nrChars; i++) {
            char currentChar = lineBuffer[i];
            if (Character.isLetter(currentChar)) {
                wordBuffer.append(Character.toLowerCase(currentChar));
            } else if (Character.isSpaceChar(currentChar) || currentChar == LINE_END) {
                if (wordBuffer.length() > 0) {
                    String word = wordBuffer.toString();
                    result.add(word);
                    // reset char buffer
                    wordBuffer.setLength(0);
                }
            } else {
                wordBuffer.append(currentChar);
            }
        }
        if (wordBuffer.length() > 0) {
            String word = wordBuffer.toString();
            result.add(word);
        }
        return result;
    }

    private static String lines(int nrChars, char[] lineBuffer) {
        StringBuilder wordBuffer = new StringBuilder(CHAR_BUFFER_SIZE);
        for (int i = 0; i < nrChars; i++) {
            wordBuffer.append(lineBuffer[i]);
        }
        return wordBuffer.toString();
    }

    private static Stream<String> lineToWords(final String line) {
        final char[] chars = line.toCharArray();
        final Stream.Builder<String> builder = Stream.builder();
        StringBuilder wordBuffer = new StringBuilder(CHAR_BUFFER_SIZE);
        for (char c: chars) {
            if (Character.isLetter(c)) {
                wordBuffer.append(Character.toLowerCase(c));
            } else if (Character.isSpaceChar(c) || c == LINE_END) {
                if (wordBuffer.length() > 0) {
                    String word = wordBuffer.toString();
                    builder.add(word);
                    // reset char buffer
                    wordBuffer.setLength(0);
                }
            } else {
                wordBuffer.append(c);
            }
        }
        if (wordBuffer.length() > 0) {
            String word = wordBuffer.toString();
            builder.add(word);
        }
        return builder.build();
    }
}

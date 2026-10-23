import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Level6 {

    private record Candidate(int firstEmitter, int firstPosition, int secondEmitter, int secondPosition) {}
    private record Pattern(int radius, int count, List<Level5.Point> cells) {}

    private static List<String> generateProgram(int radius, List<Level5.Point> cells, int count) {
        List<Level5.Point> rail = Level5.buildRail(radius);
        int perimeterLength = rail.size();
        char[][] board = Level5.createEmptyBoard(radius);
        int[] rowSteps = {0, 1, 1, 0, -1, -1};
        int[] colSteps = {2, 1, -1, -2, -1, 1};

        Set<Level5.Point> target = new HashSet<>(cells);
        Map<Level5.Point, List<Candidate>> candidatesByCell = new HashMap<>();

        List<Set<Level5.Point>[]> raysByPosition = new ArrayList<>(perimeterLength);
        for (int position = 0; position < perimeterLength; position++) {
            @SuppressWarnings("unchecked")
            Set<Level5.Point>[] rays = new Set[6];
            Level5.Point start = rail.get(position);
            for (int emitter = 0; emitter < 6; emitter++) {
                Set<Level5.Point> ray = new HashSet<>();
                int row = start.r() + rowSteps[emitter];
                int col = start.c() + colSteps[emitter];
                while (row >= 0 && row < board.length && col >= 0 && col < board[row].length
                        && (board[row][col] == 'O' || board[row][col] == 'M')) {
                    ray.add(new Level5.Point(row, col));
                    row += rowSteps[emitter];
                    col += colSteps[emitter];
                }
                rays[emitter] = ray;
            }
            raysByPosition.add(rays);
        }

        for (int firstEmitter = 0; firstEmitter < 6; firstEmitter++) {
            for (int secondEmitter = firstEmitter + 1; secondEmitter < 6; secondEmitter++) {
                for (int firstPosition = 0; firstPosition < perimeterLength; firstPosition++) {
                    Set<Level5.Point> firstRay = raysByPosition.get(firstPosition)[firstEmitter];
                    for (int secondPosition = 0; secondPosition < perimeterLength; secondPosition++) {
                        Set<Level5.Point> secondRay = raysByPosition.get(secondPosition)[secondEmitter];
                        Level5.Point intersection = null;
                        boolean multipleIntersections = false;
                        for (Level5.Point cell : firstRay) {
                            if (secondRay.contains(cell)) {
                                if (intersection != null) {
                                    multipleIntersections = true;
                                    break;
                                }
                                intersection = cell;
                            }
                        }
                        if (!multipleIntersections && intersection != null && target.contains(intersection)) {
                            candidatesByCell.computeIfAbsent(intersection, ignored -> new ArrayList<>())
                                    .add(new Candidate(firstEmitter, firstPosition, secondEmitter, secondPosition));
                        }
                    }
                }
            }
        }

        int[] positions = {5 * radius, 0, radius, 2 * radius, 3 * radius, 4 * radius};
        boolean[] active = new boolean[6];
        List<String> program = new ArrayList<>();

        for (int repetition = 0; repetition < count; repetition++) {
            for (Level5.Point cell : cells) {
                List<Candidate> candidates = candidatesByCell.get(cell);
                if (candidates == null || candidates.isEmpty()) {
                    throw new IllegalStateException("No pair of emitter rays can create target cell " + cell);
                }

                Candidate best = null;
                int bestDistance = Integer.MAX_VALUE;
                int bestTotalDistance = Integer.MAX_VALUE;
                for (Candidate candidate : candidates) {
                    int firstDistance = circularDistance(positions[candidate.firstEmitter()],
                            candidate.firstPosition(), perimeterLength);
                    int secondDistance = circularDistance(positions[candidate.secondEmitter()],
                            candidate.secondPosition(), perimeterLength);
                    int distance = Math.max(firstDistance, secondDistance);
                    int totalDistance = firstDistance + secondDistance;
                    if (distance < bestDistance || (distance == bestDistance && totalDistance < bestTotalDistance)) {
                        best = candidate;
                        bestDistance = distance;
                        bestTotalDistance = totalDistance;
                    }
                }

                appendDeactivation(program, active);
                int firstDirection = movementDirection(positions[best.firstEmitter()], best.firstPosition(), perimeterLength);
                int secondDirection = movementDirection(positions[best.secondEmitter()], best.secondPosition(), perimeterLength);
                for (int step = 0; step < bestDistance; step++) {
                    char[] instruction = noops();
                    if (positions[best.firstEmitter()] != best.firstPosition()) {
                        moveEmitter(instruction, positions, best.firstEmitter(), firstDirection, perimeterLength);
                    }
                    if (positions[best.secondEmitter()] != best.secondPosition()) {
                        moveEmitter(instruction, positions, best.secondEmitter(), secondDirection, perimeterLength);
                    }
                    program.add(format(instruction));
                }

                char[] fire = noops();
                fire[best.firstEmitter()] = 'T';
                fire[best.secondEmitter()] = 'T';
                active[best.firstEmitter()] = true;
                active[best.secondEmitter()] = true;
                program.add(format(fire));
            }
        }

        return program;
    }

    private static int circularDistance(int from, int to, int perimeterLength) {
        int forward = (to - from + perimeterLength) % perimeterLength;
        int backward = (from - to + perimeterLength) % perimeterLength;
        return Math.min(forward, backward);
    }

    private static int movementDirection(int from, int to, int perimeterLength) {
        if (from == to) return 0;
        int forward = (to - from + perimeterLength) % perimeterLength;
        int backward = (from - to + perimeterLength) % perimeterLength;
        return forward <= backward ? 1 : -1;
    }

    private static void moveEmitter(char[] instruction, int[] positions, int emitter, int direction, int perimeterLength) {
        if (direction == 0) return;
        instruction[emitter] = direction > 0 ? 'F' : 'B';
        positions[emitter] = (positions[emitter] + direction + perimeterLength) % perimeterLength;
    }

    private static void appendDeactivation(List<String> program, boolean[] active) {
        char[] instruction = noops();
        boolean hasActiveEmitter = false;
        for (int emitter = 0; emitter < active.length; emitter++) {
            if (active[emitter]) {
                instruction[emitter] = 'T';
                active[emitter] = false;
                hasActiveEmitter = true;
            }
        }
        if (hasActiveEmitter) program.add(format(instruction));
    }

    private static char[] noops() {
        char[] instruction = new char[6];
        Arrays.fill(instruction, '-');
        return instruction;
    }

    private static String format(char[] instruction) {
        return instruction[0] + " " + instruction[1] + " " + instruction[2] + " "
                + instruction[3] + " " + instruction[4] + " " + instruction[5];
    }

    private static List<Pattern> parsePatterns(String content) {
        String[] lines = content.split("\\R");
        int index = skipBlankLines(lines, 0);
        if (index >= lines.length) throw new IllegalArgumentException("Input is empty");

        int patternCount = Integer.parseInt(lines[index++].trim());
        List<Pattern> patterns = new ArrayList<>(patternCount);
        for (int patternIndex = 0; patternIndex < patternCount; patternIndex++) {
            index = skipBlankLines(lines, index);
            if (index >= lines.length) throw new IllegalArgumentException("Missing pattern header " + patternIndex);

            String[] header = lines[index++].trim().split("\\s+");
            if (header.length != 4) throw new IllegalArgumentException("Pattern header must contain four numbers");
            int radius = Integer.parseInt(header[0]);
            int rows = Integer.parseInt(header[1]);
            int columns = Integer.parseInt(header[2]);
            int count = Integer.parseInt(header[3]);
            if (rows != 2 * radius + 1 || columns != 4 * radius + 1) {
                throw new IllegalArgumentException("Pattern dimensions do not match radius " + radius);
            }

            List<Level5.Point> cells = new ArrayList<>();
            for (int row = 0; row < rows; row++) {
                if (index >= lines.length) throw new IllegalArgumentException("Pattern grid is incomplete");
                String gridRow = lines[index++];
                if (gridRow.length() != columns) throw new IllegalArgumentException("Invalid pattern row width");
                for (int column = 0; column < columns; column++) {
                    if (gridRow.charAt(column) == 'M') cells.add(new Level5.Point(row, column));
                }
            }
            if (cells.isEmpty()) throw new IllegalArgumentException("A pattern must contain at least one matter cell");
            patterns.add(new Pattern(radius, count, cells));
        }
        return patterns;
    }

    private static int skipBlankLines(String[] lines, int index) {
        while (index < lines.length && lines[index].trim().isEmpty()) index++;
        return index;
    }

    public static String solve(String input) {
        List<Pattern> patterns = parsePatterns(input);
        StringBuilder output = new StringBuilder();
        for (int patternIndex = 0; patternIndex < patterns.size(); patternIndex++) {
            Pattern pattern = patterns.get(patternIndex);
            List<String> program = generateProgram(pattern.radius(), pattern.cells(), pattern.count());

            int submitted = Level5.solveProgram(pattern.radius(), pattern.cells(), program.size(), program);
            if (submitted != pattern.count()) {
                throw new IllegalStateException("Generated program submitted pattern " + submitted
                        + " times instead of " + pattern.count());
            }

            if (patternIndex > 0) output.append('\n');
            for (String line : program) output.append(line).append('\n');
        }
        return output.toString();
    }

    public static void processFile(Path inputPath, Path outputPath) throws IOException {
        String result = solve(Files.readString(inputPath));
        Files.writeString(outputPath, result);
        System.out.println("Processed " + inputPath.getFileName() + " -> " + outputPath.getFileName());
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: java Level6 <input-file> <output-file>");
        }
        processFile(Paths.get(args[0]), Paths.get(args[1]));
    }
}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Level6 {

    private record Candidate(int firstEmitter, int firstPosition, int secondEmitter, int secondPosition,
                             Set<Level5.Point> cells) {}
    private record Pattern(int radius, int count, List<Level5.Point> cells) {}

    private static List<String> generateProgram(int radius, List<Level5.Point> cells, int count) {
        List<Level5.Point> rail = Level5.buildRail(radius);
        int perimeterLength = rail.size();
        char[][] board = Level5.createEmptyBoard(radius);
        int[] rowSteps = {0, 1, 1, 0, -1, -1};
        int[] colSteps = {2, 1, -1, -2, -1, 1};

        Set<Level5.Point> target = new HashSet<>(cells);
        List<Candidate> batchCandidates = new ArrayList<>();
        List<Candidate> singleCellCandidates = new ArrayList<>();

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
                        Set<Level5.Point> intersections = new HashSet<>();
                        boolean onlyTargetCells = true;
                        for (Level5.Point cell : firstRay) {
                            if (secondRay.contains(cell)) {
                                if (!target.contains(cell)) {
                                    onlyTargetCells = false;
                                    break;
                                }
                                intersections.add(cell);
                            }
                        }
                        if (onlyTargetCells && !intersections.isEmpty()) {
                            batchCandidates.add(new Candidate(firstEmitter, firstPosition, secondEmitter, secondPosition,
                                    Set.copyOf(intersections)));
                            for (Level5.Point cell : intersections) {
                                singleCellCandidates.add(new Candidate(firstEmitter, firstPosition, secondEmitter,
                                        secondPosition, Set.of(cell)));
                            }
                        }
                    }
                }
            }
        }

        List<String> batchedProgram = generateUsingCandidates(radius, cells, count, perimeterLength, batchCandidates);
        List<String> singleCellProgram = generateUsingCandidates(radius, cells, count, perimeterLength,
                singleCellCandidates);
        return batchedProgram.size() < singleCellProgram.size() ? batchedProgram : singleCellProgram;
    }

    private static List<String> generateUsingCandidates(int radius, List<Level5.Point> cells, int count,
                                                         int perimeterLength, List<Candidate> candidates) {
        int[] positions = {5 * radius, 0, radius, 2 * radius, 3 * radius, 4 * radius};
        boolean[] active = new boolean[6];
        List<String> program = new ArrayList<>();

        for (int repetition = 0; repetition < count; repetition++) {
            Set<Level5.Point> remaining = new HashSet<>(cells);
            while (!remaining.isEmpty()) {
                Candidate best = null;
                for (Candidate candidate : candidates) {
                    int gain = 0;
                    for (Level5.Point cell : candidate.cells()) {
                        if (remaining.contains(cell)) gain++;
                    }
                    if (gain == 0) continue;

                    int cost = transitionCost(candidate, positions, active, perimeterLength);
                    if (best == null || isBetterCandidate(candidate, gain, cost, best, remaining,
                            positions, active, perimeterLength)) {
                        best = candidate;
                    }
                }
                if (best == null) {
                    throw new IllegalStateException("No emitter pair can create remaining target cells " + remaining);
                }

                int firstDistance = circularDistance(positions[best.firstEmitter()], best.firstPosition(), perimeterLength);
                int secondDistance = circularDistance(positions[best.secondEmitter()], best.secondPosition(), perimeterLength);
                int distance = Math.max(firstDistance, secondDistance);
                int firstDirection = movementDirection(positions[best.firstEmitter()], best.firstPosition(), perimeterLength);
                int secondDirection = movementDirection(positions[best.secondEmitter()], best.secondPosition(), perimeterLength);
                if (distance > 0) {
                    boolean[] wasActive = active.clone();
                    char[] firstMovement = noops();
                    for (int emitter = 0; emitter < active.length; emitter++) {
                        if (active[emitter]) {
                            firstMovement[emitter] = 'T';
                            active[emitter] = false;
                        }
                    }
                    if (!wasActive[best.firstEmitter()] && positions[best.firstEmitter()] != best.firstPosition()) {
                        moveEmitter(firstMovement, positions, best.firstEmitter(), firstDirection, perimeterLength);
                    }
                    if (!wasActive[best.secondEmitter()] && positions[best.secondEmitter()] != best.secondPosition()) {
                        moveEmitter(firstMovement, positions, best.secondEmitter(), secondDirection, perimeterLength);
                    }
                    program.add(format(firstMovement));

                    while (positions[best.firstEmitter()] != best.firstPosition()
                            || positions[best.secondEmitter()] != best.secondPosition()) {
                        char[] movement = noops();
                        if (positions[best.firstEmitter()] != best.firstPosition()) {
                            moveEmitter(movement, positions, best.firstEmitter(), firstDirection, perimeterLength);
                        }
                        if (positions[best.secondEmitter()] != best.secondPosition()) {
                            moveEmitter(movement, positions, best.secondEmitter(), secondDirection, perimeterLength);
                        }
                        program.add(format(movement));
                    }
                }

                char[] fire = noops();
                for (int emitter = 0; emitter < active.length; emitter++) {
                    boolean shouldBeActive = emitter == best.firstEmitter() || emitter == best.secondEmitter();
                    if (active[emitter] != shouldBeActive) {
                        fire[emitter] = 'T';
                        active[emitter] = shouldBeActive;
                    }
                }
                program.add(format(fire));
                remaining.removeAll(best.cells());
            }
        }

        return program;
    }

    private static int transitionCost(Candidate candidate, int[] positions, boolean[] active, int perimeterLength) {
        int[] emitters = {candidate.firstEmitter(), candidate.secondEmitter()};
        int[] destinations = {candidate.firstPosition(), candidate.secondPosition()};
        int[] distances = new int[2];
        int activeMoves = 0;
        int inactiveMoves = 0;
        boolean anyActive = false;
        for (int i = 0; i < emitters.length; i++) {
            int emitter = emitters[i];
            distances[i] = circularDistance(positions[emitter], destinations[i], perimeterLength);
            if (active[emitter]) {
                anyActive = true;
                activeMoves = Math.max(activeMoves, distances[i]);
            } else {
                inactiveMoves = Math.max(inactiveMoves, distances[i]);
            }
        }
        int movementTicks;
        if (!anyActive) {
            movementTicks = Math.max(activeMoves, inactiveMoves);
        } else {
            movementTicks = 1 + Math.max(activeMoves, Math.max(0, inactiveMoves - 1));
        }
        return movementTicks + 1;
    }

    private static boolean isBetterCandidate(Candidate candidate, int gain, int cost, Candidate best,
                                             Set<Level5.Point> remaining, int[] positions, boolean[] active,
                                             int perimeterLength) {
        int bestGain = 0;
        for (Level5.Point cell : best.cells()) {
            if (remaining.contains(cell)) bestGain++;
        }
        int bestCost = transitionCost(best, positions, active, perimeterLength);
        long comparison = (long) gain * bestCost - (long) bestGain * cost;
        if (comparison != 0) return comparison > 0;
        if (gain != bestGain) return gain > bestGain;
        return cost < bestCost;
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

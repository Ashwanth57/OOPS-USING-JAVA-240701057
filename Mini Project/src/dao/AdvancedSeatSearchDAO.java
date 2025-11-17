package com.moviebooking.dao;

import com.moviebooking.model.Seat;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class AdvancedSeatSearchDAO {

    /**
     * Search for all possible best seat arrangements for a group
     * Returns multiple options ranked by quality
     */
    public List<SeatArrangement> findAllBestSeatsForGroup(int showId, int requiredSeats) {
        Connection conn = null;
        try {
            conn = com.moviebooking.util.DBConnection.getConnection();

            List<Seat> availableSeats = getAvailableSeatsByShow(showId, conn);

            if (availableSeats.size() < requiredSeats) {
                System.out.println("❌ Not enough available seats. Required: " + requiredSeats + ", Available: " + availableSeats.size());
                return new ArrayList<>();
            }

            Map<String, List<Seat>> seatsByRow = organizeSeatsByRow(availableSeats);

            System.out.println("========================================");
            System.out.println("SEARCHING FOR ALL BEST ARRANGEMENTS");
            System.out.println("Group Size: " + requiredSeats);
            System.out.println("========================================");

            List<SeatArrangement> allArrangements = new ArrayList<>();

            // Priority 1: Find best single row arrangements (top 2)
            System.out.println("\nPriority 1: Single row arrangements...");
            List<SeatArrangement> singleRowArrangements = findAllSingleRowArrangements(seatsByRow, requiredSeats);
            allArrangements.addAll(singleRowArrangements.stream()
                    .limit(2)
                    .collect(java.util.stream.Collectors.toList()));
            System.out.println("Found: " + singleRowArrangements.size() + " single row option(s), added top 2");

            // Priority 2: Find best split row arrangements (top 2)
            System.out.println("\nPriority 2: Split row arrangements...");
            List<SeatArrangement> splitArrangements = findAllSplitRowArrangements(seatsByRow, requiredSeats);
            allArrangements.addAll(splitArrangements.stream()
                    .limit(2)
                    .collect(java.util.stream.Collectors.toList()));
            System.out.println("Found: " + splitArrangements.size() + " split row option(s), added top 2");

            // Priority 3: Find best scattered arrangements (top 1)
            System.out.println("\nPriority 3: Optimized scattered arrangements...");
            List<SeatArrangement> scatteredArrangements = findBestScatteredArrangements(seatsByRow, requiredSeats, 1);
            allArrangements.addAll(scatteredArrangements);
            System.out.println("Found: " + scatteredArrangements.size() + " scattered option(s)");

            // Sort by quality score (lower is better) and limit to top 5
            List<SeatArrangement> topArrangements = allArrangements.stream()
                    .sorted(Comparator.comparingInt(SeatArrangement::getQualityScore))
                    .limit(5)
                    .collect(java.util.stream.Collectors.toList());

            System.out.println("\n========================================");
            System.out.println("Total arrangements found (showing top 5): " + topArrangements.size());
            for (int i = 0; i < topArrangements.size(); i++) {
                System.out.println((i+1) + ". " + topArrangements.get(i));
            }
            System.out.println("========================================");

            return topArrangements;

        } catch (SQLException e) {
            System.out.println("❌ Error searching for seats: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private List<Seat> getAvailableSeatsByShow(int showId, Connection conn) throws SQLException {
        List<Seat> seats = new ArrayList<>();
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            String sql = "SELECT * FROM seats WHERE show_id = ? AND status = 'Available' ORDER BY seat_row, seat_number";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, showId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                Seat seat = new Seat();
                seat.setSeatId(rs.getInt("seat_id"));
                seat.setShowId(rs.getInt("show_id"));
                seat.setSeatRow(rs.getString("seat_row"));
                seat.setSeatNumber(rs.getInt("seat_number"));
                seat.setStatus(rs.getString("status"));
                seats.add(seat);
            }
        } finally {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
        }

        return seats;
    }

    private Map<String, List<Seat>> organizeSeatsByRow(List<Seat> seats) {
        Map<String, List<Seat>> seatsByRow = new LinkedHashMap<>();

        for (Seat seat : seats) {
            String row = seat.getSeatRow();
            seatsByRow.computeIfAbsent(row, k -> new ArrayList<>()).add(seat);
        }

        seatsByRow.forEach((row, seatList) ->
                seatList.sort(Comparator.comparingInt(Seat::getSeatNumber))
        );

        return seatsByRow;
    }

    /**
     * Find ALL possible single row arrangements
     */
    private List<SeatArrangement> findAllSingleRowArrangements(Map<String, List<Seat>> seatsByRow, int requiredSeats) {
        List<SeatArrangement> arrangements = new ArrayList<>();

        for (Map.Entry<String, List<Seat>> entry : seatsByRow.entrySet()) {
            String row = entry.getKey();
            List<Seat> seats = entry.getValue();

            for (int i = 0; i <= seats.size() - requiredSeats; i++) {
                List<Seat> consecutive = new ArrayList<>();
                boolean isConsecutive = true;

                for (int j = 0; j < requiredSeats; j++) {
                    Seat current = seats.get(i + j);
                    if (j > 0) {
                        Seat previous = seats.get(i + j - 1);
                        if (current.getSeatNumber() != previous.getSeatNumber() + 1) {
                            isConsecutive = false;
                            break;
                        }
                    }
                    consecutive.add(current);
                }

                if (isConsecutive) {
                    SeatArrangement arrangement = new SeatArrangement();
                    arrangement.setArrangementType("SINGLE_ROW");
                    arrangement.addSeats(new ArrayList<>(consecutive));
                    arrangement.setDescription("Row " + row + ", Seats " + consecutive.get(0).getSeatNumber() + "-" + consecutive.get(consecutive.size()-1).getSeatNumber());
                    arrangement.setQualityScore(0); // Best quality
                    arrangements.add(arrangement);
                }
            }
        }

        return arrangements;
    }

    /**
     * Find ALL possible split row arrangements
     */
    private List<SeatArrangement> findAllSplitRowArrangements(Map<String, List<Seat>> seatsByRow, int requiredSeats) {
        List<SeatArrangement> arrangements = new ArrayList<>();
        List<String> rows = new ArrayList<>(seatsByRow.keySet());

        for (int firstRowSeats = requiredSeats / 2; firstRowSeats > 0; firstRowSeats--) {
            int secondRowSeats = requiredSeats - firstRowSeats;

            for (int i = 0; i < rows.size() - 1; i++) {
                String row1 = rows.get(i);
                String row2 = rows.get(i + 1);

                List<Seat> seatsRow1 = seatsByRow.get(row1);
                List<Seat> seatsRow2 = seatsByRow.get(row2);

                // Find all possible combinations
                List<List<Seat>> combos1 = findAllConsecutiveSeats(seatsRow1, firstRowSeats);
                List<List<Seat>> combos2 = findAllConsecutiveSeats(seatsRow2, secondRowSeats);

                for (List<Seat> arrangement1 : combos1) {
                    for (List<Seat> arrangement2 : combos2) {
                        SeatArrangement arrangement = new SeatArrangement();
                        arrangement.setArrangementType("SPLIT_ROWS");
                        arrangement.addSeats(arrangement1);
                        arrangement.addSeats(arrangement2);

                        int startSeat1 = arrangement1.get(0).getSeatNumber();
                        int endSeat1 = arrangement1.get(arrangement1.size()-1).getSeatNumber();
                        int startSeat2 = arrangement2.get(0).getSeatNumber();
                        int endSeat2 = arrangement2.get(arrangement2.size()-1).getSeatNumber();

                        arrangement.setDescription(firstRowSeats + " in Row " + row1 + " (" + startSeat1 + "-" + endSeat1 + "), " +
                                secondRowSeats + " in Row " + row2 + " (" + startSeat2 + "-" + endSeat2 + ")");
                        arrangement.setQualityScore(10); // Good quality
                        arrangements.add(arrangement);
                    }
                }
            }
        }

        return arrangements;
    }

    /**
     * Find multiple best scattered arrangements
     */
    private List<SeatArrangement> findBestScatteredArrangements(Map<String, List<Seat>> seatsByRow, int requiredSeats, int topN) {
        List<SeatArrangement> allArrangements = new ArrayList<>();

        List<Seat> allSeats = new ArrayList<>();
        for (List<Seat> rowSeats : seatsByRow.values()) {
            allSeats.addAll(rowSeats);
        }

        for (int i = 0; i <= allSeats.size() - requiredSeats; i++) {
            List<Seat> candidate = new ArrayList<>(allSeats.subList(i, i + requiredSeats));
            int score = calculateProximityScore(candidate);

            SeatArrangement arrangement = new SeatArrangement();
            arrangement.setArrangementType("SCATTERED");
            arrangement.addSeats(new ArrayList<>(candidate));
            arrangement.setProximityScore(score);
            arrangement.setQualityScore(20 + (score / 10)); // Lower quality

            String seatList = candidate.stream()
                    .map(s -> s.getSeatRow() + s.getSeatNumber())
                    .collect(Collectors.joining(", "));
            arrangement.setDescription("Mixed arrangement: " + seatList);

            allArrangements.add(arrangement);
        }

        // Sort by quality and return top N
        return allArrangements.stream()
                .sorted(Comparator.comparingInt(SeatArrangement::getQualityScore))
                .limit(topN)
                .collect(Collectors.toList());
    }

    private List<List<Seat>> findAllConsecutiveSeats(List<Seat> rowSeats, int required) {
        List<List<Seat>> results = new ArrayList<>();

        if (rowSeats.size() < required) return results;

        for (int i = 0; i <= rowSeats.size() - required; i++) {
            List<Seat> consecutive = new ArrayList<>();
            boolean isConsecutive = true;

            for (int j = 0; j < required; j++) {
                Seat current = rowSeats.get(i + j);
                if (j > 0) {
                    Seat previous = rowSeats.get(i + j - 1);
                    if (current.getSeatNumber() != previous.getSeatNumber() + 1) {
                        isConsecutive = false;
                        break;
                    }
                }
                consecutive.add(current);
            }

            if (isConsecutive) {
                results.add(new ArrayList<>(consecutive));
            }
        }
        return results;
    }

    private int calculateProximityScore(List<Seat> seats) {
        if (seats.size() < 2) return 0;

        int score = 0;
        for (int i = 0; i < seats.size() - 1; i++) {
            Seat current = seats.get(i);
            Seat next = seats.get(i + 1);

            int rowDifference = Math.abs(current.getSeatRow().compareTo(next.getSeatRow()));
            int seatDifference = Math.abs(current.getSeatNumber() - next.getSeatNumber());

            score += rowDifference * 100 + seatDifference;
        }
        return score;
    }

    public static class SeatArrangement {
        private String arrangementType;
        private List<Seat> seats;
        private String description;
        private int proximityScore;
        private int qualityScore;

        public SeatArrangement() {
            this.seats = new ArrayList<>();
        }

        public void addSeats(List<Seat> seatsToAdd) {
            this.seats.addAll(seatsToAdd);
        }

        public String getArrangementType() { return arrangementType; }
        public void setArrangementType(String type) { this.arrangementType = type; }

        public List<Seat> getSeats() { return seats; }
        public void setSeats(List<Seat> seats) { this.seats = seats; }

        public String getDescription() { return description; }
        public void setDescription(String desc) { this.description = desc; }

        public int getProximityScore() { return proximityScore; }
        public void setProximityScore(int score) { this.proximityScore = score; }

        public int getQualityScore() { return qualityScore; }
        public void setQualityScore(int score) { this.qualityScore = score; }

        @Override
        public String toString() {
            return arrangementType + " | " + description + " | Quality: " + qualityScore;
        }
    }
}
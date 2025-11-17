package com.moviebooking.dao;

import com.moviebooking.model.Seat;
import com.moviebooking.util.DBConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SeatDAO {

    public List<Seat> getSeatsByShow(int showId) {
        List<Seat> seats = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            String sql = "SELECT * FROM seats WHERE show_id = ? ORDER BY seat_row, seat_number";
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
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return seats;
    }

    /**
     * Book multiple seats for a booking using EXISTING connection
     * This method MUST be called within an active transaction
     * Do NOT close the connection - that's the caller's responsibility
     */
    public boolean bookSeats(List<Integer> seatIds, int bookingId, Connection existingConn) {
        PreparedStatement updateSeat = null;
        PreparedStatement insertDetail = null;

        try {
            System.out.println("bookSeats: Booking " + seatIds.size() + " seats for booking ID: " + bookingId);

            String updateSql = "UPDATE seats SET status = 'Booked' WHERE seat_id = ? AND status = 'Available'";
            String insertSql = "INSERT INTO booking_details (booking_detail_id, booking_id, seat_id) VALUES (booking_detail_seq.NEXTVAL, ?, ?)";

            updateSeat = existingConn.prepareStatement(updateSql);
            insertDetail = existingConn.prepareStatement(insertSql);

            int successCount = 0;
            for (Integer seatId : seatIds) {
                System.out.println("  -> Attempting to book seat ID: " + seatId);

                updateSeat.setInt(1, seatId);
                int updated = updateSeat.executeUpdate();

                if (updated == 0) {
                    System.out.println("  ❌ Seat " + seatId + " is NOT available");
                    return false;
                }

                System.out.println("  ✓ Seat " + seatId + " status updated to 'Booked'");

                insertDetail.setInt(1, bookingId);
                insertDetail.setInt(2, seatId);
                int inserted = insertDetail.executeUpdate();

                if (inserted == 0) {
                    System.out.println("  ❌ Failed to insert booking detail for seat " + seatId);
                    return false;
                }

                System.out.println("  ✓ Booking detail inserted for seat " + seatId);
                successCount++;
            }

            System.out.println("✓ Successfully booked " + successCount + " out of " + seatIds.size() + " seats");
            return true;

        } catch (SQLException e) {
            System.out.println("❌ SQL ERROR in bookSeats: " + e.getMessage());
            System.out.println("   Error Code: " + e.getErrorCode());
            System.out.println("   SQL State: " + e.getSQLState());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (updateSeat != null) updateSeat.close();
                if (insertDetail != null) insertDetail.close();
                // DO NOT close existingConn - caller manages it
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Overloaded method for backward compatibility
     * Creates its own connection (use this for non-transactional operations)
     */
    public boolean bookSeats(List<Integer> seatIds, int bookingId) {
        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            return bookSeats(seatIds, bookingId, conn);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check seat status before booking
     */
    public Seat getSeatById(int seatId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();
            String sql = "SELECT * FROM seats WHERE seat_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, seatId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                Seat seat = new Seat();
                seat.setSeatId(rs.getInt("seat_id"));
                seat.setShowId(rs.getInt("show_id"));
                seat.setSeatRow(rs.getString("seat_row"));
                seat.setSeatNumber(rs.getInt("seat_number"));
                seat.setStatus(rs.getString("status"));
                return seat;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(conn, stmt, rs);
        }
        return null;
    }

    private void closeResources(Connection conn, Statement stmt, ResultSet rs) {
        try {
            if (rs != null) rs.close();
            if (stmt != null) stmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
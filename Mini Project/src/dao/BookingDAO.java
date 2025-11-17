package com.moviebooking.dao;

import com.moviebooking.model.Booking;
import com.moviebooking.util.DBConnection;
import java.sql.*;
import java.util.*;

public class BookingDAO {

    /**
     * Create a new booking
     * Saves booking to database AND updates seat status
     */
    public int createBooking(int userId, int showId, double totalAmount) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            // Step 1: Insert booking record
            String bookingSql = "INSERT INTO bookings (booking_id, user_id, show_id, booking_date, total_amount, payment_status, booking_status) " +
                    "VALUES (booking_seq.NEXTVAL, ?, ?, SYSDATE, ?, 'Completed', 'Active')";

            stmt = conn.prepareStatement(bookingSql, new String[]{"booking_id"});
            stmt.setInt(1, userId);
            stmt.setInt(2, showId);
            stmt.setDouble(3, totalAmount);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                // Step 2: Get the generated booking ID
                rs = stmt.getGeneratedKeys();
                int bookingId = 0;

                if (rs.next()) {
                    bookingId = rs.getInt(1);
                    System.out.println("✓ Booking created successfully in database with ID: " + bookingId);
                    return bookingId;
                }
            }

        } catch (SQLException e) {
            System.out.println("❌ Error creating booking: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return -1; // Return -1 if failed
    }

    /**
     * Update show's available seats count
     */
    public boolean updateAvailableSeats(int showId, int seatsBooked) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "UPDATE shows SET available_seats = available_seats - ? WHERE show_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, seatsBooked);
            stmt.setInt(2, showId);

            int rowsAffected = stmt.executeUpdate();
            conn.commit();

            if (rowsAffected > 0) {
                System.out.println("✓ Available seats updated: -" + seatsBooked + " for show " + showId);
                return true;
            }

        } catch (SQLException e) {
            System.out.println("❌ Error updating available seats: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Get all bookings for a user
     */
    public List<Map<String, Object>> getUserBookings(int userId) {
        List<Map<String, Object>> bookings = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT b.booking_id, b.booking_date, b.total_amount, b.payment_status, b.booking_status, " +
                    "m.title, m.genre, m.duration, " +
                    "TO_CHAR(s.show_date, 'DD Mon YYYY') || ' ' || s.show_time as show_time " +
                    "FROM bookings b " +
                    "JOIN shows s ON b.show_id = s.show_id " +
                    "JOIN movies m ON s.movie_id = m.movie_id " +
                    "WHERE b.user_id = ? " +
                    "ORDER BY b.booking_date DESC";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> booking = new HashMap<>();
                booking.put("bookingId", rs.getInt("booking_id"));
                booking.put("bookingDate", rs.getTimestamp("booking_date"));
                booking.put("totalAmount", rs.getDouble("total_amount"));
                booking.put("paymentStatus", rs.getString("payment_status"));
                booking.put("bookingStatus", rs.getString("booking_status"));
                booking.put("movieTitle", rs.getString("title"));
                booking.put("genre", rs.getString("genre"));
                booking.put("duration", rs.getInt("duration"));
                booking.put("showTime", rs.getString("show_time"));

                // Get seats for this booking
                List<String> seats = getBookingSeats(rs.getInt("booking_id"));
                booking.put("seats", seats);

                bookings.add(booking);
            }

            System.out.println("✓ Retrieved " + bookings.size() + " bookings for user " + userId);

        } catch (SQLException e) {
            System.out.println("❌ Error getting user bookings: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return bookings;
    }

    /**
     * Get all seats booked for a specific booking
     */
    public List<String> getBookingSeats(int bookingId) {
        List<String> seats = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT seat_row || seat_number as seat_label FROM seats s " +
                    "JOIN booking_details bd ON s.seat_id = bd.seat_id " +
                    "WHERE bd.booking_id = ? " +
                    "ORDER BY s.seat_row, s.seat_number";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bookingId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                seats.add(rs.getString("seat_label"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return seats;
    }

    /**
     * Cancel a booking
     */
    public boolean cancelBooking(int bookingId, int userId) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Step 1: Update booking status to Cancelled
            String sql = "UPDATE bookings SET booking_status = 'Cancelled' " +
                    "WHERE booking_id = ? AND user_id = ? AND booking_status = 'Active'";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, bookingId);
            stmt.setInt(2, userId);

            int rowsAffected = stmt.executeUpdate();
            stmt.close();

            if (rowsAffected > 0) {
                // Trigger will automatically:
                // 1. Update seat status back to Available
                // 2. Update available_seats count
                // 3. Process waitlist

                conn.commit();
                System.out.println("✓ Booking " + bookingId + " cancelled successfully");
                return true;
            } else {
                conn.rollback();
                System.out.println("❌ Booking not found or already cancelled");
                return false;
            }

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            System.out.println("❌ Error cancelling booking: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
                if (stmt != null) stmt.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Check if seat is already booked
     */
    public boolean isSeatBooked(int seatId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DBConnection.getConnection();

            String sql = "SELECT COUNT(*) FROM seats WHERE seat_id = ? AND status = 'Booked'";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, seatId);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return false;
    }
}
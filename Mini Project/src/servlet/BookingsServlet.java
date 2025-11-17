package com.moviebooking.servlet;

import com.moviebooking.dao.*;
import com.moviebooking.model.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/BookingsServlet")
public class BookingsServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        List<Map<String, Object>> bookingsData = new ArrayList<>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = com.moviebooking.util.DBConnection.getConnection();

            String sql = "SELECT b.booking_id, b.booking_date, b.total_amount, b.payment_status, b.booking_status, " +
                    "m.title, m.genre, m.duration, " +
                    "TO_CHAR(s.show_date, 'DD Mon YYYY') || ' ' || s.show_time as show_time " +
                    "FROM bookings b " +
                    "JOIN shows s ON b.show_id = s.show_id " +
                    "JOIN movies m ON s.movie_id = m.movie_id " +
                    "WHERE b.user_id = ? " +
                    "ORDER BY b.booking_date DESC";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, user.getUserId());
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

                PreparedStatement seatStmt = conn.prepareStatement(
                        "SELECT seat_row || seat_number as seat_label FROM seats s " +
                                "JOIN booking_details bd ON s.seat_id = bd.seat_id " +
                                "WHERE bd.booking_id = ? " +
                                "ORDER BY s.seat_row, s.seat_number"
                );
                seatStmt.setInt(1, rs.getInt("booking_id"));
                ResultSet seatRs = seatStmt.executeQuery();

                List<String> seats = new ArrayList<>();
                while (seatRs.next()) {
                    seats.add(seatRs.getString("seat_label"));
                }
                booking.put("seats", seats);

                seatRs.close();
                seatStmt.close();

                bookingsData.add(booking);
            }

            System.out.println("✓ Retrieved " + bookingsData.size() + " bookings for user " + user.getUserId());

        } catch (SQLException e) {
            System.out.println("❌ Error getting bookings: " + e.getMessage());
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

        request.setAttribute("bookingsData", bookingsData);
        request.getRequestDispatcher("bookings.jsp").forward(request, response);
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        String action = request.getParameter("action");

        if ("cancel".equals(action)) {
            int bookingId = Integer.parseInt(request.getParameter("bookingId"));

            System.out.println("========================================");
            System.out.println("BOOKING CANCELLATION STARTED");
            System.out.println("User ID: " + user.getUserId());
            System.out.println("Booking ID: " + bookingId);
            System.out.println("========================================");

            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = com.moviebooking.util.DBConnection.getConnection();
                conn.setAutoCommit(false);

                // Step 1: Get booking details
                System.out.println("Step 1: Fetching booking details...");
                String getBookingSQL = "SELECT booking_status, show_id FROM bookings WHERE booking_id = ? AND user_id = ?";
                stmt = conn.prepareStatement(getBookingSQL);
                stmt.setInt(1, bookingId);
                stmt.setInt(2, user.getUserId());
                rs = stmt.executeQuery();

                if (!rs.next()) {
                    System.out.println("❌ Booking not found or doesn't belong to user");
                    conn.rollback();
                    response.sendRedirect("BookingsServlet?error=bookingNotFound");
                    return;
                }

                String currentStatus = rs.getString("booking_status");
                int showId = rs.getInt("show_id");
                rs.close();
                stmt.close();

                if ("Cancelled".equals(currentStatus)) {
                    System.out.println("❌ Booking is already cancelled");
                    conn.rollback();
                    response.sendRedirect("BookingsServlet?error=alreadyCancelled");
                    return;
                }

                System.out.println("✓ Booking found - Status: " + currentStatus + ", Show ID: " + showId);

                // Step 2: Get seat count
                System.out.println("Step 2: Getting seat count...");
                String seatCountSQL = "SELECT COUNT(*) as seat_count FROM booking_details WHERE booking_id = ?";
                stmt = conn.prepareStatement(seatCountSQL);
                stmt.setInt(1, bookingId);
                rs = stmt.executeQuery();
                int seatCount = 0;
                if (rs.next()) {
                    seatCount = rs.getInt("seat_count");
                }
                rs.close();
                stmt.close();

                System.out.println("✓ Booking has " + seatCount + " seats");

                // Step 3: Update booking status to Cancelled
                System.out.println("Step 3: Cancelling booking...");
                String updateBookingSQL = "UPDATE bookings SET booking_status = 'Cancelled' WHERE booking_id = ?";
                stmt = conn.prepareStatement(updateBookingSQL);
                stmt.setInt(1, bookingId);
                stmt.executeUpdate();
                stmt.close();

                System.out.println("✓ Booking status updated to Cancelled");

                // Step 4: Release seats
                System.out.println("Step 4: Releasing seats...");
                String releaseSeatSQL = "UPDATE seats SET status = 'Available' WHERE seat_id IN " +
                        "(SELECT seat_id FROM booking_details WHERE booking_id = ?)";
                stmt = conn.prepareStatement(releaseSeatSQL);
                stmt.setInt(1, bookingId);
                int seatsReleased = stmt.executeUpdate();
                stmt.close();

                System.out.println("✓ Released " + seatsReleased + " seats");

                // Step 5: Update show's available_seats
                System.out.println("Step 5: Updating show's available seats...");
                String updateShowSQL = "UPDATE shows SET available_seats = available_seats + ? WHERE show_id = ?";
                stmt = conn.prepareStatement(updateShowSQL);
                stmt.setInt(1, seatCount);
                stmt.setInt(2, showId);
                stmt.executeUpdate();
                stmt.close();

                System.out.println("✓ Show available_seats increased by " + seatCount);

                // Step 6: Check and notify all eligible waitlist users
                System.out.println("Step 6: Checking waitlist and notifying eligible users...");
                String getWaitlistSQL = "SELECT waitlist_id, requested_seats FROM waitlist " +
                        "WHERE show_id = ? AND status = 'Waiting' ORDER BY join_time ASC";
                stmt = conn.prepareStatement(getWaitlistSQL);
                stmt.setInt(1, showId);
                rs = stmt.executeQuery();

                // Collect all waitlist entries first
                List<Map<String, Integer>> waitlistEntries = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Integer> entry = new HashMap<>();
                    entry.put("waitlistId", rs.getInt("waitlist_id"));
                    entry.put("requestedSeats", rs.getInt("requested_seats"));
                    waitlistEntries.add(entry);
                }
                rs.close();
                stmt.close();

                // Now notify all users who have enough available seats
                int notifiedCount = 0;
                for (Map<String, Integer> entry : waitlistEntries) {
                    int waitlistId = entry.get("waitlistId");
                    int requestedSeats = entry.get("requestedSeats");

                    // Get CURRENT available seats (updated after each notification)
                    String checkSeatsSQL = "SELECT available_seats FROM shows WHERE show_id = ?";
                    PreparedStatement checkStmt = conn.prepareStatement(checkSeatsSQL);
                    checkStmt.setInt(1, showId);
                    ResultSet checkRs = checkStmt.executeQuery();

                    if (checkRs.next()) {
                        int availableSeats = checkRs.getInt("available_seats");

                        if (availableSeats >= requestedSeats) {
                            // Notify this waitlist user
                            System.out.println("  -> Notifying waitlist user " + waitlistId + " for " + requestedSeats + " seats (Available: " + availableSeats + ")");

                            String notifySQL = "UPDATE waitlist SET status = 'Notified', notification_sent = 'Y', " +
                                    "expiry_time = SYSDATE + INTERVAL '15' MINUTE WHERE waitlist_id = ?";
                            PreparedStatement notifyStmt = conn.prepareStatement(notifySQL);
                            notifyStmt.setInt(1, waitlistId);
                            notifyStmt.executeUpdate();
                            notifyStmt.close();

                            // Reserve these seats for the notified user
                            String reduceSeatsSQL = "UPDATE shows SET available_seats = available_seats - ? WHERE show_id = ?";
                            PreparedStatement reduceStmt = conn.prepareStatement(reduceSeatsSQL);
                            reduceStmt.setInt(1, requestedSeats);
                            reduceStmt.setInt(2, showId);
                            reduceStmt.executeUpdate();
                            reduceStmt.close();

                            notifiedCount++;
                        } else {
                            System.out.println("  -> Waitlist user " + waitlistId + " needs " + requestedSeats + " seats but only " + availableSeats + " available. Skipping.");
                        }
                    }
                    checkRs.close();
                    checkStmt.close();
                }

                System.out.println("✓ " + notifiedCount + " waitlist user(s) notified");

                // Step 7: Commit
                System.out.println("Step 7: Committing transaction...");
                conn.commit();
                System.out.println("✓ Booking cancelled successfully!");
                System.out.println("========================================");

                response.sendRedirect("BookingsServlet?cancelled=true");

            } catch (SQLException e) {
                try {
                    if (conn != null) conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                System.out.println("❌ DATABASE ERROR: " + e.getMessage());
                System.out.println("   Error Code: " + e.getErrorCode());
                e.printStackTrace();
                response.sendRedirect("BookingsServlet?error=database");
            } catch (Exception e) {
                System.out.println("❌ ERROR: " + e.getMessage());
                e.printStackTrace();
                response.sendRedirect("BookingsServlet?error=error");
            } finally {
                try {
                    if (rs != null) rs.close();
                    if (stmt != null) stmt.close();
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        } else {
            response.sendRedirect("BookingsServlet");
        }
    }
}
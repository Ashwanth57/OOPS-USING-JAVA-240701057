package com.moviebooking.servlet;

import com.moviebooking.dao.*;
import com.moviebooking.model.User;
import com.moviebooking.model.Seat;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/PaymentServlet")
public class PaymentServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = com.moviebooking.util.DBConnection.getConnection();
            conn.setAutoCommit(false);

            // ===== STEP 1: Parse form data =====
            int showId = Integer.parseInt(request.getParameter("showId"));
            int movieId = Integer.parseInt(request.getParameter("movieId"));
            String selectedSeatsStr = request.getParameter("selectedSeats");

            System.out.println("========================================");
            System.out.println("BOOKING PROCESS STARTED");
            System.out.println("User: " + user.getFullName() + " (ID: " + user.getUserId() + ")");
            System.out.println("Show ID: " + showId);
            System.out.println("Movie ID: " + movieId);
            System.out.println("Selected Seats (IDs): " + selectedSeatsStr);
            System.out.println("========================================");

            if (selectedSeatsStr == null || selectedSeatsStr.isEmpty()) {
                System.out.println("❌ ERROR: No seats selected");
                response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=noSeats");
                return;
            }

            String[] seatIds = selectedSeatsStr.split(",");
            List<Integer> seatIdList = new ArrayList<>();
            for (String id : seatIds) {
                seatIdList.add(Integer.parseInt(id.trim()));
            }

            System.out.println("Total seats selected: " + seatIdList.size());

            // ===== STEP 2: Get show price =====
            String priceQuery = "SELECT price FROM shows WHERE show_id = ?";
            stmt = conn.prepareStatement(priceQuery);
            stmt.setInt(1, showId);
            rs = stmt.executeQuery();

            double price = 0;
            if (rs.next()) {
                price = rs.getDouble("price");
            }
            rs.close();
            stmt.close();

            System.out.println("Price per seat: ₹" + price);

            double totalAmount = price * seatIdList.size();
            System.out.println("Total amount: ₹" + totalAmount);

            // ===== STEP 3: Process payment =====
            System.out.println("Processing payment...");
            boolean paymentSuccessful = processPayment(totalAmount);

            if (!paymentSuccessful) {
                conn.rollback();
                System.out.println("❌ Payment failed");
                response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=paymentFailed");
                return;
            }

            System.out.println("✓ Payment successful");

            // ===== STEP 4: Create booking in database =====
            System.out.println("Creating booking record in database...");

            String bookingSql = "INSERT INTO bookings (booking_id, user_id, show_id, booking_date, total_amount, payment_status, booking_status) " +
                    "VALUES (booking_seq.NEXTVAL, ?, ?, SYSDATE, ?, 'Completed', 'Active')";

            stmt = conn.prepareStatement(bookingSql, new String[]{"booking_id"});
            stmt.setInt(1, user.getUserId());
            stmt.setInt(2, showId);
            stmt.setDouble(3, totalAmount);
            stmt.executeUpdate();

            // Get generated booking ID
            rs = stmt.getGeneratedKeys();
            int bookingId = 0;
            if (rs.next()) {
                bookingId = rs.getInt(1);
            }
            rs.close();
            stmt.close();

            System.out.println("✓ Booking created with ID: " + bookingId);

            // ===== STEP 5: Book the seats =====
            System.out.println("Booking seats in database...");

            // Verify seats are available first
            System.out.println("Verifying seat availability...");
            SeatDAO seatDAO = new SeatDAO();
            for (Integer seatId : seatIdList) {
                Seat seat = seatDAO.getSeatById(seatId);
                if (seat != null) {
                    System.out.println("  Seat " + seatId + ": Status = " + seat.getStatus() +
                            " (Row: " + seat.getSeatRow() + ", Num: " + seat.getSeatNumber() + ")");
                } else {
                    System.out.println("  Seat " + seatId + ": NOT FOUND");
                }
            }

            // Pass the existing transaction connection to SeatDAO
            boolean seatsBooked = seatDAO.bookSeats(seatIdList, bookingId, conn);

            if (!seatsBooked) {
                conn.rollback();
                System.out.println("❌ Failed to book seats - Rolling back transaction");
                response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=bookingFailed");
                return;
            }

            System.out.println("✓ Seats booked successfully");

            // ===== STEP 6: Update available seats count =====
            System.out.println("Updating available seats count...");

            String updateShowSql = "UPDATE shows SET available_seats = available_seats - ? WHERE show_id = ?";
            stmt = conn.prepareStatement(updateShowSql);
            stmt.setInt(1, seatIdList.size());
            stmt.setInt(2, showId);
            stmt.executeUpdate();
            stmt.close();

            System.out.println("✓ Available seats updated");

            // ===== STEP 7: Remove from waitlist if applicable =====
            System.out.println("Checking if user was in waitlist...");

            String removeWaitlistSql = "UPDATE waitlist SET status = 'Fulfilled' WHERE user_id = ? AND show_id = ? AND status IN ('Waiting', 'Notified')";
            stmt = conn.prepareStatement(removeWaitlistSql);
            stmt.setInt(1, user.getUserId());
            stmt.setInt(2, showId);
            int waitlistRows = stmt.executeUpdate();
            stmt.close();

            if (waitlistRows > 0) {
                System.out.println("✓ User removed from waitlist");
            } else {
                System.out.println("ℹ User was not in waitlist");
            }

            // ===== STEP 8: COMMIT all changes =====
            System.out.println("Committing all database changes...");
            conn.commit();
            System.out.println("✓ All changes saved to database");

            // ===== STEP 9: Close the transaction connection =====
            if (stmt != null) stmt.close();
            if (rs != null) rs.close();
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }

            // ===== STEP 10: Prepare receipt data using NEW connection =====
            System.out.println("Preparing receipt data...");

            Map<String, Object> bookingData = prepareReceiptData(bookingId, movieId, showId, seatIdList, totalAmount);

            if (bookingData != null && !bookingData.isEmpty()) {
                System.out.println("✓ Receipt data prepared");
                System.out.println("Receipt Seats: " + bookingData.get("seatLabels").toString());
                System.out.println("========================================");
                System.out.println("BOOKING SUCCESSFUL!");
                System.out.println("========================================");

                request.setAttribute("bookingData", bookingData);
                request.getRequestDispatcher("ticket.jsp").forward(request, response);
            } else {
                System.out.println("❌ Failed to prepare receipt data");
                response.sendRedirect("HomeServlet?error=receiptError");
            }

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            System.out.println("❌ DATABASE ERROR: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("ShowServlet?movieId=" + request.getParameter("movieId") + "&error=database");
        } catch (NumberFormatException e) {
            System.out.println("❌ PARSING ERROR: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("HomeServlet?error=invalid");
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
                if (stmt != null) stmt.close();
                if (rs != null) rs.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Prepare receipt data using a separate database connection
     */
    private Map<String, Object> prepareReceiptData(int bookingId, int movieId, int showId,
                                                   List<Integer> seatIdList, double totalAmount) {
        Map<String, Object> bookingData = new HashMap<>();
        Connection receiptConn = null;
        PreparedStatement receiptStmt = null;
        ResultSet receiptRs = null;

        try {
            receiptConn = com.moviebooking.util.DBConnection.getConnection();

            // Get movie details
            MovieDAO movieDAO = new MovieDAO();
            bookingData.put("movie", movieDAO.getMovieById(movieId));

            // Get show time
            String showTimeQuery = "SELECT TO_CHAR(show_date, 'DD Mon YYYY') || ' ' || show_time as full_time FROM shows WHERE show_id = ?";
            receiptStmt = receiptConn.prepareStatement(showTimeQuery);
            receiptStmt.setInt(1, showId);
            receiptRs = receiptStmt.executeQuery();

            if (receiptRs.next()) {
                bookingData.put("showTime", receiptRs.getString("full_time"));
            }
            receiptRs.close();
            receiptStmt.close();

            // Get seat labels
            List<String> seatLabels = new ArrayList<>();
            for (Integer seatId : seatIdList) {
                String seatQuery = "SELECT seat_row || seat_number as seat_label FROM seats WHERE seat_id = ?";
                receiptStmt = receiptConn.prepareStatement(seatQuery);
                receiptStmt.setInt(1, seatId);
                receiptRs = receiptStmt.executeQuery();

                if (receiptRs.next()) {
                    seatLabels.add(receiptRs.getString("seat_label"));
                }
                receiptRs.close();
                receiptStmt.close();
            }

            bookingData.put("seatLabels", seatLabels);
            bookingData.put("totalAmount", totalAmount);
            bookingData.put("bookingId", bookingId);
            bookingData.put("bookingDate", new java.text.SimpleDateFormat("dd MMM yyyy HH:mm").format(new java.util.Date()));

            System.out.println("✓ Receipt data successfully prepared with " + seatLabels.size() + " seats");
            return bookingData;

        } catch (SQLException e) {
            System.out.println("❌ ERROR preparing receipt data: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (receiptRs != null) receiptRs.close();
                if (receiptStmt != null) receiptStmt.close();
                if (receiptConn != null) receiptConn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Simulate payment processing
     * Change success rate for testing
     */
    private boolean processPayment(double amount) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 100% success for reliable testing
        // Change to: if (Math.random() > 0.1) for 90% success
        if (Math.random() > 0) {
            System.out.println("✓ Payment processed: ₹" + amount);
            return true;
        } else {
            System.out.println("❌ Payment declined");
            return false;
        }
    }
}
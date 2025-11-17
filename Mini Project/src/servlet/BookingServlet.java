package com.moviebooking.servlet;

import com.moviebooking.dao.*;
import com.moviebooking.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/BookingServlet")
public class BookingServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        int showId = Integer.parseInt(request.getParameter("showId"));
        int movieId = Integer.parseInt(request.getParameter("movieId"));
        String selectedSeatsStr = request.getParameter("selectedSeats");

        if (selectedSeatsStr == null || selectedSeatsStr.isEmpty()) {
            response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=noSeats");
            return;
        }

        String[] seatIds = selectedSeatsStr.split(",");
        List<Integer> seatIdList = new ArrayList<>();
        for (String id : seatIds) {
            seatIdList.add(Integer.parseInt(id.trim()));
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = com.moviebooking.util.DBConnection.getConnection();
            conn.setAutoCommit(false);

            // Get show price
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

            double totalAmount = price * seatIdList.size();

            // Create booking
            String bookingSql = "INSERT INTO bookings (booking_id, user_id, show_id, total_amount, payment_status, booking_status) " +
                    "VALUES (booking_seq.NEXTVAL, ?, ?, ?, 'Completed', 'Active')";
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

            // Book seats
            SeatDAO seatDAO = new SeatDAO();
            boolean seatsBooked = seatDAO.bookSeats(seatIdList, bookingId);

            if (!seatsBooked) {
                conn.rollback();
                response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=bookingFailed");
                return;
            }

            // Update available seats count
            String updateShowSql = "UPDATE shows SET available_seats = available_seats - ? WHERE show_id = ?";
            stmt = conn.prepareStatement(updateShowSql);
            stmt.setInt(1, seatIdList.size());
            stmt.setInt(2, showId);
            stmt.executeUpdate();
            stmt.close();

            // Remove user from waitlist if they were waiting
            String removeWaitlistSql = "UPDATE waitlist SET status = 'Fulfilled' WHERE user_id = ? AND show_id = ? AND status IN ('Waiting', 'Notified')";
            stmt = conn.prepareStatement(removeWaitlistSql);
            stmt.setInt(1, user.getUserId());
            stmt.setInt(2, showId);
            stmt.executeUpdate();
            stmt.close();

            conn.commit();

            // Prepare ticket data
            Map<String, Object> bookingData = new HashMap<>();
            MovieDAO movieDAO = new MovieDAO();
            bookingData.put("movie", movieDAO.getMovieById(movieId));

            // Get show time
            String showTimeQuery = "SELECT TO_CHAR(show_date, 'DD Mon YYYY') || ' ' || show_time as full_time FROM shows WHERE show_id = ?";
            stmt = conn.prepareStatement(showTimeQuery);
            stmt.setInt(1, showId);
            rs = stmt.executeQuery();
            if (rs.next()) {
                bookingData.put("showTime", rs.getString("full_time"));
            }
            rs.close();
            stmt.close();

            // Get seat labels
            List<String> seatLabels = new ArrayList<>();
            for (Integer seatId : seatIdList) {
                String seatQuery = "SELECT seat_row || seat_number as seat_label FROM seats WHERE seat_id = ?";
                stmt = conn.prepareStatement(seatQuery);
                stmt.setInt(1, seatId);
                rs = stmt.executeQuery();
                if (rs.next()) {
                    seatLabels.add(rs.getString("seat_label"));
                }
                rs.close();
                stmt.close();
            }

            bookingData.put("seatLabels", seatLabels);
            bookingData.put("totalAmount", totalAmount);
            bookingData.put("bookingId", bookingId);
            bookingData.put("bookingDate", new java.text.SimpleDateFormat("dd MMM yyyy HH:mm").format(new java.util.Date()));

            request.setAttribute("bookingData", bookingData);
            request.getRequestDispatcher("ticket.jsp").forward(request, response);

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
            response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=database");
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

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("HomeServlet");
    }
}
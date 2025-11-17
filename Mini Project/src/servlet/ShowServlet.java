package com.moviebooking.servlet;

import com.moviebooking.dao.*;
import com.moviebooking.model.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.List;

@WebServlet("/ShowServlet")
public class ShowServlet extends HttpServlet {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        int movieId = Integer.parseInt(request.getParameter("movieId"));
        String showIdParam = request.getParameter("showId");

        MovieDAO movieDAO = new MovieDAO();
        Movie movie = movieDAO.getMovieById(movieId);

        // Get show details from database
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = com.moviebooking.util.DBConnection.getConnection();

            // If showId is provided, use it; otherwise get first available show
            int showId;
            if (showIdParam != null) {
                showId = Integer.parseInt(showIdParam);
            } else {
                String sql = "SELECT show_id FROM shows WHERE movie_id = ? ORDER BY show_date, show_time";
                stmt = conn.prepareStatement(sql);
                stmt.setInt(1, movieId);
                rs = stmt.executeQuery();

                if (rs.next()) {
                    showId = rs.getInt("show_id");
                } else {
                    response.sendRedirect("HomeServlet?error=noShows");
                    return;
                }

                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
            }

            // Get show details
            String sql = "SELECT s.*, TO_CHAR(s.show_date, 'DD Mon YYYY') || ' ' || s.show_time as full_time " +
                    "FROM shows s WHERE s.show_id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, showId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                String showTime = rs.getString("full_time");
                double price = rs.getDouble("price");
                int availableSeats = rs.getInt("available_seats");

                // Get seats
                SeatDAO seatDAO = new SeatDAO();
                List<Seat> seats = seatDAO.getSeatsByShow(showId);

                request.setAttribute("movie", movie);
                request.setAttribute("showId", showId);
                request.setAttribute("showTime", showTime);
                request.setAttribute("price", price);
                request.setAttribute("seats", seats);
                request.setAttribute("availableSeats", availableSeats);

                request.getRequestDispatcher("booking.jsp").forward(request, response);
            } else {
                response.sendRedirect("HomeServlet?error=showNotFound");
            }

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendRedirect("HomeServlet?error=database");
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

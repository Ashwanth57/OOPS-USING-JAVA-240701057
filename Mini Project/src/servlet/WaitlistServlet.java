package com.moviebooking.servlet;

import com.moviebooking.dao.*;
import com.moviebooking.model.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/WaitlistServlet")
public class WaitlistServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        WaitlistDAO waitlistDAO = new WaitlistDAO();
        List<Waitlist> userWaitlist = waitlistDAO.getUserWaitlist(user.getUserId());

        // Prepare detailed waitlist data
        List<Map<String, Object>> waitlistData = new ArrayList<>();
        MovieDAO movieDAO = new MovieDAO();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = com.moviebooking.util.DBConnection.getConnection();

            for (Waitlist wl : userWaitlist) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("waitlist", wl);

                // Get movie details
                String movieQuery = "SELECT m.* FROM MOVIES m JOIN SHOWS s ON m.MOVIE_ID = s.MOVIE_ID WHERE s.SHOW_ID = ?";
                stmt = conn.prepareStatement(movieQuery);
                stmt.setInt(1, wl.getShowId());
                rs = stmt.executeQuery();

                if (rs.next()) {
                    Movie movie = new Movie();
                    movie.setMovieId(rs.getInt("MOVIE_ID"));
                    movie.setTitle(rs.getString("TITLE"));
                    movie.setGenre(rs.getString("GENRE"));
                    movie.setDuration(rs.getInt("DURATION"));
                    entry.put("movie", movie);
                }
                rs.close();
                stmt.close();

                // Get show time
                String showQuery = "SELECT TO_CHAR(SHOW_DATE, 'DD Mon YYYY') || ' ' || SHOW_TIME as FULL_TIME FROM SHOWS WHERE SHOW_ID = ?";
                stmt = conn.prepareStatement(showQuery);
                stmt.setInt(1, wl.getShowId());
                rs = stmt.executeQuery();

                if (rs.next()) {
                    entry.put("showTime", rs.getString("FULL_TIME"));
                }
                rs.close();
                stmt.close();

                // Get position and total count
                int position = waitlistDAO.getWaitlistPosition(user.getUserId(), wl.getShowId());
                int count = waitlistDAO.getWaitlistCount(wl.getShowId());

                // Position 0 means notified (seats available!)
                // Position -1 means not in waitlist
                // Position 1+ means waiting in queue
                entry.put("position", position);
                entry.put("waitlistCount", count);

                // Add display message based on position
                if (position == 0 && "Notified".equals(wl.getStatus())) {
                    entry.put("positionMessage", "Seats Available!");
                } else if (position > 0) {
                    entry.put("positionMessage", "Position #" + position);
                } else {
                    entry.put("positionMessage", "N/A");
                }

                waitlistData.add(entry);
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

        request.setAttribute("waitlistData", waitlistData);
        request.getRequestDispatcher("waitlist.jsp").forward(request, response);
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

        if ("join".equals(action)) {
            int showId = Integer.parseInt(request.getParameter("showId"));
            int requestedSeats = Integer.parseInt(request.getParameter("requestedSeats"));

            WaitlistDAO waitlistDAO = new WaitlistDAO();

            // Check if already in waitlist
            if (!waitlistDAO.isUserInWaitlist(user.getUserId(), showId)) {
                boolean added = waitlistDAO.addToWaitlist(user.getUserId(), showId, requestedSeats);

                if (added) {
                    response.sendRedirect("WaitlistServlet?joined=true");
                } else {
                    response.sendRedirect("WaitlistServlet?error=failedToJoin");
                }
            } else {
                response.sendRedirect("WaitlistServlet?error=alreadyInWaitlist");
            }

        } else if ("cancel".equals(action)) {
            int waitlistId = Integer.parseInt(request.getParameter("waitlistId"));

            WaitlistDAO waitlistDAO = new WaitlistDAO();
            boolean removed = waitlistDAO.removeFromWaitlist(waitlistId);

            if (removed) {
                response.sendRedirect("WaitlistServlet?cancelled=true");
            } else {
                response.sendRedirect("WaitlistServlet?error=removeFailed");
            }
        }
    }
}
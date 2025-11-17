package com.moviebooking.servlet;

import com.moviebooking.dao.AdvancedSeatSearchDAO;
import com.moviebooking.dao.AdvancedSeatSearchDAO.SeatArrangement;
import com.moviebooking.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.List;

@WebServlet("/GroupSeatSearchServlet")
public class GroupSeatSearchServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession();
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        try {
            int showId = Integer.parseInt(request.getParameter("showId"));
            int groupSize = Integer.parseInt(request.getParameter("groupSize"));
            int movieId = Integer.parseInt(request.getParameter("movieId"));

            System.out.println("========================================");
            System.out.println("GROUP SEAT SEARCH REQUEST");
            System.out.println("User: " + user.getFullName());
            System.out.println("Movie ID: " + movieId);
            System.out.println("Show ID: " + showId);
            System.out.println("Group Size: " + groupSize);
            System.out.println("========================================");

            // Search for all best seat arrangements
            AdvancedSeatSearchDAO seatSearchDAO = new AdvancedSeatSearchDAO();
            List<AdvancedSeatSearchDAO.SeatArrangement> arrangements = seatSearchDAO.findAllBestSeatsForGroup(showId, groupSize);

            if (arrangements != null && !arrangements.isEmpty()) {
                System.out.println("✓ Found " + arrangements.size() + " arrangement(s)");

                request.setAttribute("arrangements", arrangements);
                request.setAttribute("showId", showId);
                request.setAttribute("movieId", movieId);
                request.setAttribute("groupSize", groupSize);

                request.getRequestDispatcher("group_booking_options.jsp").forward(request, response);
            } else {
                System.out.println("❌ No suitable arrangements found");
                response.sendRedirect("ShowServlet?movieId=" + movieId + "&error=noSeatsFound");
            }

        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid parameters: " + e.getMessage());
            e.printStackTrace();
            response.sendRedirect("ShowServlet?error=invalid");
        }
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendRedirect("HomeServlet");
    }
}
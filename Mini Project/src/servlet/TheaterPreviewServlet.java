package com.moviebooking.servlet;

import com.moviebooking.dao.SeatDAO;
import com.moviebooking.model.Seat;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.*;

@WebServlet("/TheaterPreviewServlet")
public class TheaterPreviewServlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        try {
            int showId = Integer.parseInt(request.getParameter("showId"));
            String selectedSeatIds = request.getParameter("selectedSeatIds");

            // Parse selected seat IDs
            List<Integer> selectedIds = new ArrayList<>();
            if (selectedSeatIds != null && !selectedSeatIds.isEmpty()) {
                for (String id : selectedSeatIds.split(",")) {
                    selectedIds.add(Integer.parseInt(id.trim()));
                }
            }

            // Get all seats for this show
            SeatDAO seatDAO = new SeatDAO();
            List<Seat> allSeats = seatDAO.getSeatsByShow(showId);

            request.setAttribute("allSeats", allSeats);
            request.setAttribute("selectedSeatIds", selectedIds);

            request.getRequestDispatcher("theater_grid_modal.jsp").forward(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(500, "Error loading theater preview");
        }
    }
}

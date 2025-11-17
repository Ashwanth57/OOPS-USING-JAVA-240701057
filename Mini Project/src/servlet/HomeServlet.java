package com.moviebooking.servlet;

import com.moviebooking.dao.MovieDAO;
import com.moviebooking.model.Movie;
import com.moviebooking.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.List;

@WebServlet("/HomeServlet")
public class HomeServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Check if user is logged in
        HttpSession session = request.getSession(false);
        User user = (User) session.getAttribute("user");

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }

        // Get search query if any
        String searchQuery = request.getParameter("search");

        MovieDAO movieDAO = new MovieDAO();
        List<Movie> movies;

        try {
            // Search or get all movies
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                movies = movieDAO.searchMovies(searchQuery);
            } else {
                movies = movieDAO.getAllMovies();
            }

            // Set movies in request and forward to home.jsp
            request.setAttribute("movies", movies);
            request.getRequestDispatcher("home.jsp").forward(request, response);

        } catch (Exception e) {
            e.printStackTrace();
            request.setAttribute("error", "Unable to load movies. Please try again.");
            request.getRequestDispatcher("home.jsp").forward(request, response);
        }
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }
}
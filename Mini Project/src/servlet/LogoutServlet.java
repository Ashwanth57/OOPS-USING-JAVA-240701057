package com.moviebooking.servlet;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet("/LogoutServlet")
public class LogoutServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Get the current session, don't create a new one
        HttpSession session = request.getSession(false);

        // If session exists, invalidate it
        if (session != null) {
            session.invalidate();
        }

        // Redirect to login page
        response.sendRedirect("login.jsp");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Support POST method as well
        doGet(request, response);
    }
}
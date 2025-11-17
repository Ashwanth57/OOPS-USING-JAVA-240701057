package com.moviebooking.servlet;

import com.moviebooking.dao.UserDAO;
import com.moviebooking.model.User;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.WebServlet;
import java.io.IOException;

@WebServlet("/RegisterServlet")
public class RegisterServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");

        User user = new User(email, password, fullName, phone);
        UserDAO userDAO = new UserDAO();

        boolean registered = userDAO.registerUser(user);

        if (registered) {
            response.sendRedirect("login.jsp?registered=true");
        } else {
            response.sendRedirect("register.jsp?error=true");
        }
    }
}
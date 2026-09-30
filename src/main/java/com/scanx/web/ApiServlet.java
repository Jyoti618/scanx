package com.scanx.web;

import com.scanx.Api;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * The one servlet of SCANX. Every address that starts with /api/ ends up here.
 * It passes the request to Api.handle(...) and writes the answer.
 */
@WebServlet(urlPatterns = "/api/*", loadOnStartup = 1)
public class ApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getServletPath()
                + (request.getPathInfo() == null ? "" : request.getPathInfo());

        if (request.getQueryString() != null) {
            path = path + "?" + request.getQueryString();
        }

        String origin = Api.allowedOrigin(request.getHeader("Origin"));
        if (origin != null) {
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIOjavax");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");

        String method = request.getMethod().toUpperCase();

        /* Browsers ask with OPTIOjavax first when the page is on another address */
        if (method.equals("OPTIOjavax")) {
            response.setStatus(204);
            return;
        }

        Api.Response answer = Api.handle(method, path,
                request.getHeader("Authorization"), request.getInputStream());

        response.setStatus(answer.status);
        response.setContentType(answer.contentType);
        response.setContentLength(answer.body.length);

        OutputStream out = response.getOutputStream();
        out.write(answer.body);
        out.flush();
    }

}

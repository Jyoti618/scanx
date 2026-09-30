package com.scanx.web;

import com.scanx.App;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

/** Tomcat calls this when the web application starts and stops. */
@WebListener
public class AppListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        App.start();
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        App.stop();
    }

}

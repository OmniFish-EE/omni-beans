/*
 * Copyright 2022 OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.omnibeans.test;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 
 * @author Arjan Tijms
 * 
 */
@WebServlet(urlPatterns = "/servlet2")
public class PublicServlet2 extends HttpServlet {

    private static final long serialVersionUID = 1L;

    @Inject
    private SingletonBean singletonBean;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.getWriter().write("This is a public servlet \n");
        
        try {
            testSingleton2(response.getWriter());
        } catch (Exception e) {
            response.getWriter().write("FAILED");
            e.printStackTrace(response.getWriter());
        }

    }
   

    public void testSingleton2(PrintWriter writer) throws InterruptedException {
        singletonBean.addValue(" bar");
        
        writer.write("singleton 2 outcome: " + singletonBean.getAccumulatedValues() + "\n");
    }

}
